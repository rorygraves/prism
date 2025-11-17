"""Base filter classes and filter registry."""

from abc import ABC, abstractmethod
from collections.abc import Callable
from typing import Any

from prism.core.types import PrismObject


class Filter(ABC):
    """Base class for object filters.

    Filters transform objects before transmission, serving dual purposes:
    - Security: Server-enforced filters limiting data exposure
    - Optimization: Client-requested filters reducing payload size
    """

    def __init__(self, name: str, cacheable: bool = True, client_mutable: bool = False):
        """Initialize filter.

        Args:
            name: Unique filter name
            cacheable: Whether filtered results can be cached
            client_mutable: Whether clients can request this filter
        """
        self.name = name
        self.cacheable = cacheable
        self.client_mutable = client_mutable

    @abstractmethod
    def apply(self, obj: PrismObject, params: dict[str, Any] | None = None) -> PrismObject:
        """Apply filter to an object.

        Args:
            obj: The object to filter
            params: Optional parameters for the filter

        Returns:
            Filtered PrismObject (same ID and version, transformed data)
        """
        pass


class FunctionFilter(Filter):
    """Filter that wraps a simple transformation function."""

    def __init__(
        self,
        name: str,
        transform: Callable[[dict[str, Any], dict[str, Any] | None], dict[str, Any]],
        cacheable: bool = True,
        client_mutable: bool = False,
    ):
        """Initialize function-based filter.

        Args:
            name: Unique filter name
            transform: Function that transforms object data
            cacheable: Whether results can be cached
            client_mutable: Whether clients can request this filter
        """
        super().__init__(name, cacheable, client_mutable)
        self.transform = transform

    def apply(self, obj: PrismObject, params: dict[str, Any] | None = None) -> PrismObject:
        """Apply transformation function to object."""
        filtered_data = self.transform(obj.data, params)
        return PrismObject(id=obj.id, version=obj.version, data=filtered_data)


class FilterRegistry:
    """Registry for managing available filters."""

    def __init__(self) -> None:
        self._filters: dict[str, Filter] = {}

    def register(self, filter_obj: Filter) -> None:
        """Register a filter.

        Args:
            filter_obj: The filter to register

        Raises:
            ValueError: If a filter with this name already exists
        """
        if filter_obj.name in self._filters:
            raise ValueError(f"Filter '{filter_obj.name}' already registered")
        self._filters[filter_obj.name] = filter_obj

    def register_function(
        self,
        name: str,
        transform: Callable[[dict[str, Any], dict[str, Any] | None], dict[str, Any]],
        cacheable: bool = True,
        client_mutable: bool = False,
    ) -> None:
        """Register a simple function as a filter.

        Args:
            name: Unique filter name
            transform: Transformation function
            cacheable: Whether results can be cached
            client_mutable: Whether clients can request this filter
        """
        filter_obj = FunctionFilter(name, transform, cacheable, client_mutable)
        self.register(filter_obj)

    def get(self, name: str) -> Filter:
        """Get a filter by name.

        Args:
            name: Filter name

        Returns:
            The requested filter

        Raises:
            KeyError: If filter not found
        """
        if name not in self._filters:
            raise KeyError(f"Filter '{name}' not found")
        return self._filters[name]

    def has(self, name: str) -> bool:
        """Check if a filter exists."""
        return name in self._filters

    def apply(
        self, obj: PrismObject, filter_name: str, params: dict[str, Any] | None = None
    ) -> PrismObject:
        """Apply a filter to an object.

        Args:
            obj: The object to filter
            filter_name: Name of the filter to apply
            params: Optional filter parameters

        Returns:
            Filtered object
        """
        filter_obj = self.get(filter_name)
        return filter_obj.apply(obj, params)
