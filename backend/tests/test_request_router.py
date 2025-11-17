"""Tests for RequestRouter and smart reference hydration."""

from typing import Any

import pytest

from prism.core.protocol import RequestMessage
from prism.core.types import ObjectReference, PrismObject
from prism.filters.common import create_default_registry
from prism.server.object_manager import PrismObjectManager
from prism.server.request_router import BusinessHandler, RequestRouter
from prism.storage.base import StorageAdapter


class MockStorage(StorageAdapter):
    """Mock storage for testing."""

    def __init__(self) -> None:
        self.objects: dict[str, list[PrismObject]] = {}

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
                obj for obj in self.objects[object_id] if from_version <= obj.version <= to_version
            ]
        return []

    async def delete(self, object_id: str) -> None:
        """Delete object."""
        self.objects.pop(object_id, None)

    async def list_objects(self, limit: int = 100, offset: int = 0) -> list[str]:
        """List object IDs."""
        ids = list(self.objects.keys())
        return ids[offset : offset + limit]


class MockBusinessHandler(BusinessHandler):
    """Mock business handler for testing."""

    def __init__(self) -> None:
        self.request_log: list[tuple[str, dict[str, Any]]] = []

    async def process(self, request_type: str, payload: dict[str, Any]) -> dict[str, Any]:
        """Process request and optionally return object references."""
        self.request_log.append((request_type, payload))

        if request_type == "createUser":
            return {
                "user": ObjectReference(
                    id="user-1",
                    version=1,
                    subscribe=True,
                )
            }

        if request_type == "getMessage":
            return {
                "message": ObjectReference(id="msg-1", version=1),
                "user": ObjectReference(id="user-1", version=2),
            }

        if request_type == "getNestedRefs":
            return {
                "data": {
                    "user": ObjectReference(id="user-1", version=1),
                    "nested": {"message": ObjectReference(id="msg-1", version=1)},
                }
            }

        if request_type == "getListRefs":
            return {
                "items": [
                    ObjectReference(id="item-1", version=1),
                    ObjectReference(id="item-2", version=1),
                ]
            }

        if request_type == "error":
            raise ValueError("Test error")

        return {"result": "ok"}


@pytest.mark.asyncio
async def test_request_router_basic_request():
    """Test basic request processing without references."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    request = RequestMessage(
        request_id="req-1",
        request_type="simple",
        payload={"data": "test"},
    )

    response = await router.handle_request("client-1", request)

    assert response.request_id == "req-1"
    assert response.success is True
    assert response.data == {"result": "ok"}
    assert len(response.hydrated) == 0


@pytest.mark.asyncio
async def test_request_router_with_new_reference():
    """Test request with reference that client doesn't have."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create object in storage
    user_obj = PrismObject(
        id="user-1", version=1, data={"name": "Alice", "email": "alice@example.com"}
    )
    await storage.save(user_obj)

    # Make request
    request = RequestMessage(
        request_id="req-1",
        request_type="createUser",
        payload={"name": "Alice"},
        options={"hydrate_refs": True},
    )

    response = await router.handle_request("client-1", request)

    assert response.success is True
    assert len(response.hydrated) == 1

    hydrated = response.hydrated[0]
    assert hydrated.id == "user-1"
    assert hydrated.version == 1
    assert hydrated.data == {"name": "Alice", "email": "alice@example.com"}
    assert hydrated.cached is False  # Full object sent, not cached


