"""Tests for delta computation."""

import pytest
from prism.core.types import PrismObject, Delta
from prism.core.delta import DeltaComputer


def test_compute_delta_basic():
    """Test basic delta computation."""
    obj1 = PrismObject(
        id="test-1", version=1, data={"name": "Alice", "age": 30, "city": "NYC"}
    )
    obj2 = PrismObject(
        id="test-1", version=2, data={"name": "Alice", "age": 31, "city": "SF"}
    )

    computer = DeltaComputer()
    delta = computer.compute_delta(obj1, obj2)

    assert delta.object_id == "test-1"
    assert delta.from_version == 1
    assert delta.to_version == 2
    assert len(delta.patches) > 0


def test_apply_delta():
    """Test applying a delta to an object."""
    obj1 = PrismObject(id="test-1", version=1, data={"count": 10, "status": "active"})
    obj2 = PrismObject(id="test-1", version=2, data={"count": 15, "status": "active"})

    computer = DeltaComputer()
    delta = computer.compute_delta(obj1, obj2)

    # Apply delta
    result = computer.apply_delta(obj1, delta)

    assert result.id == "test-1"
    assert result.version == 2
    assert result.data["count"] == 15
    assert result.data["status"] == "active"


def test_delta_with_different_objects_raises():
    """Test that computing delta with different object IDs raises error."""
    obj1 = PrismObject(id="test-1", version=1, data={"a": 1})
    obj2 = PrismObject(id="test-2", version=2, data={"a": 2})

    computer = DeltaComputer()
    with pytest.raises(ValueError, match="different objects"):
        computer.compute_delta(obj1, obj2)


def test_delta_with_invalid_version_order_raises():
    """Test that computing delta with invalid version order raises error."""
    obj1 = PrismObject(id="test-1", version=2, data={"a": 1})
    obj2 = PrismObject(id="test-1", version=1, data={"a": 2})

    computer = DeltaComputer()
    with pytest.raises(ValueError, match="Invalid version ordering"):
        computer.compute_delta(obj1, obj2)


def test_estimate_delta_size():
    """Test delta size estimation."""
    obj1 = PrismObject(id="test-1", version=1, data={"a": 1})
    obj2 = PrismObject(id="test-1", version=2, data={"a": 2})

    computer = DeltaComputer()
    delta = computer.compute_delta(obj1, obj2)

    size = computer.estimate_delta_size(delta)
    assert size > 0
    assert isinstance(size, int)


def test_is_delta_efficient():
    """Test delta efficiency check."""
    # Small change - delta should be efficient
    obj1 = PrismObject(id="test-1", version=1, data={"field": "value" * 100})
    obj2 = PrismObject(id="test-1", version=2, data={"field": "value" * 100, "new": "x"})

    computer = DeltaComputer()
    delta = computer.compute_delta(obj1, obj2)

    assert computer.is_delta_efficient(delta, obj2)


def test_delta_with_nested_objects():
    """Test delta computation with nested objects."""
    obj1 = PrismObject(
        id="test-1",
        version=1,
        data={"user": {"name": "Alice", "settings": {"theme": "dark", "lang": "en"}}},
    )
    obj2 = PrismObject(
        id="test-1",
        version=2,
        data={"user": {"name": "Alice", "settings": {"theme": "light", "lang": "en"}}},
    )

    computer = DeltaComputer()
    delta = computer.compute_delta(obj1, obj2)
    result = computer.apply_delta(obj1, delta)

    assert result.data["user"]["settings"]["theme"] == "light"
    assert result.data["user"]["settings"]["lang"] == "en"


def test_delta_with_array_changes():
    """Test delta computation with array changes."""
    obj1 = PrismObject(id="test-1", version=1, data={"tags": ["python", "web"]})
    obj2 = PrismObject(id="test-1", version=2, data={"tags": ["python", "web", "prism"]})

    computer = DeltaComputer()
    delta = computer.compute_delta(obj1, obj2)
    result = computer.apply_delta(obj1, delta)

    assert result.data["tags"] == ["python", "web", "prism"]
