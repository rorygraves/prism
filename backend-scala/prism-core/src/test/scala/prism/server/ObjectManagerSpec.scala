package prism.server

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import prism.core.Types.PrismObject
import prism.filters.CommonFilters
import prism.storage.MemoryStorageAdapter

class ObjectManagerSpec extends FunSuite {

  // Helper to run IO tests
  def runIO[A](io: cats.effect.IO[A]): A = io.unsafeRunSync()

  // Helper to create ObjectManager with storage and filters
  def createObjectManager() = runIO {
    for {
      storage <- MemoryStorageAdapter.create
      filterRegistry = CommonFilters.createDefaultRegistry()
      manager <- ObjectManager.create(storage, filterRegistry)
    } yield (manager, storage)
  }

  // ========== Client State Management ==========

  test("getClientState - creates new state for new client") {
    val (manager, _) = createObjectManager()

    val state = runIO(manager.getClientState("client-1"))

    assertEquals(state.subscriptions.size, 0)
    assertEquals(state.objectVersions.size, 0)
  }

  test("getClientState - returns same state for same client") {
    val (manager, _) = createObjectManager()

    val result = runIO {
      for {
        state1 <- manager.getClientState("client-1")
        _ = state1.updateVersion("obj-1", 5)
        state2 <- manager.getClientState("client-1")
      } yield state2.getVersion("obj-1")
    }

    assertEquals(result, Some(5))
  }

  test("removeClientState - removes client state") {
    val (manager, _) = createObjectManager()

    val result = runIO {
      for {
        state1 <- manager.getClientState("client-1")
        _ = state1.updateVersion("obj-1", 5)
        _ <- manager.removeClientState("client-1")
        state2 <- manager.getClientState("client-1")
      } yield state2.getVersion("obj-1")
    }

    // New state should be empty
    assertEquals(result, None)
  }

  // ========== Subscribe/Unsubscribe ==========

