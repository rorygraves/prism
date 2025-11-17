package prism.core

import munit.FunSuite
import prism.core.Types._
import upickle.default._

class TypesSpec extends FunSuite {

  test("PrismObject - create valid object") {
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "test"))
    assertEquals(obj.id, "obj-1")
    assertEquals(obj.version, 1)
    assertEquals(obj.data("name").str, "test")
  }

  test("PrismObject - reject negative version") {
    intercept[IllegalArgumentException] {
      PrismObject("obj-1", -1, ujson.Obj())
    }
  }

  test("PrismObject - reject empty ID") {
    intercept[IllegalArgumentException] {
      PrismObject("", 0, ujson.Obj())
    }
  }

  test("PrismObject - version 0 is valid") {
    val obj = PrismObject("obj-1", 0, ujson.Obj())
    assertEquals(obj.version, 0)
  }

  test("PrismObject - JSON round-trip serialization") {
    val obj = PrismObject("obj-1", 5, ujson.Obj("name" -> "test", "count" -> 42))
    val json = write(obj)
    val decoded = read[PrismObject](json)

    assertEquals(decoded.id, obj.id)
    assertEquals(decoded.version, obj.version)
    assertEquals(decoded.data, obj.data)
  }

  test("Delta - create valid delta") {
    val patches = List(
      ujson.Obj("op" -> "replace", "path" -> "/name", "value" -> "new-name")
    )
    val delta = Delta("obj-1", 1, 2, patches)

    assertEquals(delta.objectId, "obj-1")
    assertEquals(delta.fromVersion, 1)
    assertEquals(delta.toVersion, 2)
    assertEquals(delta.patches.length, 1)
  }

  test("Delta - reject invalid version ordering") {
    intercept[IllegalArgumentException] {
      Delta("obj-1", 2, 1, List.empty)
    }
  }

  test("Delta - reject same from/to version") {
    intercept[IllegalArgumentException] {
      Delta("obj-1", 1, 1, List.empty)
    }
  }

  test("Delta - reject negative versions") {
    intercept[IllegalArgumentException] {
      Delta("obj-1", -1, 2, List.empty)
    }
    intercept[IllegalArgumentException] {
      Delta("obj-1", 1, -1, List.empty)
    }
  }

  test("Delta - JSON round-trip serialization") {
    val patches = List(
      ujson.Obj("op" -> "add", "path" -> "/tags/-", "value" -> "new-tag")
    )
    val delta = Delta("obj-1", 3, 4, patches)
    val json = write(delta)
    val decoded = read[Delta](json)

    assertEquals(decoded.objectId, delta.objectId)
    assertEquals(decoded.fromVersion, delta.fromVersion)
    assertEquals(decoded.toVersion, delta.toVersion)
    assertEquals(decoded.patches, delta.patches)
  }

  test("ObjectReference - create with defaults") {
    val ref = ObjectReference("obj-1", 5)
    assertEquals(ref.id, "obj-1")
    assertEquals(ref.version, 5)
    assertEquals(ref.filterType, None)
    assertEquals(ref.subscribe, false)
  }

  test("ObjectReference - create with filter and subscribe") {
    val ref = ObjectReference("obj-1", 5, Some("fields"), subscribe = true)
    assertEquals(ref.filterType, Some("fields"))
    assertEquals(ref.subscribe, true)
  }

  test("ObjectReference - reject negative version") {
    intercept[IllegalArgumentException] {
      ObjectReference("obj-1", -1)
    }
  }

  test("ObjectReference - JSON round-trip serialization") {
    val ref = ObjectReference("obj-1", 10, Some("exclude"), subscribe = true)
    val json = write(ref)
    val decoded = read[ObjectReference](json)

    assertEquals(decoded.id, ref.id)
    assertEquals(decoded.version, ref.version)
    assertEquals(decoded.filterType, ref.filterType)
    assertEquals(decoded.subscribe, ref.subscribe)
  }

  test("HydratedReference - with full data") {
    val data = ujson.Obj("name" -> "test")
    val hydrated = HydratedReference("obj-1", 5, data = Some(data))

    assertEquals(hydrated.id, "obj-1")
    assertEquals(hydrated.version, 5)
    assert(hydrated.data.isDefined)
    assertEquals(hydrated.delta, None)
    assertEquals(hydrated.cached, false)
  }

  test("HydratedReference - with delta") {
    val delta = Delta("obj-1", 4, 5, List(ujson.Obj("op" -> "replace")))
    val hydrated = HydratedReference("obj-1", 5, delta = Some(delta))

    assertEquals(hydrated.data, None)
    assert(hydrated.delta.isDefined)
    assertEquals(hydrated.cached, false)
  }

  test("HydratedReference - cached") {
    val hydrated = HydratedReference("obj-1", 5, cached = true)

    assertEquals(hydrated.data, None)
    assertEquals(hydrated.delta, None)
    assertEquals(hydrated.cached, true)
  }

  test("RequestOptions - default values") {
    val opts = RequestOptions()
    assertEquals(opts.hydrateRefs, true)
    assertEquals(opts.subscribeToRefs, false)
    assertEquals(opts.filterType, None)
    assertEquals(opts.maxHydrationDepth, 5)
  }

  test("RequestOptions - custom values") {
    val opts = RequestOptions(
      hydrateRefs = false,
      subscribeToRefs = true,
      filterType = Some("fields"),
      maxHydrationDepth = 3
    )

    assertEquals(opts.hydrateRefs, false)
    assertEquals(opts.subscribeToRefs, true)
    assertEquals(opts.filterType, Some("fields"))
    assertEquals(opts.maxHydrationDepth, 3)
  }

  test("RequestOptions - reject negative max depth") {
    intercept[IllegalArgumentException] {
      RequestOptions(maxHydrationDepth = -1)
    }
  }

  test("Subscription - create with defaults") {
    val sub = Subscription("obj-1")
    assertEquals(sub.objectId, "obj-1")
    assertEquals(sub.filterType, "default")
    assertEquals(sub.filterParams, None)
    assertEquals(sub.currentVersion, 0)
    assertEquals(sub.temporary, false)
  }

  test("Subscription - create with custom values") {
    val params = Some(Map("fields" -> ujson.Arr("name", "id")))
    val sub = Subscription(
      objectId = "obj-1",
      filterType = "fields",
      filterParams = params,
      currentVersion = 5,
      temporary = true
    )

    assertEquals(sub.objectId, "obj-1")
    assertEquals(sub.filterType, "fields")
    assertEquals(sub.filterParams, params)
    assertEquals(sub.currentVersion, 5)
    assertEquals(sub.temporary, true)
  }

  test("Subscription - reject negative current version") {
    intercept[IllegalArgumentException] {
      Subscription("obj-1", currentVersion = -1)
    }
  }

  test("ClientState - starts empty") {
    val state = ClientState.empty
    assertEquals(state.subscriptions.size, 0)
    assertEquals(state.objectVersions.size, 0)
  }

  test("ClientState - get/update version") {
    val state = ClientState.empty

    assertEquals(state.getVersion("obj-1"), None)

    state.updateVersion("obj-1", 5)
    assertEquals(state.getVersion("obj-1"), Some(5))

    state.updateVersion("obj-1", 10)
    assertEquals(state.getVersion("obj-1"), Some(10))
  }

  test("ClientState - add/get/remove subscription") {
    val state = ClientState.empty
    val sub = Subscription("obj-1", filterType = "fields")

    assertEquals(state.hasSubscription("obj-1"), false)
    assertEquals(state.getSubscription("obj-1"), None)

    state.addSubscription(sub)
    assertEquals(state.hasSubscription("obj-1"), true)
    assertEquals(state.getSubscription("obj-1"), Some(sub))

    state.removeSubscription("obj-1")
    assertEquals(state.hasSubscription("obj-1"), false)
  }

  test("ClientState - get active subscriptions") {
    val state = ClientState.empty
    state.addSubscription(Subscription("obj-1", temporary = false))
    state.addSubscription(Subscription("obj-2", temporary = true))
    state.addSubscription(Subscription("obj-3", temporary = false))

    val active = state.getActiveSubscriptions.toList
    assertEquals(active.length, 2)
    assert(active.exists(_.objectId == "obj-1"))
    assert(active.exists(_.objectId == "obj-3"))
    assert(!active.exists(_.objectId == "obj-2"))
  }

  test("ClientState - clear temporary subscriptions") {
    val state = ClientState.empty
    state.addSubscription(Subscription("obj-1", temporary = false))
    state.addSubscription(Subscription("obj-2", temporary = true))
    state.addSubscription(Subscription("obj-3", temporary = true))

    assertEquals(state.subscriptions.size, 3)

    state.clearTemporarySubscriptions()
    assertEquals(state.subscriptions.size, 1)
    assertEquals(state.getSubscription("obj-1").isDefined, true)
    assertEquals(state.getSubscription("obj-2").isDefined, false)
  }

  test("ClientState - copy creates independent state") {
    val state1 = ClientState.empty
    state1.addSubscription(Subscription("obj-1"))
    state1.updateVersion("obj-1", 5)

    val state2 = state1.copy()

    // Modify state1
    state1.addSubscription(Subscription("obj-2"))
    state1.updateVersion("obj-1", 10)

    // state2 should be unchanged
    assertEquals(state2.subscriptions.size, 1)
    assertEquals(state2.getVersion("obj-1"), Some(5))
    assertEquals(state2.hasSubscription("obj-2"), false)
  }

  test("ClientState - subscription updates") {
    val state = ClientState.empty
    state.addSubscription(Subscription("obj-1", currentVersion = 1))

    // Update subscription (replace)
    state.addSubscription(Subscription("obj-1", currentVersion = 5))

    val sub = state.getSubscription("obj-1")
    assert(sub.isDefined)
    assertEquals(sub.get.currentVersion, 5)
  }
}