@pytest.mark.asyncio
async def test_request_router_with_cached_reference():
    """Test request with reference that client already has."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create object
    user_obj = PrismObject(id="user-1", version=1, data={"name": "Alice"})
    await storage.save(user_obj)

    # Set client state to already have this version
    client_state = manager.get_client_state("client-1")
    client_state.update_version("user-1", 1)

    # Make request
    request = RequestMessage(
        request_id="req-1",
        request_type="createUser",
        payload={},
        options={"hydrate_refs": True},
    )

    response = await router.handle_request("client-1", request)

    assert response.success is True
    assert len(response.hydrated) == 1

    hydrated = response.hydrated[0]
    assert hydrated.id == "user-1"
    assert hydrated.version == 1
    assert hydrated.cached is True  # Client has it
    assert hydrated.data is None  # No data needed


@pytest.mark.asyncio
async def test_request_router_with_delta():
    """Test request sends delta when client has older version."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create v1
    user_v1 = PrismObject(id="user-1", version=1, data={"name": "Alice", "age": 30})
    await storage.save(user_v1)

    # Create v2
    user_v2 = PrismObject(id="user-1", version=2, data={"name": "Alice Smith", "age": 30})
    await storage.save(user_v2)

    # Set client to have v1
    client_state = manager.get_client_state("client-1")
    client_state.update_version("user-1", 1)

    # Make request (handler will return reference to v2)
    class DeltaHandler(BusinessHandler):
        async def process(self, request_type: str, payload: dict[str, Any]) -> dict[str, Any]:
            return {"user": ObjectReference(id="user-1", version=2)}

    delta_handler = DeltaHandler()
    router = RequestRouter(manager, delta_handler)

    request = RequestMessage(
        request_id="req-1",
        request_type="getUser",
        payload={},
        options={"hydrate_refs": True},
    )

    response = await router.handle_request("client-1", request)

    assert response.success is True
    assert len(response.hydrated) == 1

    hydrated = response.hydrated[0]
    assert hydrated.id == "user-1"
    assert hydrated.version == 2

    # Should have delta (small change) or full object
    assert hydrated.delta is not None or hydrated.data is not None


@pytest.mark.asyncio
async def test_request_router_auto_subscribe():
    """Test auto-subscription when reference has subscribe=True."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create object
    user_obj = PrismObject(id="user-1", version=1, data={"name": "Alice"})
    await storage.save(user_obj)

    # Make request
    request = RequestMessage(
        request_id="req-1",
        request_type="createUser",
        payload={},
        options={"hydrate_refs": True, "subscribe_to_refs": False},
    )

    response = await router.handle_request("client-1", request)
    assert response.success is True

    # Check that client was subscribed (reference has subscribe=True)
    client_state = manager.get_client_state("client-1")
    assert "user-1" in client_state.subscriptions


@pytest.mark.asyncio
async def test_request_router_multiple_references():
    """Test request with multiple object references."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create objects
    msg_obj = PrismObject(id="msg-1", version=1, data={"content": "Hello"})
    user_obj = PrismObject(id="user-1", version=2, data={"name": "Alice"})
    await storage.save(msg_obj)
    await storage.save(user_obj)

    # Make request
    request = RequestMessage(
        request_id="req-1",
        request_type="getMessage",
        payload={},
        options={"hydrate_refs": True},
    )

    response = await router.handle_request("client-1", request)

    assert response.success is True
    assert len(response.hydrated) == 2

    # Check both references were hydrated
    ids = {h.id for h in response.hydrated}
    assert ids == {"msg-1", "user-1"}


@pytest.mark.asyncio
async def test_request_router_nested_references():
    """Test extracting references from nested structures."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create objects
    user_obj = PrismObject(id="user-1", version=1, data={"name": "Alice"})
    msg_obj = PrismObject(id="msg-1", version=1, data={"content": "Hello"})
    await storage.save(user_obj)
    await storage.save(msg_obj)

    # Make request
    request = RequestMessage(
        request_id="req-1",
        request_type="getNestedRefs",
        payload={},
        options={"hydrate_refs": True},
    )

    response = await router.handle_request("client-1", request)

    assert response.success is True
    assert len(response.hydrated) == 2  # Both nested references found


@pytest.mark.asyncio
async def test_request_router_list_references():
    """Test extracting references from lists."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create objects
    item1 = PrismObject(id="item-1", version=1, data={"name": "Item 1"})
    item2 = PrismObject(id="item-2", version=1, data={"name": "Item 2"})
    await storage.save(item1)
    await storage.save(item2)

    # Make request
    request = RequestMessage(
        request_id="req-1",
        request_type="getListRefs",
        payload={},
        options={"hydrate_refs": True},
    )

    response = await router.handle_request("client-1", request)

    assert response.success is True
    assert len(response.hydrated) == 2


