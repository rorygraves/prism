package prism.server

import cats.effect.unsafe.implicits.global
import munit.FunSuite
import prism.core.Types._
import prism.filters.CommonFilters
import prism.storage.MemoryStorageAdapter

class RequestRouterSpec extends FunSuite {

  // Helper to run IO tests
  def runIO[A](io: cats.effect.IO[A]): A = io.unsafeRunSync()

  // Helper to create router with manager and storage
  def createRouter() = runIO {
    for {
      storage <- MemoryStorageAdapter.create
      filterRegistry = CommonFilters.createDefaultRegistry()
      manager <- ObjectManager.create(storage, filterRegistry)
      router = RequestRouter.create(manager)
    } yield (router, manager, storage)
  }

  // ========== Hydrate Single Reference ==========

  test("hydrateReference - client has no version, returns full object") {
    val (router, _, storage) = createRouter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val ref = ObjectReference(id = "obj-1", version = 0)

    val result = runIO {
      for {
        _ <- storage.save(obj)
        hydrated <- router.hydrateReference("client-1", ref)
      } yield hydrated
    }

    assertEquals(result.id, "obj-1")
    assertEquals(result.version, 1)
    assert(result.data.isDefined)
    assertEquals(result.data.get, ujson.Obj("name" -> "Alice"))
    assertEquals(result.delta, None)
    assertEquals(result.cached, false)
  }

  test("hydrateReference - client has current version, returns cached") {
    val (router, manager, storage) = createRouter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val ref = ObjectReference(id = "obj-1", version = 0)

    val result = runIO {
      for {
        _ <- storage.save(obj)
        // Subscribe so client knows about version 1
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        // Manually update version tracking (subscribe doesn't do this automatically)
        state <- manager.getClientState("client-1")
        _ = state.updateVersion("obj-1", 1)
        hydrated <- router.hydrateReference("client-1", ref)
      } yield hydrated
    }

    assertEquals(result.id, "obj-1")
    assertEquals(result.version, 1)
    assertEquals(result.data, None)
    assertEquals(result.delta, None)
    assertEquals(result.cached, true)
  }

