package prism.cache

import cats.effect.{IO, Ref}

/** Thread-safe LRU (Least Recently Used) cache implementation.
  *
  * This cache maintains a maximum size and evicts the least recently used entries when full. Access order is tracked,
  * and both get and put operations update the access time.
  *
  * Thread-safe implementation using cats-effect Ref.
  *
  * @param maxSize
  *   Maximum number of entries in the cache
  * @tparam K
  *   Key type
  * @tparam V
  *   Value type
  */
class LRUCache[K, V] private (
    maxSize: Int,
    state: Ref[IO, LRUCache.CacheState[K, V]]
) {

  /** Get a value from the cache.
    *
    * If the key exists, updates its access time and returns Some(value). If the key doesn't exist, returns None.
    *
    * @param key
    *   The key to look up
    * @return
    *   Option containing the value if found
    */
  def get(key: K): IO[Option[V]] = {
    state.modify { s =>
      s.entries.get(key) match {
        case Some(entry) =>
          // Update access order by removing and re-adding
          val newEntries = (s.entries - key) + (key -> entry)
          (LRUCache.CacheState(newEntries), Some(entry.value))
        case None =>
          (s, None)
      }
    }
  }

  /** Put a value into the cache.
    *
    * If the cache is at max capacity and the key doesn't exist, evicts the least recently used entry. If the key
    * already exists, updates its value and access time.
    *
    * @param key
    *   The key to store
    * @param value
    *   The value to store
    */
  def put(key: K, value: V): IO[Unit] = {
    state.update { s =>
      val newEntry = LRUCache.CacheEntry(value)

      if (s.entries.contains(key)) {
        // Key exists - update value and move to end (most recent)
        val newEntries = (s.entries - key) + (key -> newEntry)
        LRUCache.CacheState(newEntries)
      } else {
        // New key
        if (s.entries.size >= maxSize) {
          // Evict least recently used (first entry in LinkedHashMap)
          val lruKey = s.entries.head._1
          val afterEviction = s.entries - lruKey
          val newEntries = afterEviction + (key -> newEntry)
          LRUCache.CacheState(newEntries)
        } else {
          // Still room in cache
          val newEntries = s.entries + (key -> newEntry)
          LRUCache.CacheState(newEntries)
        }
      }
    }
  }

  /** Remove a key from the cache.
    *
    * @param key
    *   The key to remove
    * @return
    *   true if the key was present and removed, false otherwise
    */
  def remove(key: K): IO[Boolean] = {
    state.modify { s =>
      if (s.entries.contains(key)) {
        (LRUCache.CacheState(s.entries - key), true)
      } else {
        (s, false)
      }
    }
  }

  /** Clear all entries from the cache. */
  def clear(): IO[Unit] = {
    state.set(LRUCache.CacheState(scala.collection.immutable.ListMap.empty))
  }

  /** Get the current number of entries in the cache. */
  def size: IO[Int] = {
    state.get.map(_.entries.size)
  }

  /** Check if the cache contains a key.
    *
    * Note: This does not update access order.
    *
    * @param key
    *   The key to check
    * @return
    *   true if the key exists
    */
  def contains(key: K): IO[Boolean] = {
    state.get.map(_.entries.contains(key))
  }

  /** Get all keys in the cache in access order (least recently used first).
    *
    * @return
    *   List of keys in LRU order
    */
  def keys: IO[List[K]] = {
    state.get.map(_.entries.keys.toList)
  }
}

object LRUCache {

  /** Cache entry containing a value. */
  private case class CacheEntry[V](value: V)

  /** Internal cache state using ListMap to maintain insertion order. */
  private case class CacheState[K, V](entries: scala.collection.immutable.ListMap[K, CacheEntry[V]])

  /** Create a new LRU cache.
    *
    * @param maxSize
    *   Maximum number of entries (must be > 0)
    * @tparam K
    *   Key type
    * @tparam V
    *   Value type
    * @return
    *   A new LRUCache instance wrapped in IO
    */
  def create[K, V](maxSize: Int): IO[LRUCache[K, V]] = {
    require(maxSize > 0, s"maxSize must be > 0, got $maxSize")

    for {
      state <- Ref.of[IO, CacheState[K, V]](CacheState(scala.collection.immutable.ListMap.empty))
    } yield new LRUCache(maxSize, state)
  }
}
