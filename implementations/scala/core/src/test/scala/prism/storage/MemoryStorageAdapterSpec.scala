package prism.storage

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import prism.core.Types.PrismObject

class MemoryStorageAdapterSpec extends FunSuite {

  // Helper to run IO tests
  def runIO[A](io: cats.effect.IO[A]): A = io.unsafeRunSync()

  test("save - stores object") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "test"))
        _ <- storage.save(obj)
        retrieved <- storage.getCurrent("obj-1")
      } yield retrieved
    }

    assert(result.isDefined)
    assertEquals(result.get.id, "obj-1")
    assertEquals(result.get.version, 1)
  }

  test("save - multiple versions") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
        obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Bob"))
        obj3 = PrismObject("obj-1", 3, ujson.Obj("name" -> "Charlie"))
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        _ <- storage.save(obj3)
        current <- storage.getCurrent("obj-1")
        v1 <- storage.getVersion("obj-1", 1)
        v2 <- storage.getVersion("obj-1", 2)
      } yield (current, v1, v2)
    }

    val (current, v1, v2) = result

    // Current should be version 3
    assert(current.isDefined)
    assertEquals(current.get.version, 3)
    assertEquals(current.get.data("name").str, "Charlie")

    // Can still retrieve old versions
    assert(v1.isDefined)
    assertEquals(v1.get.data("name").str, "Alice")
    assert(v2.isDefined)
    assertEquals(v2.get.data("name").str, "Bob")
  }

  test("getCurrent - returns None for non-existent object") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        retrieved <- storage.getCurrent("non-existent")
      } yield retrieved
    }

    assertEquals(result, None)
  }

  test("getCurrent - returns latest version") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 5, ujson.Obj("v" -> 5)))
        _ <- storage.save(PrismObject("obj-1", 10, ujson.Obj("v" -> 10)))
        _ <- storage.save(PrismObject("obj-1", 7, ujson.Obj("v" -> 7))) // Out of order
        current <- storage.getCurrent("obj-1")
      } yield current
    }

    assert(result.isDefined)
    assertEquals(result.get.version, 10) // Should be the highest version
  }

  test("getVersion - retrieves specific version") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj("v" -> 1)))
        _ <- storage.save(PrismObject("obj-1", 2, ujson.Obj("v" -> 2)))
        _ <- storage.save(PrismObject("obj-1", 3, ujson.Obj("v" -> 3)))
        v2 <- storage.getVersion("obj-1", 2)
      } yield v2
    }

    assert(result.isDefined)
    assertEquals(result.get.version, 2)
    assertEquals(result.get.data("v").num, 2.0)
  }

  test("getVersion - returns None for non-existent version") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj()))
        v99 <- storage.getVersion("obj-1", 99)
      } yield v99
    }

    assertEquals(result, None)
  }

  test("getVersionsRange - retrieves range of versions") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj("v" -> 1)))
        _ <- storage.save(PrismObject("obj-1", 2, ujson.Obj("v" -> 2)))
        _ <- storage.save(PrismObject("obj-1", 3, ujson.Obj("v" -> 3)))
        _ <- storage.save(PrismObject("obj-1", 5, ujson.Obj("v" -> 5)))
        range <- storage.getVersionsRange("obj-1", 2, 4)
      } yield range
    }

    assertEquals(result.length, 2) // Only v2 and v3 exist in range 2-4
    assertEquals(result(0).version, 2)
    assertEquals(result(1).version, 3)
  }

  test("getVersionsRange - empty for non-existent object") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        range <- storage.getVersionsRange("non-existent", 1, 10)
      } yield range
    }

    assertEquals(result, List.empty)
  }

  test("getVersionsRange - includes endpoints") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 5, ujson.Obj("v" -> 5)))
        _ <- storage.save(PrismObject("obj-1", 10, ujson.Obj("v" -> 10)))
        range <- storage.getVersionsRange("obj-1", 5, 10)
      } yield range
    }

    assertEquals(result.length, 2)
    assertEquals(result(0).version, 5)
    assertEquals(result(1).version, 10)
  }

  test("delete - removes all versions") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-1", 2, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-1", 3, ujson.Obj()))
        _ <- storage.delete("obj-1")
        current <- storage.getCurrent("obj-1")
        v1 <- storage.getVersion("obj-1", 1)
      } yield (current, v1)
    }

    val (current, v1) = result
    assertEquals(current, None)
    assertEquals(v1, None)
  }

  test("delete - does not affect other objects") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-2", 1, ujson.Obj()))
        _ <- storage.delete("obj-1")
        obj1 <- storage.getCurrent("obj-1")
        obj2 <- storage.getCurrent("obj-2")
      } yield (obj1, obj2)
    }

    val (obj1, obj2) = result
    assertEquals(obj1, None)
    assert(obj2.isDefined)
  }

  test("listObjects - returns sorted object IDs") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-3", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-2", 1, ujson.Obj()))
        ids <- storage.listObjects()
      } yield ids
    }

    assertEquals(result, List("obj-1", "obj-2", "obj-3"))
  }

  test("listObjects - respects limit") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-2", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-3", 1, ujson.Obj()))
        ids <- storage.listObjects(limit = 2)
      } yield ids
    }

    assertEquals(result.length, 2)
    assertEquals(result, List("obj-1", "obj-2"))
  }

  test("listObjects - respects offset") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-2", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-3", 1, ujson.Obj()))
        ids <- storage.listObjects(offset = 1)
      } yield ids
    }

    assertEquals(result, List("obj-2", "obj-3"))
  }

  test("listObjects - respects limit and offset") {
    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-2", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-3", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-4", 1, ujson.Obj()))
        ids <- storage.listObjects(limit = 2, offset = 1)
      } yield ids
    }

    assertEquals(result, List("obj-2", "obj-3"))
  }

  test("concurrent saves are thread-safe") {
    import cats.syntax.all._

    val result = runIO {
      for {
        storage <- MemoryStorageAdapter.create
        // Save 100 versions concurrently
        saves = (1 to 100).toList.map { v =>
          storage.save(PrismObject("obj-1", v, ujson.Obj("v" -> v)))
        }
        _ <- saves.parTraverse(identity)
        current <- storage.getCurrent("obj-1")
        allVersions <- storage.getVersionsRange("obj-1", 1, 100)
      } yield (current, allVersions)
    }

    val (current, allVersions) = result
    assert(current.isDefined)
    assertEquals(current.get.version, 100)
    assertEquals(allVersions.length, 100)
  }
}
