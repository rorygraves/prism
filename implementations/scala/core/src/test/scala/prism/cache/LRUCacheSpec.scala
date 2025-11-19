package prism.cache

import cats.effect.unsafe.implicits.global
import munit.FunSuite

class LRUCacheSpec extends FunSuite {

  // Helper to run IO tests
  def runIO[A](io: cats.effect.IO[A]): A = io.unsafeRunSync()

  // ========== Basic Operations ==========

  test("create - rejects invalid maxSize") {
    intercept[IllegalArgumentException] {
      runIO(LRUCache.create[String, String](0))
    }

    intercept[IllegalArgumentException] {
      runIO(LRUCache.create[String, String](-1))
    }
  }

  test("put and get - stores and retrieves value") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](10)
        _ <- cache.put("key1", 100)
        value <- cache.get("key1")
      } yield value
    }

    assertEquals(result, Some(100))
  }

  test("get - returns None for non-existent key") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](10)
        value <- cache.get("nonexistent")
      } yield value
    }

    assertEquals(result, None)
  }

  test("put - updates existing key") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](10)
        _ <- cache.put("key1", 100)
        _ <- cache.put("key1", 200)
        value <- cache.get("key1")
      } yield value
    }

    assertEquals(result, Some(200))
  }

  test("size - returns correct count") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](10)
        size0 <- cache.size
        _ <- cache.put("key1", 1)
        size1 <- cache.size
        _ <- cache.put("key2", 2)
        size2 <- cache.size
      } yield (size0, size1, size2)
    }

    assertEquals(result, (0, 1, 2))
  }

  test("contains - checks key existence") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](10)
        before <- cache.contains("key1")
        _ <- cache.put("key1", 100)
        after <- cache.contains("key1")
      } yield (before, after)
    }

    assertEquals(result, (false, true))
  }

  test("remove - removes key and returns true if existed") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](10)
        _ <- cache.put("key1", 100)
        removed1 <- cache.remove("key1")
        value <- cache.get("key1")
        removed2 <- cache.remove("key1")
      } yield (removed1, value, removed2)
    }

    assertEquals(result, (true, None, false))
  }

  test("clear - removes all entries") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](10)
        _ <- cache.put("key1", 1)
        _ <- cache.put("key2", 2)
        _ <- cache.put("key3", 3)
        sizeBefore <- cache.size
        _ <- cache.clear()
        sizeAfter <- cache.size
        value <- cache.get("key1")
      } yield (sizeBefore, sizeAfter, value)
    }

    assertEquals(result, (3, 0, None))
  }

  // ========== LRU Eviction ==========

  test("put - evicts LRU entry when cache is full") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](3)
        _ <- cache.put("key1", 1)
        _ <- cache.put("key2", 2)
        _ <- cache.put("key3", 3)
        // Cache is full: [key1, key2, key3]
        _ <- cache.put("key4", 4)
        // Should evict key1 (LRU)
        has1 <- cache.contains("key1")
        has2 <- cache.contains("key2")
        has3 <- cache.contains("key3")
        has4 <- cache.contains("key4")
        size <- cache.size
      } yield (has1, has2, has3, has4, size)
    }

    assertEquals(result, (false, true, true, true, 3))
  }

  test("get - updates access order") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](3)
        _ <- cache.put("key1", 1)
        _ <- cache.put("key2", 2)
        _ <- cache.put("key3", 3)
        // Cache: [key1, key2, key3]
        _ <- cache.get("key1") // Access key1, moves to end
        // Cache: [key2, key3, key1]
        _ <- cache.put("key4", 4)
        // Should evict key2 (now LRU)
        has1 <- cache.contains("key1")
        has2 <- cache.contains("key2")
        has3 <- cache.contains("key3")
        has4 <- cache.contains("key4")
      } yield (has1, has2, has3, has4)
    }

    assertEquals(result, (true, false, true, true))
  }

  test("put - updating existing key doesn't increase size") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](3)
        _ <- cache.put("key1", 1)
        _ <- cache.put("key2", 2)
        _ <- cache.put("key3", 3)
        sizeBefore <- cache.size
        _ <- cache.put("key2", 22) // Update existing
        sizeAfter <- cache.size
        _ <- cache.put("key4", 4)
        // Should evict key1 (LRU)
        has1 <- cache.contains("key1")
        has2 <- cache.contains("key2")
        value2 <- cache.get("key2")
      } yield (sizeBefore, sizeAfter, has1, has2, value2)
    }

    assertEquals(result, (3, 3, false, true, Some(22)))
  }

  test("keys - returns keys in LRU order") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](5)
        _ <- cache.put("key1", 1)
        _ <- cache.put("key2", 2)
        _ <- cache.put("key3", 3)
        keysBefore <- cache.keys
        _ <- cache.get("key1") // Access key1, moves to end
        keysAfter <- cache.keys
      } yield (keysBefore, keysAfter)
    }

    assertEquals(result._1, List("key1", "key2", "key3"))
    assertEquals(result._2, List("key2", "key3", "key1"))
  }

  // ========== Edge Cases ==========

  test("cache with maxSize=1 works correctly") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](1)
        _ <- cache.put("key1", 1)
        has1 <- cache.contains("key1")
        _ <- cache.put("key2", 2)
        has1After <- cache.contains("key1")
        has2 <- cache.contains("key2")
        size <- cache.size
      } yield (has1, has1After, has2, size)
    }

    assertEquals(result, (true, false, true, 1))
  }

  test("handles different value types") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, String](10)
        _ <- cache.put("greeting", "Hello, World!")
        value <- cache.get("greeting")
      } yield value
    }

    assertEquals(result, Some("Hello, World!"))
  }

  test("handles different key types") {
    val result = runIO {
      for {
        cache <- LRUCache.create[Int, String](10)
        _ <- cache.put(1, "one")
        _ <- cache.put(2, "two")
        value1 <- cache.get(1)
        value2 <- cache.get(2)
        value3 <- cache.get(3)
      } yield (value1, value2, value3)
    }

    assertEquals(result, (Some("one"), Some("two"), None))
  }

  test("remove - updates size correctly") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](5)
        _ <- cache.put("key1", 1)
        _ <- cache.put("key2", 2)
        _ <- cache.put("key3", 3)
        sizeBefore <- cache.size
        _ <- cache.remove("key2")
        sizeAfter <- cache.size
      } yield (sizeBefore, sizeAfter)
    }

    assertEquals(result, (3, 2))
  }

  // ========== Concurrency ==========

  test("concurrent operations are thread-safe") {
    import cats.syntax.all._

    val result = runIO {
      for {
        cache <- LRUCache.create[Int, String](100)
        // Put 50 items concurrently
        puts = (1 to 50).toList.map { i =>
          cache.put(i, s"value-$i")
        }
        _ <- puts.parTraverse(identity)
        // Get all 50 items concurrently
        gets = (1 to 50).toList.map { i =>
          cache.get(i)
        }
        values <- gets.parTraverse(identity)
        size <- cache.size
      } yield (values, size)
    }

    val (values, size) = result
    assertEquals(size, 50)
    assertEquals(values.flatten.length, 50)
    assert(values.forall(_.isDefined))
  }

  test("concurrent puts and evictions") {
    import cats.syntax.all._

    val result = runIO {
      for {
        cache <- LRUCache.create[Int, String](10)
        // Put 100 items concurrently (will trigger many evictions)
        puts = (1 to 100).toList.map { i =>
          cache.put(i, s"value-$i")
        }
        _ <- puts.parTraverse(identity)
        size <- cache.size
        allKeys <- cache.keys
      } yield (size, allKeys.length)
    }

    val (size, keyCount) = result
    // Should never exceed maxSize
    assertEquals(size, 10)
    assertEquals(keyCount, 10)
  }

  // ========== Complex Scenarios ==========

  test("complex access pattern maintains LRU order") {
    val result = runIO {
      for {
        cache <- LRUCache.create[String, Int](4)
        _ <- cache.put("a", 1)
        _ <- cache.put("b", 2)
        _ <- cache.put("c", 3)
        _ <- cache.put("d", 4)
        // Cache: [a, b, c, d]
        _ <- cache.get("b") // Access b
        // Cache: [a, c, d, b]
        _ <- cache.get("a") // Access a
        // Cache: [c, d, b, a]
        _ <- cache.put("e", 5) // Evict c
        // Cache: [d, b, a, e]
        hasC <- cache.contains("c")
        hasD <- cache.contains("d")
        _ <- cache.put("f", 6) // Evict d
        // Cache: [b, a, e, f]
        hasDAfter <- cache.contains("d")
        hasB <- cache.contains("b")
        keys <- cache.keys
      } yield (hasC, hasD, hasDAfter, hasB, keys)
    }

    val (hasC, hasD, hasDAfter, hasB, keys) = result
    assertEquals(hasC, false)
    assertEquals(hasD, true)
    assertEquals(hasDAfter, false)
    assertEquals(hasB, true)
    assertEquals(keys, List("b", "a", "e", "f"))
  }
}
