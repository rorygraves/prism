"""Tests for LRU cache."""

from prism.server.cache import LRUCache


def test_cache_put_and_get():
    """Test basic cache put and get operations."""
    cache: LRUCache[str] = LRUCache(capacity=3)

    cache.put("key1", "value1")
    cache.put("key2", "value2")

    assert cache.get("key1") == "value1"
    assert cache.get("key2") == "value2"


def test_cache_get_nonexistent():
    """Test getting non-existent key returns None."""
    cache: LRUCache[str] = LRUCache(capacity=3)

    assert cache.get("nonexistent") is None


def test_cache_lru_eviction():
    """Test LRU eviction when capacity is exceeded."""
    cache: LRUCache[str] = LRUCache(capacity=2)

    cache.put("key1", "value1")
    cache.put("key2", "value2")
    cache.put("key3", "value3")  # Should evict key1

    assert cache.get("key1") is None
    assert cache.get("key2") == "value2"
    assert cache.get("key3") == "value3"


def test_cache_access_updates_lru():
    """Test that accessing a key updates its position in LRU."""
    cache: LRUCache[str] = LRUCache(capacity=2)

    cache.put("key1", "value1")
    cache.put("key2", "value2")

    # Access key1, making it most recently used
    cache.get("key1")

    # Add key3, should evict key2 (least recently used)
    cache.put("key3", "value3")

    assert cache.get("key1") == "value1"
    assert cache.get("key2") is None
    assert cache.get("key3") == "value3"


def test_cache_update_existing():
    """Test updating an existing key."""
    cache: LRUCache[str] = LRUCache(capacity=3)

    cache.put("key1", "value1")
    cache.put("key1", "updated")

    assert cache.get("key1") == "updated"
    assert len(cache) == 1


def test_cache_clear():
    """Test clearing the cache."""
    cache: LRUCache[str] = LRUCache(capacity=3)

    cache.put("key1", "value1")
    cache.put("key2", "value2")

    assert len(cache) == 2

    cache.clear()

    assert len(cache) == 0
    assert cache.get("key1") is None


def test_cache_len():
    """Test cache length."""
    cache: LRUCache[str] = LRUCache(capacity=5)

    assert len(cache) == 0

    cache.put("key1", "value1")
    assert len(cache) == 1

    cache.put("key2", "value2")
    cache.put("key3", "value3")
    assert len(cache) == 3
