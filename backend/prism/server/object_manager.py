"""Core Prism object manager for server-side state management."""

import asyncio
from collections.abc import Awaitable, Callable
from typing import Any

from prism.core.delta import DeltaComputer
from prism.core.protocol import (
    ClientMessage,
    DeltaMessage,
    ErrorMessage,
    FullObjectMessage,
    ServerMessage,
    SubscribeMessage,
    SyncMessage,
    UnsubscribeMessage,
    UpdateFilterMessage,
)
from prism.core.types import (
    ClientState,
    Delta,
    PrismObject,
    Subscription,
)
from prism.filters.base import FilterRegistry
from prism.server.cache import LRUCache
from prism.storage.base import StorageAdapter


class PrismObjectManager:
    """Manages object state, subscriptions, and synchronization for clients."""

    def __init__(
        self,
        storage: StorageAdapter,
        filters: FilterRegistry,
        version_cache_size: int = 10000,
        delta_cache_size: int = 5000,
        filter_cache_size: int = 5000,
    ):
        """Initialize object manager.

        Args:
            storage: Storage adapter for persisting objects
            filters: Filter registry
            version_cache_size: Size of version cache
            delta_cache_size: Size of delta cache
            filter_cache_size: Size of filter cache
        """
        self.storage = storage
        self.filters = filters
        self.clients: dict[str, ClientState] = {}
        self.version_cache: LRUCache[PrismObject] = LRUCache(version_cache_size)
        self.delta_cache: LRUCache[Delta] = LRUCache(delta_cache_size)
        self.filter_cache: LRUCache[PrismObject] = LRUCache(filter_cache_size)
        self.delta_computer = DeltaComputer()

        # Callbacks for sending messages to each client (per-client callback map)
        self.send_callbacks: dict[str, Callable[[ServerMessage], Awaitable[None]]] = {}

    def register_client(
        self, client_id: str, callback: Callable[[ServerMessage], Awaitable[None]]
    ) -> None:
        """Register a client's send callback.

        Args:
            client_id: Client identifier
            callback: Async function(message) for sending to this client
        """
        self.send_callbacks[client_id] = callback

    def get_client_state(self, client_id: str) -> ClientState:
        """Get or create client state.

        Args:
            client_id: Client identifier

        Returns:
            ClientState for this client
        """
        if client_id not in self.clients:
            self.clients[client_id] = ClientState()
        return self.clients[client_id]

    def remove_client(self, client_id: str) -> None:
        """Remove client state when disconnected.

        Args:
            client_id: Client identifier
        """
        self.clients.pop(client_id, None)
        self.send_callbacks.pop(client_id, None)

    async def handle_message(self, client_id: str, message: ClientMessage) -> None:
        """Handle incoming client message.

        Args:
            client_id: Client identifier
            message: The client message to handle
        """
        try:
            if isinstance(message, SubscribeMessage):
                await self.handle_subscribe(client_id, message)
            elif isinstance(message, UnsubscribeMessage):
                await self.handle_unsubscribe(client_id, message)
            elif isinstance(message, SyncMessage):
                await self.handle_sync(client_id, message)
            elif isinstance(message, UpdateFilterMessage):
                await self.handle_update_filter(client_id, message)
        except Exception as e:
            await self.send_error(client_id, "INTERNAL_ERROR", str(e))

    async def handle_subscribe(self, client_id: str, msg: SubscribeMessage) -> None:
        """Handle subscription request.

        Args:
            client_id: Client identifier
            msg: Subscribe message
        """
        # TODO: Add logging when logger is available
        # print(f"[SUBSCRIBE] Client {client_id} subscribing to {msg.object_id} with filter {msg.filter_type}")
        client = self.get_client_state(client_id)

        # Get current object
        current_obj = await self.storage.get_current(msg.object_id)
        if current_obj is None:
            await self.send_error(
                client_id, "OBJECT_NOT_FOUND", f"Object {msg.object_id} not found"
            )
            return

        # Apply filter
        filter_type = msg.filter_type or "default"
        filtered_obj = await self._apply_filter_cached(current_obj, filter_type, msg.filter_params)

        # Smart sync based on client state
        await self._smart_sync(client_id, msg.object_id, filtered_obj)

        # Register subscription if not temporary
        if not msg.temporary:
            subscription = Subscription(
                object_id=msg.object_id,
                filter_type=filter_type,
                filter_params=msg.filter_params,
                current_version=filtered_obj.version,
                temporary=False,
            )
            client.subscriptions[msg.object_id] = subscription
            client.update_version(msg.object_id, filtered_obj.version)

    async def handle_unsubscribe(self, client_id: str, msg: UnsubscribeMessage) -> None:
        """Handle unsubscribe request.

        Args:
            client_id: Client identifier
            msg: Unsubscribe message
        """
        client = self.get_client_state(client_id)
        client.subscriptions.pop(msg.object_id, None)

    async def handle_sync(self, client_id: str, msg: SyncMessage) -> None:
        """Handle sync request after reconnection.

        Args:
            client_id: Client identifier
            msg: Sync message with client's current state
        """
        client = self.get_client_state(client_id)

        for state in msg.states:
            current_obj = await self.storage.get_current(state.id)
            if current_obj is None:
                continue

            # Apply filter
            filtered_obj = await self._apply_filter_cached(current_obj, state.filter_type)

            # Smart sync
            await self._smart_sync(client_id, state.id, filtered_obj, state.version)

            # Re-register subscription (critical for receiving future updates)
            subscription = Subscription(
                object_id=state.id,
                filter_type=state.filter_type,
                current_version=filtered_obj.version,
                temporary=False,
            )
            client.subscriptions[state.id] = subscription
            client.update_version(state.id, filtered_obj.version)

    async def handle_update_filter(self, client_id: str, msg: UpdateFilterMessage) -> None:
        """Handle filter update for existing subscription.

        Args:
            client_id: Client identifier
            msg: Update filter message
        """
        client = self.get_client_state(client_id)

        if msg.object_id not in client.subscriptions:
            await self.send_error(
                client_id, "NOT_SUBSCRIBED", f"Not subscribed to {msg.object_id}"
            )
            return

        # Update subscription filter
        subscription = client.subscriptions[msg.object_id]
        subscription.filter_type = msg.filter_type
        subscription.filter_params = msg.filter_params

        # Re-sync with new filter
        current_obj = await self.storage.get_current(msg.object_id)
        if current_obj:
            filtered_obj = await self._apply_filter_cached(current_obj, msg.filter_type, msg.filter_params)
            # Always send full object when filter changes, even if version matches
            await self._send_full_object(client_id, filtered_obj)
            client.update_version(msg.object_id, filtered_obj.version)

    async def notify_object_updated(self, obj: PrismObject) -> None:
        """Notify all subscribed clients when an object is updated.

        Args:
            obj: The updated object
        """
        # Update caches
        self.version_cache.put(f"{obj.id}:{obj.version}", obj)

        # Notify all subscribed clients
        tasks = []
        for client_id, client_state in self.clients.items():
            if obj.id in client_state.subscriptions:
                subscription = client_state.subscriptions[obj.id]
                task = self._send_update(client_id, obj, subscription)
                tasks.append(task)

        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

    async def _send_update(
        self, client_id: str, obj: PrismObject, subscription: Subscription
    ) -> None:
        """Send update to a specific client.

        Args:
            client_id: Client identifier
            obj: Updated object
            subscription: Client's subscription
        """
        # Apply filter
        filtered_obj = await self._apply_filter_cached(obj, subscription.filter_type, subscription.filter_params)

        # Smart sync
        await self._smart_sync(client_id, obj.id, filtered_obj)

    async def _smart_sync(
        self,
        client_id: str,
        object_id: str,
        current_obj: PrismObject,
        known_version: int | None = None,
    ) -> None:
        """Intelligently sync object with client based on their current state.

        Args:
            client_id: Client identifier
            object_id: Object ID
            current_obj: Current filtered object
            known_version: Version client has (if known)
        """
        client = self.get_client_state(client_id)

        if known_version is None:
            known_version = client.get_version(object_id)

        if known_version is None:
            # Client doesn't have object - send full
            await self._send_full_object(client_id, current_obj)
        elif known_version < current_obj.version:
            # Client has older version - try delta
            await self._send_delta_or_full(client_id, object_id, known_version, current_obj)
        # else: client has current version, no update needed

        # Update client's known version
        client.update_version(object_id, current_obj.version)

    async def _send_delta_or_full(
        self,
        client_id: str,
        object_id: str,
        from_version: int,
        to_obj: PrismObject,
    ) -> None:
        """Send delta if efficient, otherwise full object.

        Args:
            client_id: Client identifier
            object_id: Object ID
            from_version: Client's current version
            to_obj: Target object
        """
        # Try to compute delta
        delta = await self._compute_delta_cached(object_id, from_version, to_obj.version)

        if delta and self.delta_computer.is_delta_efficient(delta, to_obj):
            # Delta is efficient - send it
            await self._send_delta(client_id, delta)
        else:
            # Delta too large or unavailable - send full object
            await self._send_full_object(client_id, to_obj)

    async def _compute_delta_cached(
        self, object_id: str, from_version: int, to_version: int
    ) -> Delta | None:
        """Compute delta with caching.

        Args:
            object_id: Object ID
            from_version: Starting version
            to_version: Target version

        Returns:
            Delta or None if cannot be computed
        """
        cache_key = f"{object_id}:{from_version}:{to_version}"
        cached = self.delta_cache.get(cache_key)
        if cached:
            return cached

        # Get both versions
        from_obj = await self._get_version_cached(object_id, from_version)
        to_obj = await self._get_version_cached(object_id, to_version)

        if from_obj is None or to_obj is None:
            return None

        # Compute delta
        delta = self.delta_computer.compute_delta(from_obj, to_obj)
        self.delta_cache.put(cache_key, delta)
        return delta

    async def _get_version_cached(self, object_id: str, version: int) -> PrismObject | None:
        """Get object version with caching.

        Args:
            object_id: Object ID
            version: Version number

        Returns:
            PrismObject or None
        """
        cache_key = f"{object_id}:{version}"
        cached = self.version_cache.get(cache_key)
        if cached:
            return cached

        obj = await self.storage.get_version(object_id, version)
        if obj:
            self.version_cache.put(cache_key, obj)
        return obj

    async def _apply_filter_cached(
        self, obj: PrismObject, filter_type: str, filter_params: dict[str, Any] | None = None
    ) -> PrismObject:
        """Apply filter with caching.

        Args:
            obj: Object to filter
            filter_type: Filter name
            filter_params: Optional filter parameters

        Returns:
            Filtered object
        """
        if filter_type == "default":
            return obj

        # Include params in cache key if present
        cache_key = f"{obj.id}:{obj.version}:{filter_type}"
        if filter_params:
            # Simple hash of params for cache key
            import json
            params_str = json.dumps(filter_params, sort_keys=True)
            cache_key = f"{cache_key}:{params_str}"

        cached = self.filter_cache.get(cache_key)
        if cached:
            return cached

        filtered = self.filters.apply(obj, filter_type, filter_params)
        self.filter_cache.put(cache_key, filtered)
        return filtered

    async def _send_full_object(self, client_id: str, obj: PrismObject) -> None:
        """Send full object to client.

        Args:
            client_id: Client identifier
            obj: Object to send
        """
        message = FullObjectMessage(
            id=obj.id, version=obj.version, data=obj.data, filtered=True
        )
        await self._send_message(client_id, message)

    async def _send_delta(self, client_id: str, delta: Delta) -> None:
        """Send delta to client.

        Args:
            client_id: Client identifier
            delta: Delta to send
        """
        message = DeltaMessage(
            id=delta.object_id,
            from_version=delta.from_version,
            to_version=delta.to_version,
            patches=delta.patches,
        )
        await self._send_message(client_id, message)

    async def send_error(
        self, client_id: str, code: str, message: str, **kwargs: Any
    ) -> None:
        """Send error message to client.

        Args:
            client_id: Client identifier
            code: Error code
            message: Error message
            **kwargs: Additional error fields
        """
        error_msg = ErrorMessage(code=code, message=message, **kwargs)
        await self._send_message(client_id, error_msg)

    async def _send_message(self, client_id: str, message: ServerMessage) -> None:
        """Send message to client via callback.

        Args:
            client_id: Client identifier
            message: Message to send
        """
        callback = self.send_callbacks.get(client_id)
        if callback:
            await callback(message)
