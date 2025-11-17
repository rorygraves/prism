"""Request router and response enhancer for smart object hydration."""

from abc import ABC, abstractmethod
from typing import Any

from prism.core.delta import DeltaComputer
from prism.core.protocol import RequestMessage, ResponseMessage
from prism.core.types import (
    Delta,
    HydratedReference,
    ObjectReference,
    RequestOptions,
)
from prism.server.object_manager import PrismObjectManager


class BusinessHandler(ABC):
    """Abstract interface for business logic handlers.

    Business logic remains independent of Prism - handlers just process
    requests and return data (potentially with ObjectReferences).
    """

    @abstractmethod
    async def process(self, request_type: str, payload: dict[str, Any]) -> Any:
        """Process a business logic request.

        Args:
            request_type: Type of request
            payload: Request payload

        Returns:
            Response data (may contain ObjectReferences)

        Raises:
            Exception: If request processing fails
        """
        pass


class RequestRouter:
    """Routes requests to business logic and enhances responses with smart hydration."""

    def __init__(self, object_manager: PrismObjectManager, business_handler: BusinessHandler):
        """Initialize request router.

        Args:
            object_manager: Prism object manager
            business_handler: Business logic handler
        """
        self.object_manager = object_manager
        self.business_handler = business_handler
        self.delta_computer = DeltaComputer()

    async def handle_request(self, client_id: str, request: RequestMessage) -> ResponseMessage:
        """Handle a request with smart response hydration.

        Args:
            client_id: Client identifier
            request: The request message

        Returns:
            Enhanced response message
        """
        try:
            # Parse options
            options = RequestOptions(**(request.options or {}))

            # Process business logic
            result = await self.business_handler.process(request.request_type, request.payload)

            # Enhance response with smart hydration
            hydrated = []
            if options.hydrate_refs:
                hydrated = await self.enhance_response(client_id, result, options)

            return ResponseMessage(
                request_id=request.request_id,
                success=True,
                data=result,
                hydrated=hydrated,
            )

        except Exception as e:
            return ResponseMessage(
                request_id=request.request_id,
                success=False,
                error=str(e),
            )

    async def enhance_response(
        self, client_id: str, data: Any, options: RequestOptions
    ) -> list[HydratedReference]:
        """Scan response for object references and hydrate based on client state.

        Args:
            client_id: Client identifier
            data: Response data to scan
            options: Request options

        Returns:
            List of hydrated references
        """
        # Extract all object references from the response
        references = self._extract_references(data)

        if not references:
            return []

        # Hydrate each reference based on client state
        hydrated = []
        client_state = self.object_manager.get_client_state(client_id)

        for ref in references:
            # Get current object
            obj = await self.object_manager.storage.get_current(ref.id)
            if obj is None:
                continue

            # Apply filter if specified
            filter_type = ref.filter_type or options.filter_type or "default"
            if filter_type != "default":
                obj = self.object_manager.filters.apply(obj, filter_type)

            # Determine what to send based on client state
            known_version = client_state.get_version(ref.id)

            if known_version is None:
                # Client doesn't have object - send full
                hydrated.append(HydratedReference(id=ref.id, version=ref.version, data=obj.data))

                # Auto-subscribe if requested
                if options.subscribe_to_refs or ref.subscribe:
                    from prism.core.protocol import SubscribeMessage

                    await self.object_manager.handle_subscribe(
                        client_id,
                        SubscribeMessage(
                            object_id=ref.id,
                            filter_type=filter_type,
                        ),
                    )

            elif known_version < ref.version:
                # Client has older version - try delta
                delta = await self._compute_delta_for_ref(
                    ref.id, known_version, ref.version, filter_type
                )

                if delta and self.delta_computer.is_delta_efficient(delta, obj):
                    # Send delta
                    hydrated.append(HydratedReference(id=ref.id, version=ref.version, delta=delta))
                else:
                    # Delta too large - send full
                    hydrated.append(
                        HydratedReference(id=ref.id, version=ref.version, data=obj.data)
                    )

                # Update client's known version
                client_state.update_version(ref.id, ref.version)

            else:
                # Client has current version - mark as cached
                hydrated.append(HydratedReference(id=ref.id, version=ref.version, cached=True))

        return hydrated

    def _extract_references(self, data: Any, max_depth: int = 5) -> list[ObjectReference]:
        """Extract all ObjectReferences from response data.

        Args:
            data: Data to scan (dict, list, or primitive)
            max_depth: Maximum recursion depth

        Returns:
            List of found ObjectReferences
        """
        if max_depth <= 0:
            return []

        references: list[ObjectReference] = []

        # Check if this is already an ObjectReference instance
        if isinstance(data, ObjectReference):
            references.append(data)
            return references

        if isinstance(data, dict):
            # Check if this dict is an ObjectReference
            if self._is_object_reference(data):
                try:
                    ref = ObjectReference(**data)
                    references.append(ref)
                except Exception:
                    pass
            else:
                # Recurse into dict values
                for value in data.values():
                    references.extend(self._extract_references(value, max_depth - 1))

        elif isinstance(data, list):
            # Recurse into list items
            for item in data:
                references.extend(self._extract_references(item, max_depth - 1))

        return references

    def _is_object_reference(self, data: dict[str, Any]) -> bool:
        """Check if a dict represents an ObjectReference.

        Args:
            data: Dict to check

        Returns:
            True if it looks like an ObjectReference
        """
        # ObjectReference must have 'id' and 'version'
        return "id" in data and "version" in data and isinstance(data.get("version"), int)

    async def _compute_delta_for_ref(
        self, object_id: str, from_version: int, to_version: int, filter_type: str
    ) -> Delta | None:
        """Compute delta between filtered versions.

        Args:
            object_id: Object ID
            from_version: Starting version
            to_version: Target version
            filter_type: Filter to apply

        Returns:
            Delta or None if unavailable
        """
        try:
            from_obj = await self.object_manager.storage.get_version(object_id, from_version)
            to_obj = await self.object_manager.storage.get_version(object_id, to_version)

            if from_obj is None or to_obj is None:
                return None

            # Apply filters to both versions
            if filter_type != "default":
                from_obj = self.object_manager.filters.apply(from_obj, filter_type)
                to_obj = self.object_manager.filters.apply(to_obj, filter_type)

            return self.delta_computer.compute_delta(from_obj, to_obj)

        except Exception:
            return None
