"""Caching utilities for Prism server."""

from collections import OrderedDict
from typing import Generic, TypeVar

T = TypeVar("T")


class LRUCache(Generic[T]):
    """Simple LRU cache implementation."""

    def __init__(self, capacity: int):
        """Initialize LRU cache.

        Args:
            capacity: Maximum number of items to cache
        """
        self.capacity = capacity
        self.cache: OrderedDict[str, T] = OrderedDict()

    def get(self, key: str) -> T | None:
        """Get an item from cache.

        Args:
            key: Cache key

        Returns:
            Cached value or None if not found
        """
        if key not in self.cache:
            return None

        # Move to end (most recently used)
        self.cache.move_to_end(key)
        return self.cache[key]

    def put(self, key: str, value: T) -> None:
        """Put an item in cache.

        Args:
            key: Cache key
            value: Value to cache
        """
        if key in self.cache:
            # Update existing item
            self.cache.move_to_end(key)
        else:
            # Add new item
            if len(self.cache) >= self.capacity:
                # Remove oldest item
                self.cache.popitem(last=False)

        self.cache[key] = value

    def clear(self) -> None:
        """Clear all cached items."""
        self.cache.clear()

    def __len__(self) -> int:
        """Get number of cached items."""
        return len(self.cache)
