"""Tests for filter system."""

import pytest

from prism.core.types import PrismObject
from prism.filters.base import FilterRegistry, FunctionFilter
from prism.filters.common import ExcludeFieldsFilter, FieldsFilter, create_default_registry


def test_fields_filter():
    """Test fields filter that includes only specified fields."""
    obj = PrismObject(
        id="test-1", version=1, data={"name": "Alice", "age": 30, "email": "alice@example.com"}
    )

    filter_obj = FieldsFilter()
    filtered = filter_obj.apply(obj, {"fields": ["name", "email"]})

    assert filtered.id == obj.id
    assert filtered.version == obj.version
    assert "name" in filtered.data
    assert "email" in filtered.data
    assert "age" not in filtered.data


def test_exclude_fields_filter():
    """Test exclude filter that removes specified fields."""
    obj = PrismObject(
        id="test-1",
        version=1,
        data={"name": "Alice", "age": 30, "password": "secret", "email": "alice@example.com"},
    )

    filter_obj = ExcludeFieldsFilter()
    filtered = filter_obj.apply(obj, {"fields": ["password"]})

    assert filtered.id == obj.id
    assert filtered.version == obj.version
    assert "name" in filtered.data
    assert "age" in filtered.data
    assert "email" in filtered.data
    assert "password" not in filtered.data


def test_filter_registry():
    """Test filter registry."""
    registry = FilterRegistry()

    # Register a simple filter
    def uppercase_name(data, params):
        return {**data, "name": data.get("name", "").upper()}

    registry.register_function("uppercase", uppercase_name)

    obj = PrismObject(id="test-1", version=1, data={"name": "alice", "age": 30})
    filtered = registry.apply(obj, "uppercase")

    assert filtered.data["name"] == "ALICE"
    assert filtered.data["age"] == 30


def test_filter_registry_get_nonexistent():
    """Test getting non-existent filter raises error."""
    registry = FilterRegistry()

    with pytest.raises(KeyError):
        registry.get("nonexistent")


def test_filter_registry_duplicate_registration():
    """Test registering duplicate filter raises error."""
    registry = FilterRegistry()

    def dummy(data, params):
        return data

    registry.register_function("test", dummy)

    with pytest.raises(ValueError, match="already registered"):
        registry.register_function("test", dummy)


def test_default_registry():
    """Test default registry has common filters."""
    registry = create_default_registry()

    assert registry.has("fields")
    assert registry.has("exclude")
    assert registry.has("default")


def test_function_filter():
    """Test function-based filter."""

    def add_prefix(data, params):
        prefix = params.get("prefix", "") if params else ""
        return {**data, "name": f"{prefix}{data.get('name', '')}"}

    filter_obj = FunctionFilter("prefix", add_prefix)
    obj = PrismObject(id="test-1", version=1, data={"name": "Alice"})

    filtered = filter_obj.apply(obj, {"prefix": "Ms. "})

    assert filtered.data["name"] == "Ms. Alice"


def test_filter_with_no_params():
    """Test filter with no parameters."""
    obj = PrismObject(id="test-1", version=1, data={"name": "Alice", "age": 30})

    filter_obj = FieldsFilter()
    filtered = filter_obj.apply(obj, None)

    # Should return unchanged when no params
    assert filtered.data == obj.data


def test_filter_preserves_object_metadata():
    """Test that filters preserve object ID and version."""
    obj = PrismObject(id="test-123", version=5, data={"name": "Alice", "age": 30})

    filter_obj = FieldsFilter()
    filtered = filter_obj.apply(obj, {"fields": ["name"]})

    assert filtered.id == "test-123"
    assert filtered.version == 5
