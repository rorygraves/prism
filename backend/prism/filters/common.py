"""Common filter implementations."""

from typing import Any

from prism.core.types import PrismObject
from prism.filters.base import Filter


class FieldsFilter(Filter):
    """Filter that includes only specified fields."""

    def __init__(self, client_mutable: bool = True) -> None:
        super().__init__("fields", cacheable=True, client_mutable=client_mutable)

    def apply(self, obj: PrismObject, params: dict[str, Any] | None = None) -> PrismObject:
        """Apply fields filter.

        Args:
            obj: Object to filter
            params: Must contain 'fields' key with list of field names

        Returns:
            Object with only specified fields
        """
        if not params or "fields" not in params:
            return obj

        fields = params["fields"]
        if not isinstance(fields, list):
            return obj

        filtered_data = {k: v for k, v in obj.data.items() if k in fields}
        return PrismObject(id=obj.id, version=obj.version, data=filtered_data)


class ExcludeFieldsFilter(Filter):
    """Filter that excludes specified fields."""

    def __init__(self, client_mutable: bool = True) -> None:
        super().__init__("exclude", cacheable=True, client_mutable=client_mutable)

    def apply(self, obj: PrismObject, params: dict[str, Any] | None = None) -> PrismObject:
        """Apply exclude filter.

        Args:
            obj: Object to filter
            params: Must contain 'fields' key with list of field names to exclude

        Returns:
            Object without excluded fields
        """
        if not params or "fields" not in params:
            return obj

        exclude_fields = params["fields"]
        if not isinstance(exclude_fields, list):
            return obj

        filtered_data = {k: v for k, v in obj.data.items() if k not in exclude_fields}
        return PrismObject(id=obj.id, version=obj.version, data=filtered_data)


class SecurityFilter(Filter):
    """Filter for security-sensitive data (server-enforced only)."""

    def __init__(self, hidden_fields: list[str]) -> None:
        """Initialize security filter.

        Args:
            hidden_fields: Fields to always exclude
        """
        super().__init__("security", cacheable=True, client_mutable=False)
        self.hidden_fields = set(hidden_fields)

    def apply(self, obj: PrismObject, params: dict[str, Any] | None = None) -> PrismObject:
        """Apply security filter by excluding sensitive fields."""
        filtered_data = {k: v for k, v in obj.data.items() if k not in self.hidden_fields}
        return PrismObject(id=obj.id, version=obj.version, data=filtered_data)


def create_default_registry() -> Any:
    """Create a filter registry with common filters.

    Returns:
        FilterRegistry with standard filters registered
    """
    from prism.filters.base import FilterRegistry

    registry = FilterRegistry()
    registry.register(FieldsFilter())
    registry.register(ExcludeFieldsFilter())

    # Register a default passthrough filter
    registry.register_function(
        "default", lambda data, params: data, cacheable=True, client_mutable=True
    )

    return registry