  test("hydrateReference - client has old version, returns delta if efficient") {
    val (router, _, storage) = createRouter()
    // Create larger objects so delta is genuinely efficient
    val baseData = ujson.Obj(
      "name" -> "Alice",
      "age" -> 30,
      "bio" -> ("Lorem ipsum dolor sit amet, consectetur adipiscing elit. " * 20),
      "metadata" -> ujson.Obj("created" -> "2024-01-01", "updated" -> "2024-01-01")
    )
    val obj1 = PrismObject("obj-1", 1, baseData)
    val updatedData = baseData.copy()
    updatedData("age") = ujson.Num(31)
    val obj2 = PrismObject("obj-1", 2, updatedData)
    val ref = ObjectReference(id = "obj-1", version = 0)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        // Manually set client's known version to 1
        state <- router.objectManager.getClientState("client-1")
        _ = state.updateVersion("obj-1", 1)
        hydrated <- router.hydrateReference("client-1", ref)
      } yield hydrated
    }

    assertEquals(result.id, "obj-1")
    assertEquals(result.version, 2)
    assertEquals(result.data, None) // Should send delta, not full data
    assert(result.delta.isDefined)
    assertEquals(result.delta.get.fromVersion, 1)
    assertEquals(result.delta.get.toVersion, 2)
    assertEquals(result.cached, false)
  }

  test("hydrateReference - client has old version, returns full if delta not efficient") {
    val (router, manager, storage) = createRouter()
    // Create objects where delta would be large (complete replacement)
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("data" -> ("old data " * 100)))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("data" -> ("completely new data " * 100)))
    val ref = ObjectReference(id = "obj-1", version = 0)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        state <- manager.getClientState("client-1")
        _ = state.updateVersion("obj-1", 1)
        _ <- storage.save(obj2)
        hydrated <- router.hydrateReference("client-1", ref)
      } yield hydrated
    }

    assertEquals(result.id, "obj-1")
    assertEquals(result.version, 2)
    // Should send full object because delta is not efficient
    assert(result.data.isDefined)
    assertEquals(result.delta, None)
    assertEquals(result.cached, false)
  }

  test("hydrateReference - applies filter from subscription") {
    val (router, manager, storage) = createRouter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30, "email" -> "alice@example.com"))
    val ref = ObjectReference(id = "obj-1", version = 0)
    val params = Some(ujson.Obj("fields" -> ujson.Arr("name", "age")))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        // Subscribe with filter
        _ <- manager.subscribe("client-1", "obj-1", "fields", params, ongoing = true)
        hydrated <- router.hydrateReference("client-1", ref)
      } yield hydrated
    }

    assert(result.data.isDefined)
    val data = result.data.get.obj
    assert(data.contains("name"))
    assert(data.contains("age"))
    assert(!data.contains("email"))
  }

  test("hydrateReference - non-existent object returns empty") {
    val (router, _, _) = createRouter()
    val ref = ObjectReference(id = "non-existent", version = 0)

    val result = runIO {
      router.hydrateReference("client-1", ref)
    }

    assertEquals(result.id, "non-existent")
    assertEquals(result.version, 0)
    assertEquals(result.cached, false)
  }

  // ========== Hydrate Multiple References ==========

  test("hydrateReferences - hydrates multiple objects") {
    val (router, _, storage) = createRouter()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val obj2 = PrismObject("obj-2", 1, ujson.Obj("name" -> "Bob"))
    val refs = List(
      ObjectReference(id = "obj-1", version = 0),
      ObjectReference(id = "obj-2", version = 0)
    )

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        hydrated <- router.hydrateReferences("client-1", refs)
      } yield hydrated
    }

    assertEquals(result.length, 2)
    assertEquals(result(0).id, "obj-1")
    assertEquals(result(1).id, "obj-2")
    assert(result(0).data.isDefined)
    assert(result(1).data.isDefined)
  }

  // ========== Process Request ==========

  test("processRequest - hydrates primary references") {
    val (router, _, storage) = createRouter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val refs = List(ObjectReference(id = "obj-1", version = 0))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        hydrated <- router.processRequest("client-1", refs)
      } yield hydrated
    }

    assertEquals(result.length, 1)
    assertEquals(result.head.id, "obj-1")
  }

  test("processRequest - respects max hydration depth") {
    val (router, _, storage) = createRouter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val refs = List(ObjectReference(id = "obj-1", version = 0))
    val options = RequestOptions(maxHydrationDepth = 0)

    val result = runIO {
      for {
        _ <- storage.save(obj)
        hydrated <- router.processRequest("client-1", refs, options)
      } yield hydrated
    }

    // Should hydrate primary refs even at depth 0
    assertEquals(result.length, 1)
  }

  test("processRequest - auto-hydrates nested references") {
    val (router, _, storage) = createRouter()
    // obj-1 contains a reference to obj-2
    val obj1 = PrismObject("obj-1", 1, ujson.Obj(
      "name" -> "Alice",
      "friend" -> ujson.Obj("id" -> "obj-2")
    ))
    val obj2 = PrismObject("obj-2", 1, ujson.Obj("name" -> "Bob"))
    val refs = List(ObjectReference(id = "obj-1", version = 0))
    val options = RequestOptions(hydrateRefs = true)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        hydrated <- router.processRequest("client-1", refs, options)
      } yield hydrated
    }

    // Should have hydrated both obj-1 and obj-2
    assert(result.length >= 2)
    assert(result.exists(_.id == "obj-1"))
    assert(result.exists(_.id == "obj-2"))
  }

  test("processRequest - no auto-hydration if disabled") {
    val (router, _, storage) = createRouter()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj(
      "name" -> "Alice",
      "friend" -> ujson.Obj("id" -> "obj-2")
    ))
    val obj2 = PrismObject("obj-2", 1, ujson.Obj("name" -> "Bob"))
    val refs = List(ObjectReference(id = "obj-1", version = 0))
    val options = RequestOptions(hydrateRefs = false)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        hydrated <- router.processRequest("client-1", refs, options)
      } yield hydrated
    }

    // Should only have hydrated obj-1
    assertEquals(result.length, 1)
    assertEquals(result.head.id, "obj-1")
  }

  test("processRequest - subscribes to refs if requested") {
    val (router, manager, storage) = createRouter()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj(
      "name" -> "Alice",
      "friend" -> ujson.Obj("id" -> "obj-2")
    ))
    val obj2 = PrismObject("obj-2", 1, ujson.Obj("name" -> "Bob"))
    val refs = List(ObjectReference(id = "obj-1", version = 0))
    val options = RequestOptions(hydrateRefs = true, subscribeToRefs = true)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        _ <- router.processRequest("client-1", refs, options)
        subs <- manager.getActiveSubscriptions("client-1")
      } yield subs.map(_.objectId).toList.sorted
    }

    // Should have subscribed to obj-2 (nested ref)
    assert(result.contains("obj-2"))
  }

  // ========== Temporary Subscriptions ==========

  test("createTemporarySubscriptions - creates temporary subs") {
    val (router, manager, storage) = createRouter()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj())
    val obj2 = PrismObject("obj-2", 1, ujson.Obj())
    val refs = List(
      ObjectReference(id = "obj-1", version = 0),
      ObjectReference(id = "obj-2", version = 0)
    )

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        _ <- router.createTemporarySubscriptions("client-1", refs)
        state <- manager.getClientState("client-1")
        sub1 = state.getSubscription("obj-1")
        sub2 = state.getSubscription("obj-2")
      } yield (sub1.map(_.temporary), sub2.map(_.temporary))
    }

    assertEquals(result, (Some(true), Some(true)))
  }

  test("createTemporarySubscriptions - uses filter from ref") {
    val (router, manager, storage) = createRouter()
    val obj = PrismObject("obj-1", 1, ujson.Obj())
    val refs = List(ObjectReference(id = "obj-1", version = 0, filterType = Some("fields")))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        _ <- router.createTemporarySubscriptions("client-1", refs)
        state <- manager.getClientState("client-1")
        sub = state.getSubscription("obj-1")
      } yield sub.map(_.filterType)
    }

    assertEquals(result, Some("fields"))
  }

  // ========== Extract Nested References ==========

  test("scanForReferences - finds nested object references") {
    val (router, _, _) = createRouter()

    val data = ujson.Obj(
      "user" -> ujson.Obj("id" -> "user-1"),
      "items" -> ujson.Arr(
        ujson.Obj("id" -> "item-1"),
        ujson.Obj("id" -> "item-2")
      )
    )

    // Access private method via reflection for testing
    val method = router.getClass.getDeclaredMethod("scanForReferences", classOf[ujson.Value], classOf[String])
    method.setAccessible(true)
    val refs = method.invoke(router, data, "default").asInstanceOf[List[ObjectReference]]

    assertEquals(refs.length, 3)
    assert(refs.exists(_.id == "user-1"))
    assert(refs.exists(_.id == "item-1"))
    assert(refs.exists(_.id == "item-2"))
  }

  test("scanForReferences - ignores objects without id") {
    val (router, _, _) = createRouter()

    val data = ujson.Obj(
      "name" -> "Alice",
      "metadata" -> ujson.Obj("type" -> "user")
    )

    val method = router.getClass.getDeclaredMethod("scanForReferences", classOf[ujson.Value], classOf[String])
    method.setAccessible(true)
    val refs = method.invoke(router, data, "default").asInstanceOf[List[ObjectReference]]

    assertEquals(refs.length, 0)
  }

  test("scanForReferences - handles deeply nested structures") {
    val (router, _, _) = createRouter()

    val data = ujson.Obj(
      "level1" -> ujson.Obj(
        "level2" -> ujson.Obj(
          "level3" -> ujson.Obj("id" -> "deep-obj")
        )
      )
    )

    val method = router.getClass.getDeclaredMethod("scanForReferences", classOf[ujson.Value], classOf[String])
    method.setAccessible(true)
    val refs = method.invoke(router, data, "default").asInstanceOf[List[ObjectReference]]

    assertEquals(refs.length, 1)
    assertEquals(refs.head.id, "deep-obj")
  }
}