@pytest.mark.asyncio
async def test_request_router_error_handling():
    """Test error handling in request processing."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    request = RequestMessage(
        request_id="req-1",
        request_type="error",
        payload={},
    )

    response = await router.handle_request("client-1", request)

    assert response.request_id == "req-1"
    assert response.success is False
    assert response.error == "Test error"


@pytest.mark.asyncio
async def test_request_router_no_hydration():
    """Test request with hydrate_refs=False."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create object
    user_obj = PrismObject(id="user-1", version=1, data={"name": "Alice"})
    await storage.save(user_obj)

    # Make request with hydration disabled
    request = RequestMessage(
        request_id="req-1",
        request_type="createUser",
        payload={},
        options={"hydrate_refs": False},  # Use snake_case
    )

    response = await router.handle_request("client-1", request)

    assert response.success is True
    assert len(response.hydrated) == 0  # No hydration


@pytest.mark.asyncio
async def test_extract_references_depth_limiting():
    """Test that reference extraction respects max depth."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create deeply nested structure
    deeply_nested = {
        "level1": {
            "level2": {
                "level3": {
                    "level4": {
                        "level5": {
                            "level6": ObjectReference(id="deep-obj", version=1),
                        }
                    }
                }
            }
        }
    }

    # Each dict level decrements depth, and we check depth BEFORE processing
    # So with 6 nested dicts + the ObjectReference, we need depth >= 7
    refs = router._extract_references(deeply_nested, max_depth=6)
    assert len(refs) == 0  # Not deep enough

    # With max_depth=7, we can reach it
    refs = router._extract_references(deeply_nested, max_depth=7)
    assert len(refs) == 1
    assert refs[0].id == "deep-obj"


@pytest.mark.asyncio
async def test_is_object_reference():
    """Test reference detection logic."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Valid reference
    assert router._is_object_reference({"id": "obj-1", "version": 1}) is True

    # With optional fields
    assert router._is_object_reference({"id": "obj-1", "version": 1, "subscribe": True}) is True

    # Missing id
    assert router._is_object_reference({"version": 1}) is False

    # Missing version
    assert router._is_object_reference({"id": "obj-1"}) is False

    # Wrong type for version
    assert router._is_object_reference({"id": "obj-1", "version": "1"}) is False

    # Regular data dict
    assert router._is_object_reference({"name": "Alice", "age": 30}) is False


@pytest.mark.asyncio
async def test_compute_delta_for_ref():
    """Test delta computation for filtered references."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Create versions
    obj_v1 = PrismObject(id="obj-1", version=1, data={"name": "Alice", "age": 30})
    obj_v2 = PrismObject(id="obj-1", version=2, data={"name": "Alice Smith", "age": 30})
    await storage.save(obj_v1)
    await storage.save(obj_v2)

    # Compute delta
    delta = await router._compute_delta_for_ref("obj-1", 1, 2, "default")

    assert delta is not None
    assert delta.object_id == "obj-1"
    assert delta.from_version == 1
    assert delta.to_version == 2
    assert len(delta.patches) > 0  # Should have patches for name change


@pytest.mark.asyncio
async def test_compute_delta_missing_version():
    """Test delta computation with missing versions."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)
    handler = MockBusinessHandler()
    router = RequestRouter(manager, handler)

    # Try to compute delta for non-existent object
    delta = await router._compute_delta_for_ref("nonexistent", 1, 2, "default")

    assert delta is None


@pytest.mark.asyncio
async def test_request_router_with_filters():
    """Test reference hydration with filters."""
    storage = MockStorage()
    filters = create_default_registry()
    manager = PrismObjectManager(storage, filters)

    class FilteredRefHandler(BusinessHandler):
        async def process(self, request_type: str, payload: dict[str, Any]) -> dict[str, Any]:
            return {
                "user": ObjectReference(
                    id="user-1",
                    version=1,
                    filter_type="fields",  # Request specific filter
                )
            }

    handler = FilteredRefHandler()
    router = RequestRouter(manager, handler)

    # Create object with multiple fields
    user_obj = PrismObject(
        id="user-1",
        version=1,
        data={"name": "Alice", "age": 30, "email": "alice@example.com", "password": "secret"},
    )
    await storage.save(user_obj)

    # Make request
    request = RequestMessage(
        request_id="req-1",
        request_type="getUser",
        payload={},
        options={"hydrate_refs": True},
    )

    response = await router.handle_request("client-1", request)

    assert response.success is True
    # The reference should use the filter_type from the ObjectReference
    # (This test verifies the logic exists, actual filtering depends on filter implementation)
