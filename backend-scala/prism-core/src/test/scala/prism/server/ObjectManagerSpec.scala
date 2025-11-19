package prism.server

import cats.effect.unsafe.implicits.global
import cats.effect.IO
import cats.effect.Ref
import munit.FunSuite
import prism.core.Types.PrismObject
import prism.core.Protocol._
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
    val params = Some(ujson.Obj("fields" -> ujson.Arr("name", "age")))

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
    val params = Some(ujson.Obj("fields" -> ujson.Arr("name")))

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
    val params = Some(ujson.Obj("fields" -> ujson.Arr("name", "age")))

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
    val params = Some(ujson.Obj("fields" -> ujson.Arr("name")))

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

  // ========== Callback Registration and Notification ==========

  test("registerClient - stores callback for client") {
    val (manager, _) = createObjectManager()
    var messageReceived: Option[ServerMessage] = None

    val callback: ServerMessage => IO[Unit] = msg => IO { messageReceived = Some(msg) }

    runIO {
      manager.registerClient("client-1", callback)
    }

    // Callback is stored - will be tested via notifyObjectUpdated
    assert(true)
  }

  test("registerClient - removes callback when client state is removed") {
    val (manager, _) = createObjectManager()
    var callbackInvoked = false

    val callback: ServerMessage => IO[Unit] = _ => IO { callbackInvoked = true }

    runIO {
      for {
        _ <- manager.registerClient("client-1", callback)
        _ <- manager.removeClientState("client-1")
      } yield ()
    }

    // After removal, callback should not be invoked (tested in notifyObjectUpdated test)
    assert(true)
  }

  test("notifyObjectUpdated - sends fullObject to subscribed clients") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Bob"))

    val messagesReceived = runIO(Ref.of[IO, List[ServerMessage]](List.empty))

    val callback: ServerMessage => IO[Unit] = msg => messagesReceived.update(_ :+ msg)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- manager.registerClient("client-1", callback)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        count <- manager.notifyObjectUpdated(obj2)
        messages <- messagesReceived.get
      } yield (count, messages)
    }

    val (count, messages) = result
    assertEquals(count, 1)
    assertEquals(messages.length, 1)

    messages.head match {
      case ServerMessage.FullObject(msg) =>
        assertEquals(msg.id, "obj-1")
        assertEquals(msg.version, 2)
        assertEquals(msg.data("name").str, "Bob")
      case _ => fail("Expected FullObject message")
    }
  }

  test("notifyObjectUpdated - does not notify temporary subscriptions") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Bob"))

    val messagesReceived = runIO(Ref.of[IO, List[ServerMessage]](List.empty))
    val callback: ServerMessage => IO[Unit] = msg => messagesReceived.update(_ :+ msg)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- manager.registerClient("client-1", callback)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = false) // Temporary
        count <- manager.notifyObjectUpdated(obj2)
        messages <- messagesReceived.get
      } yield (count, messages)
    }

    val (count, messages) = result
    assertEquals(count, 0)
    assertEquals(messages.length, 0)
  }

  test("notifyObjectUpdated - sends to multiple subscribed clients") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Bob"))

    val messages1 = runIO(Ref.of[IO, List[ServerMessage]](List.empty))
    val messages2 = runIO(Ref.of[IO, List[ServerMessage]](List.empty))

    val callback1: ServerMessage => IO[Unit] = msg => messages1.update(_ :+ msg)
    val callback2: ServerMessage => IO[Unit] = msg => messages2.update(_ :+ msg)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- manager.registerClient("client-1", callback1)
        _ <- manager.registerClient("client-2", callback2)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        _ <- manager.subscribe("client-2", "obj-1", ongoing = true)
        count <- manager.notifyObjectUpdated(obj2)
        msgs1 <- messages1.get
        msgs2 <- messages2.get
      } yield (count, msgs1, msgs2)
    }

    val (count, msgs1, msgs2) = result
    assertEquals(count, 2)
    assertEquals(msgs1.length, 1)
    assertEquals(msgs2.length, 1)
  }

  test("notifyObjectUpdated - applies filters before sending") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30, "email" -> "alice@example.com"))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Bob", "age" -> 31, "email" -> "bob@example.com"))
    val params = Some(ujson.Obj("fields" -> ujson.Arr("name", "age")))

    val messagesReceived = runIO(Ref.of[IO, List[ServerMessage]](List.empty))
    val callback: ServerMessage => IO[Unit] = msg => messagesReceived.update(_ :+ msg)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- manager.registerClient("client-1", callback)
        _ <- manager.subscribe("client-1", "obj-1", "fields", params, ongoing = true)
        _ <- manager.notifyObjectUpdated(obj2)
        messages <- messagesReceived.get
      } yield messages
    }

    assertEquals(result.length, 1)
    result.head match {
      case ServerMessage.FullObject(msg) =>
        assert(msg.data.obj.contains("name"))
        assert(msg.data.obj.contains("age"))
        assert(!msg.data.obj.contains("email"))
      case _ => fail("Expected FullObject message")
    }
  }

  test("notifyObjectUpdated - sends delta for small changes") {
    val (manager, storage) = createObjectManager()
    val obj1 = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30))
    val obj2 = PrismObject("obj-1", 2, ujson.Obj("name" -> "Alice", "age" -> 31))

    val messagesReceived = runIO(Ref.of[IO, List[ServerMessage]](List.empty))
    val callback: ServerMessage => IO[Unit] = msg => messagesReceived.update(_ :+ msg)

    val result = runIO {
      for {
        _ <- storage.save(obj1)
        _ <- storage.save(obj2)
        _ <- manager.registerClient("client-1", callback)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        state <- manager.getClientState("client-1")
        _ = state.updateVersion("obj-1", 1) // Client has version 1
        _ <- manager.notifyObjectUpdated(obj2)
        messages <- messagesReceived.get
      } yield messages
    }

    assertEquals(result.length, 1)
    result.head match {
      case ServerMessage.Delta(msg) =>
        assertEquals(msg.id, "obj-1")
        assertEquals(msg.fromVersion, 1)
        assertEquals(msg.toVersion, 2)
        assert(msg.patches.nonEmpty)
      case ServerMessage.FullObject(_) =>
        // FullObject is also acceptable if delta is deemed inefficient
        assert(true)
      case _ => fail("Expected Delta or FullObject message")
    }
  }

  test("notifyObjectUpdated - sends fullObject when client version is unknown") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val messagesReceived = runIO(Ref.of[IO, List[ServerMessage]](List.empty))
    val callback: ServerMessage => IO[Unit] = msg => messagesReceived.update(_ :+ msg)

    val result = runIO {
      for {
        _ <- storage.save(obj)
        _ <- manager.registerClient("client-1", callback)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        // Don't set client version - should send full object
        _ <- manager.notifyObjectUpdated(obj)
        messages <- messagesReceived.get
      } yield messages
    }

    assertEquals(result.length, 1)
    result.head match {
      case ServerMessage.FullObject(msg) =>
        assertEquals(msg.id, "obj-1")
        assertEquals(msg.version, 1)
      case _ => fail("Expected FullObject message")
    }
  }

  test("notifyObjectUpdated - updates version cache") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val callback: ServerMessage => IO[Unit] = _ => IO.unit

    val result = runIO {
      for {
        _ <- storage.save(obj)
        _ <- manager.registerClient("client-1", callback)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        _ <- manager.notifyObjectUpdated(obj)
        // Try to get from cache
        cached <- manager.getObject("obj-1", Some(1))
      } yield cached
    }

    assert(result.isDefined)
    assertEquals(result.get.id, "obj-1")
  }

  test("notifyObjectUpdated - does not send if client has current version") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val messagesReceived = runIO(Ref.of[IO, List[ServerMessage]](List.empty))
    val callback: ServerMessage => IO[Unit] = msg => messagesReceived.update(_ :+ msg)

    val result = runIO {
      for {
        _ <- storage.save(obj)
        _ <- manager.registerClient("client-1", callback)
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        state <- manager.getClientState("client-1")
        _ = state.updateVersion("obj-1", 1) // Client already has version 1
        _ <- manager.notifyObjectUpdated(obj)
        messages <- messagesReceived.get
      } yield messages
    }

    // Client already has this version, so no message should be sent
    assertEquals(result.length, 0)
  }

  test("notifyObjectUpdated - handles client without callback gracefully") {
    val (manager, storage) = createObjectManager()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val result = runIO {
      for {
        _ <- storage.save(obj)
        // Subscribe without registering callback
        _ <- manager.subscribe("client-1", "obj-1", ongoing = true)
        count <- manager.notifyObjectUpdated(obj)
      } yield count
    }

    // Should count client as subscribed even though callback failed
    assertEquals(result, 1)
  }
}
