"""Tests for PrismObjectManager."""

import pytest
from unittest.mock import AsyncMock, MagicMock
from prism.core.protocol import (
    SubscribeMessage,
    UnsubscribeMessage,
    UpdateFilterMessage,
    FullObjectMessage,
    DeltaMessage,
    ErrorMessage,
)
from prism.core.types import PrismObject
from prism.filters.common import create_default_registry
from prism.server.object_manager import PrismObjectManager
from prism.storage.base import StorageAdapter


class MockStorage(StorageAdapter):
    """Mock storage for testing."""

    def __init__(self):
        self.objects = {}

    async def save(self, obj: PrismObject) -> None:
        """Save object."""
        if obj.id not in self.objects:
            self.objects[obj.id] = []
        self.objects[obj.id].append(obj)

    async def get_current(self, object_id: str) -> PrismObject | None:
        """Get current version."""
        if object_id in self.objects and self.objects[object_id]:
            return self.objects[object_id][-1]
        return None

    async def get_version(self, object_id: str, version: int) -> PrismObject | None:
        """Get specific version."""
        if object_id in self.objects:
            for obj in self.objects[object_id]:
                if obj.version == version:
                    return obj
        return None

    async def get_versions_range(
        self, object_id: str, from_version: int, to_version: int
    ) -> list[PrismObject]:
        """Get version range."""
        if object_id in self.objects:
            return [
                obj
                for obj in self.objects[object_id]
                if from_version <= obj.version <= to_version
            ]
        return []

    async def delete(self, object_id: str) -> None:
        """Delete object."""
        self.objects.pop(object_id, None)

    async def list_objects(self, limit: int = 100, offset: int = 0) -> list[str]:
        """List object IDs."""
        ids = list(self.objects.keys())
        return ids[offset:offset + limit]


@pytest.mark.asyncio
async def test_subscribe_to_object():
    """Test subscribing to an object."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)

    # Create test object
    obj = PrismObject(id="test-1", version=1, data={"name": "Alice", "age": 30})
    await storage.save(obj)

    # Mock send callback
    messages = []
    async def send_callback(msg):
        messages.append(msg)

    manager.register_client("client-1", send_callback)

    # Subscribe
    msg = SubscribeMessage(object_id="test-1", filter_type="default", temporary=False)
    await manager.handle_subscribe("client-1", msg)

    # Should send full object
    assert len(messages) == 1
    assert isinstance(messages[0], FullObjectMessage)
    assert messages[0].id == "test-1"
    assert messages[0].version == 1

    # Check subscription registered
    client = manager.get_client_state("client-1")
    assert "test-1" in client.subscriptions


@pytest.mark.asyncio
async def test_unsubscribe_from_object():
    """Test unsubscribing from an object."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)

    # Create test object and subscribe
    obj = PrismObject(id="test-1", version=1, data={"name": "Alice"})
    await storage.save(obj)

    messages = []
    async def send_callback(msg):
        messages.append(msg)

    manager.register_client("client-1", send_callback)

    # Subscribe first
    sub_msg = SubscribeMessage(object_id="test-1", filter_type="default", temporary=False)
    await manager.handle_subscribe("client-1", sub_msg)

    client = manager.get_client_state("client-1")
    assert "test-1" in client.subscriptions

    # Unsubscribe
    unsub_msg = UnsubscribeMessage(object_id="test-1")
    await manager.handle_unsubscribe("client-1", unsub_msg)

    # Should remove subscription
    assert "test-1" not in client.subscriptions


@pytest.mark.asyncio
async def test_update_filter_success():
    """Test updating filter for existing subscription."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)

    # Create test object with multiple fields
    obj = PrismObject(
        id="test-1",
        version=1,
        data={"name": "Alice", "age": 30, "email": "alice@example.com"}
    )
    await storage.save(obj)

    messages = []
    async def send_callback(msg):
        messages.append(msg)

    manager.register_client("client-1", send_callback)

    # Subscribe with default filter
    sub_msg = SubscribeMessage(object_id="test-1", filter_type="default", temporary=False)
    await manager.handle_subscribe("client-1", sub_msg)

    # Should have sent full object
    assert len(messages) == 1
    messages.clear()

    # Update filter to only show name and email
    update_msg = UpdateFilterMessage(
        object_id="test-1",
        filter_type="fields",
        filter_params={"fields": ["name", "email"]}
    )
    await manager.handle_update_filter("client-1", update_msg)

    # Should send filtered object
    assert len(messages) == 1
    response = messages[0]
    assert isinstance(response, FullObjectMessage)
    assert response.id == "test-1"
    assert "name" in response.data
    assert "email" in response.data
    assert "age" not in response.data

    # Check subscription updated
    client = manager.get_client_state("client-1")
    subscription = client.subscriptions["test-1"]
    assert subscription.filter_type == "fields"
    assert subscription.filter_params == {"fields": ["name", "email"]}


@pytest.mark.asyncio
async def test_update_filter_not_subscribed():
    """Test updating filter for non-existent subscription."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)

    messages = []
    async def send_callback(msg):
        messages.append(msg)

    manager.register_client("client-1", send_callback)

    # Try to update filter without subscription
    update_msg = UpdateFilterMessage(
        object_id="test-1",
        filter_type="fields",
        filter_params={"fields": ["name"]}
    )
    await manager.handle_update_filter("client-1", update_msg)

    # Should send error
    assert len(messages) == 1
    error = messages[0]
    assert isinstance(error, ErrorMessage)
    assert error.code == "NOT_SUBSCRIBED"
    assert "test-1" in error.message