  test("subscribe - creates subscription and returns object") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        retrieved <- manager.subscribe("client-1", "obj-1", ongoing = true)
        state <- manager.getClientState("client-1")
      } yield (retrieved, state.getSubscription("obj-1"))
    }

    val (retrieved, sub) = result
    assert(retrieved.isDefined)
    assertEquals(retrieved.get.id, "obj-1")
    assert(sub.isDefined)
    assertEquals(sub.get.temporary, false) // ongoing = true means temporary = false
  }

  test("subscribe - with filter applies filter") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30, "email" -> "alice@example.com"))
    val params = Some(Map("fields" -> ujson.Arr("name", "age")))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        retrieved <- manager.subscribe("client-1", "obj-1", "fields", params, ongoing = true)
      } yield retrieved
    }

    assert(result.isDefined)
    assert(result.get.data.obj.contains("name"))
    assert(result.get.data.obj.contains("age"))
    assert(!result.get.data.obj.contains("email"))
  }

  test("subscribe - returns None for non-existent object") {
    val (manager, _) = createObjectManager()

    val result = runIO {
      manager.subscribe("client-1", "non-existent", ongoing = true)
    }

    assertEquals(result, None)
  }

  test("unsubscribe - removes subscription") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        state1 <- manager.getClientState("client-1")
        hasBefore = state1.getSubscription("obj-1").isDefined
        _ <- manager.unsubscribe("client-1", "obj-1")
        state2 <- manager.getClientState("client-1")
        hasAfter = state2.getSubscription("obj-1").isDefined
      } yield (hasBefore, hasAfter)
    }

    assertEquals(result, (true, false))
  }

  test("updateFilter - updates subscription filter") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val params = Some(Map("fields" -> ujson.Arr("name")))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        _ <- manager.subscribe("client-1", "obj-1", "default", None, ongoing = true)
        state1 <- manager.getClientState("client-1")
        filterBefore = state1.getSubscription("obj-1").map(_.filterType)
        _ <- manager.updateFilter("client-1", "obj-1", "fields", params)
        state2 <- manager.getClientState("client-1")
        filterAfter = state2.getSubscription("obj-1").map(_.filterType)
      } yield (filterBefore, filterAfter)
    }

    assertEquals(result, (Some("default"), Some("fields")))
  }

  // ========== Save and Notify ==========

  test("saveAndNotify - saves object and returns notified clients") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Bob"))

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        _ <- manager.subscribe("client-2", "obj-1", ongoing = true)
        _ <- manager.subscribe("client-3", "obj-1", ongoing = false) // Not ongoing
        notified <- manager.saveAndNotify(obj2)
        retrieved <- storage.getCurrent("obj-1")
      } yield (notified.sorted, retrieved)
    }

    val (notified, retrieved) = result
    assertEquals(notified, List("client-1", "client-2"))
    assert(retrieved.isDefined)
    assertEquals(retrieved.get.version, 2)
  }

  // ========== Get Object with Caching ==========

  test("getObject - retrieves current version") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        retrieved <- manager.getObject("obj-1")
      } yield retrieved
    }

    assert(result.isDefined)
    assertEquals(result.get.id, "obj-1")
    assertEquals(result.get.version, 1)
  }

  test("getObject - retrieves specific version") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Bob"))

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        retrieved <- manager.getObject("obj-1", Some(1))
      } yield retrieved
    }

    assert(result.isDefined)
    assertEquals(result.get.version, 1)
    assertEquals(result.get.data("name").str, "Alice")
  }

  test("getObject - caches version") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        // First call - cache miss
        retrieved1 <- manager.getObject("obj-1", Some(1))
        // Second call - cache hit
        retrieved2 <- manager.getObject("obj-1", Some(1))
      } yield (retrieved1, retrieved2)
    }

    val (r1, r2) = result
    assert(r1.isDefined && r2.isDefined)
    assertEquals(r1.get.id, r2.get.id)
  }

  // ========== Delta Computation with Caching ==========

  test("getDelta - computes delta between versions") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Alice", "age" -> 31))

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        delta <- manager.getDelta("obj-1", 1, 2)
      } yield delta
    }

    assert(result.isDefined)
    assertEquals(result.get.fromVersion, 1)
    assertEquals(result.get.toVersion, 2)
    assert(result.get.patches.nonEmpty)
  }

  test("getDelta - returns None for non-existent versions") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        delta <- manager.getDelta("obj-1", 1, 99)
      } yield delta
    }

    assertEquals(result, None)
  }

  test("getDelta - caches deltas") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Bob"))

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        // First call - cache miss
        delta1 <- manager.getDelta("obj-1", 1, 2)
        // Second call - cache hit
        delta2 <- manager.getDelta("obj-1", 1, 2)
      } yield (delta1, delta2)
    }

    val (d1, d2) = result
    assert(d1.isDefined && d2.isDefined)
    assertEquals(d1.get.patches, d2.get.patches)
  }

  // ========== Filter Application with Caching ==========

  test("applyFilter - applies filter to object") {
    val (manager, _) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30, "email" -> "alice@example.com"))
    val params = Some(Map("fields" -> ujson.Arr("name", "age")))

    val result = runIO {
      manager.applyFilter(obj, Some("fields"), params)
    }

    assert(result.data.obj.contains("name"))
    assert(result.data.obj.contains("age"))
    assert(!result.data.obj.contains("email"))
  }

  test("applyFilter - returns unchanged if no filter") {
    val (manager, _) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val result = runIO {
      manager.applyFilter(obj, None, None)
    }

    assertEquals(result.data, ujson.Obj("name" -> "Alice"))
  }

  test("applyFilter - caches filtered objects") {
    val (manager, _) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30))
    val params = Some(Map("fields" -> ujson.Arr("name")))

    val result = runIO {
      for {
        // First call - cache miss
        filtered1 <- manager.applyFilter(obj, Some("fields"), params)
        // Second call - cache hit
        filtered2 <- manager.applyFilter(obj, Some("fields"), params)
      } yield (filtered1, filtered2)
    }

    val (f1, f2) = result
    assertEquals(f1.data, f2.data)
  }

  // ========== Delete and Notify ==========

  test("deleteAndNotify - deletes object and notifies subscribers") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        _ <- manager.subscribe("client-2", "obj-1", ongoing = true)
        notified <- manager.deleteAndNotify("obj-1")
        retrieved <- storage.getCurrent("obj-1")
        state <- manager.getClientState("client-1")
        hasSub = state.getSubscription("obj-1").isDefined
      } yield (notified.sorted, retrieved, hasSub)
    }

    val (notified, retrieved, hasSub) = result
    assertEquals(notified, List("client-1", "client-2"))
    assertEquals(retrieved, None)
    assertEquals(hasSub, false)
  }

  // ========== List Objects ==========

  test("listObjects - returns object IDs") {
    val (manager, storage) = createObjectManager()

    val result = runIO {
      for {
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-2", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-3", 1, ujson.Obj()))
        ids <- manager.listObjects()
      } yield ids
    }

    assertEquals(result.sorted, List("obj-1", "obj-2", "obj-3"))
  }

  test("listObjects - respects limit and offset") {
    val (manager, storage) = createObjectManager()

    val result = runIO {
      for {
        _ <- storage.save(PrismObject("obj-1", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-2", 1, ujson.Obj()))
        _ <- storage.save(PrismObject("obj-3", 1, ujson.Obj()))
        ids <- manager.listObjects(limit = 2, offset = 1)
      } yield ids
    }

    assertEquals(result, List("obj-2", "obj-3"))
  }

  // ========== Temporary Subscriptions ==========

  test("clearTemporarySubscriptions - removes only temporary subs") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj())
    val obj2 = PrismObject("obj-2", 1, ujson.Obj())

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        _ <- manager.subscribe("client-1", "obj-2", ongoing = false)
        state1 <- manager.getClientState("client-1")
        countBefore = state1.subscriptions.size
        _ <- manager.clearTemporarySubscriptions("client-1")
        state2 <- manager.getClientState("client-1")
        countAfter = state2.subscriptions.size
        has1 = state2.getSubscription("obj-1").isDefined
        has2 = state2.getSubscription("obj-2").isDefined
      } yield (countBefore, countAfter, has1, has2)
    }

    val (before, after, has1, has2) = result
    assertEquals(before, 2)
    assertEquals(after, 1)
    assertEquals(has1, true)
    assertEquals(has2, false)
  }

  // ========== Active Subscriptions ==========

  test("getActiveSubscriptions - returns ongoing subscriptions") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj())
    val obj2 = PrismObject("obj-2", 1, ujson.Obj())

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        _ <- manager.subscribe("client-1", "obj-2", ongoing = false)
        subs <- manager.getActiveSubscriptions("client-1")
      } yield subs.map(_.objectId).toList.sorted
    }

    assertEquals(result, List("obj-1"))
  }
}