@pytest.mark.asyncio
async def test_update_filter_with_delta():
    """Test that updating filter can send delta if appropriate."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)

    # Create test object
    obj_v1 = PrismObject(
        id="test-1",
        version=1,
        data={"name": "Alice", "age": 30, "email": "alice@example.com"}
    )
    await storage.save(obj_v1)

    messages = []
    async def send_callback(msg):
        messages.append(msg)

    manager.register_client("client-1", send_callback)

    # Subscribe with default filter
    sub_msg = SubscribeMessage(object_id="test-1", filter_type="default", temporary=False)
    await manager.handle_subscribe("client-1", sub_msg)

    # Client now has version 1
    client = manager.get_client_state("client-1")
    assert client.get_version("test-1") == 1
    messages.clear()

    # Update object to version 2
    obj_v2 = PrismObject(
        id="test-1",
        version=2,
        data={"name": "Alice Smith", "age": 30, "email": "alice@example.com"}
    )
    await storage.save(obj_v2)

    # Update filter (same filter but triggering re-sync)
    update_msg = UpdateFilterMessage(
        object_id="test-1",
        filter_type="default",
        filter_params=None
    )
    await manager.handle_update_filter("client-1", update_msg)

    # Should send either delta or full object depending on efficiency
    assert len(messages) == 1
    response = messages[0]
    assert isinstance(response, (FullObjectMessage, DeltaMessage))

    if isinstance(response, DeltaMessage):
        assert response.id == "test-1"
        assert response.from_version == 1
        assert response.to_version == 2


@pytest.mark.asyncio
async def test_temporary_subscription():
    """Test temporary subscriptions are not registered."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)

    obj = PrismObject(id="test-1", version=1, data={"name": "Alice"})
    await storage.save(obj)

    messages = []
    async def send_callback(msg):
        messages.append(msg)

    manager.register_client("client-1", send_callback)

    # Subscribe with temporary=True
    msg = SubscribeMessage(object_id="test-1", filter_type="default", temporary=True)
    await manager.handle_subscribe("client-1", msg)

    # Should send object but not register subscription
    assert len(messages) == 1
    client = manager.get_client_state("client-1")
    assert "test-1" not in client.subscriptions


@pytest.mark.asyncio
async def test_notify_object_updated():
    """Test notifying clients of object updates."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)

    # Create initial object
    obj_v1 = PrismObject(id="test-1", version=1, data={"name": "Alice"})
    await storage.save(obj_v1)

    # Setup two clients
    messages_1 = []
    messages_2 = []

    async def send_callback_1(msg):
        messages_1.append(msg)

    async def send_callback_2(msg):
        messages_2.append(msg)

    manager.register_client("client-1", send_callback_1)
    manager.register_client("client-2", send_callback_2)

    # Both subscribe
    sub_msg = SubscribeMessage(object_id="test-1", filter_type="default", temporary=False)
    await manager.handle_subscribe("client-1", sub_msg)
    await manager.handle_subscribe("client-2", sub_msg)

    messages_1.clear()
    messages_2.clear()

    # Update object
    obj_v2 = PrismObject(id="test-1", version=2, data={"name": "Alice Smith"})
    await storage.save(obj_v2)
    await manager.notify_object_updated(obj_v2)

    # Both clients should receive update
    assert len(messages_1) == 1
    assert len(messages_2) == 1


@pytest.mark.asyncio
async def test_subscribe_nonexistent_object():
    """Test subscribing to non-existent object."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)

    messages = []
    async def send_callback(msg):
        messages.append(msg)

    manager.register_client("client-1", send_callback)

    # Subscribe to non-existent object
    msg = SubscribeMessage(object_id="nonexistent", filter_type="default", temporary=False)
    await manager.handle_subscribe("client-1", msg)

    # Should send error
    assert len(messages) == 1
    error = messages[0]
    assert isinstance(error, ErrorMessage)
    assert error.code == "OBJECT_NOT_FOUND"
