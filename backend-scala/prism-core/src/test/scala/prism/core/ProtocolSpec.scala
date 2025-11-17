package prism.core

import munit.FunSuite
import prism.core.Protocol._
import prism.core.Types.HydratedReference
import upickle.default._

class ProtocolSpec extends FunSuite {

  // ========== Client Messages Tests ==========

  test("SubscribeMessage - JSON round-trip") {
    val msg = ClientMessage.Subscribe(
      SubscribeMessage(
        objectId = "obj-1",
        filterType = Some("fields"),
        filterParams = Some(Map("fields" -> ujson.Arr("name", "id"))),
        temporary = true
      )
    )

    val json = write(msg)
    val decoded = read[ClientMessage](json)

    decoded match {
      case ClientMessage.Subscribe(sub) =>
        assertEquals(sub.objectId, "obj-1")
        assertEquals(sub.filterType, Some("fields"))
        assertEquals(sub.temporary, true)
      case _ => fail("Expected Subscribe message")
    }
  }

  test("SubscribeMessage - with defaults") {
    val msg = ClientMessage.Subscribe(
      SubscribeMessage(objectId = "obj-1")
    )

    val json = write(msg)
    assert(json.contains("\"type\":\"subscribe\""))
    assert(json.contains("\"objectId\":\"obj-1\""))

    val decoded = read[ClientMessage](json)
    decoded match {
      case ClientMessage.Subscribe(sub) =>
        assertEquals(sub.filterType, None)
        assertEquals(sub.filterParams, None)
        assertEquals(sub.temporary, false)
      case _ => fail("Expected Subscribe message")
    }
  }

  test("UnsubscribeMessage - JSON round-trip") {
    val msg = ClientMessage.Unsubscribe(
      UnsubscribeMessage(objectId = "obj-1")
    )

    val json = write(msg)
    val decoded = read[ClientMessage](json)

    decoded match {
      case ClientMessage.Unsubscribe(unsub) =>
        assertEquals(unsub.objectId, "obj-1")
      case _ => fail("Expected Unsubscribe message")
    }
  }

  test("SyncMessage - JSON round-trip") {
    val states = List(
      SyncStateItem("obj-1", 5, "default"),
      SyncStateItem("obj-2", 10, "fields")
    )
    val msg = ClientMessage.Sync(SyncMessage(states))

    val json = write(msg)
    val decoded = read[ClientMessage](json)

    decoded match {
      case ClientMessage.Sync(sync) =>
        assertEquals(sync.states.length, 2)
        assertEquals(sync.states(0).id, "obj-1")
        assertEquals(sync.states(0).version, 5)
        assertEquals(sync.states(1).filterType, "fields")
      case _ => fail("Expected Sync message")
    }
  }

  test("RequestMessage - JSON round-trip") {
    val payload = ujson.Obj("roomId" -> "room-1", "content" -> "Hello")
    val options = ujson.Obj("hydrateRefs" -> true)
    val msg = ClientMessage.Request(
      RequestMessage(
        requestId = "req-123",
        requestType = "sendMessage",
        payload = payload,
        options = Some(options)
      )
    )

    val json = write(msg)
    val decoded = read[ClientMessage](json)

    decoded match {
      case ClientMessage.Request(req) =>
        assertEquals(req.requestId, "req-123")
        assertEquals(req.requestType, "sendMessage")
        assertEquals(req.payload("roomId").str, "room-1")
        assert(req.options.isDefined)
      case _ => fail("Expected Request message")
    }
  }

  test("UpdateFilterMessage - JSON round-trip") {
    val msg = ClientMessage.UpdateFilter(
      UpdateFilterMessage(
        objectId = "obj-1",
        filterType = "exclude",
        filterParams = Some(Map("fields" -> ujson.Arr("password")))
      )
    )

    val json = write(msg)
    val decoded = read[ClientMessage](json)

    decoded match {
      case ClientMessage.UpdateFilter(upd) =>
        assertEquals(upd.objectId, "obj-1")
        assertEquals(upd.filterType, "exclude")
        assert(upd.filterParams.isDefined)
      case _ => fail("Expected UpdateFilter message")
    }
  }

  test("ClientMessage - unknown type throws exception") {
    val json = """{"type":"unknown","data":"test"}"""
    intercept[IllegalArgumentException] {
      read[ClientMessage](json)
    }
  }

  // ========== Server Messages Tests ==========

  test("FullObjectMessage - JSON round-trip") {
    val data = ujson.Obj("name" -> "test", "count" -> 42)
    val msg = ServerMessage.FullObject(
      FullObjectMessage(
        id = "obj-1",
        version = 5,
        data = data,
        filtered = true,
        filterType = Some("fields")
      )
    )

    val json = write(msg)
    val decoded = read[ServerMessage](json)

    decoded match {
      case ServerMessage.FullObject(full) =>
        assertEquals(full.id, "obj-1")
        assertEquals(full.version, 5)
        assertEquals(full.data("name").str, "test")
        assertEquals(full.filtered, true)
        assertEquals(full.filterType, Some("fields"))
      case _ => fail("Expected FullObject message")
    }
  }

  test("FullObjectMessage - with defaults") {
    val msg = ServerMessage.FullObject(
      FullObjectMessage(
        id = "obj-1",
        version = 1,
        data = ujson.Obj()
      )
    )

    val json = write(msg)
    val decoded = read[ServerMessage](json)

    decoded match {
      case ServerMessage.FullObject(full) =>
        assertEquals(full.filtered, false)
        assertEquals(full.filterType, None)
      case _ => fail("Expected FullObject message")
    }
  }

  test("DeltaMessage - JSON round-trip") {
    val patches = List(
      ujson.Obj("op" -> "replace", "path" -> "/name", "value" -> "new-name")
    )
    val msg = ServerMessage.Delta(
      DeltaMessage(
        id = "obj-1",
        fromVersion = 5,
        toVersion = 6,
        patches = patches,
        filterType = Some("default")
      )
    )

    val json = write(msg)
    val decoded = read[ServerMessage](json)

    decoded match {
      case ServerMessage.Delta(delta) =>
        assertEquals(delta.id, "obj-1")
        assertEquals(delta.fromVersion, 5)
        assertEquals(delta.toVersion, 6)
        assertEquals(delta.patches.length, 1)
        assertEquals(delta.patches(0)("op").str, "replace")
      case _ => fail("Expected Delta message")
    }
  }

  test("ResponseMessage - success with data and hydrated refs") {
    val data = ujson.Obj("messageId" -> "msg-1")
    val hydrated = List(
      HydratedReference("user-1", 3, data = Some(ujson.Obj("name" -> "Alice"))),
      HydratedReference("room-1", 10, cached = true)
    )
    val msg = ServerMessage.Response(
      ResponseMessage(
        requestId = "req-123",
        success = true,
        data = Some(data),
        hydrated = hydrated
      )
    )

    val json = write(msg)
    val decoded = read[ServerMessage](json)

    decoded match {
      case ServerMessage.Response(resp) =>
        assertEquals(resp.requestId, "req-123")
        assertEquals(resp.success, true)
        assert(resp.data.isDefined)
        assertEquals(resp.hydrated.length, 2)
        assertEquals(resp.hydrated(0).id, "user-1")
        assertEquals(resp.hydrated(1).cached, true)
        assertEquals(resp.error, None)
      case _ => fail("Expected Response message")
    }
  }

  test("ResponseMessage - error response") {
    val msg = ServerMessage.Response(
      ResponseMessage(
        requestId = "req-123",
        success = false,
        error = Some("Object not found")
      )
    )

    val json = write(msg)
    val decoded = read[ServerMessage](json)

    decoded match {
      case ServerMessage.Response(resp) =>
        assertEquals(resp.success, false)
        assertEquals(resp.error, Some("Object not found"))
        assertEquals(resp.data, None)
        assertEquals(resp.hydrated, List.empty)
      case _ => fail("Expected Response message")
    }
  }

  test("ErrorMessage - JSON round-trip") {
    val msg = ServerMessage.Error(
      ErrorMessage(
        code = "OBJECT_NOT_FOUND",
        message = "Object obj-999 not found",
        objectId = Some("obj-999")
      )
    )

    val json = write(msg)
    val decoded = read[ServerMessage](json)

    decoded match {
      case ServerMessage.Error(err) =>
        assertEquals(err.code, "OBJECT_NOT_FOUND")
        assertEquals(err.message, "Object obj-999 not found")
        assertEquals(err.objectId, Some("obj-999"))
        assertEquals(err.requestId, None)
      case _ => fail("Expected Error message")
    }
  }

  test("ErrorMessage - with request ID") {
    val msg = ServerMessage.Error(
      ErrorMessage(
        code = "INVALID_REQUEST",
        message = "Invalid payload",
        requestId = Some("req-123")
      )
    )

    val json = write(msg)
    val decoded = read[ServerMessage](json)

    decoded match {
      case ServerMessage.Error(err) =>
        assertEquals(err.code, "INVALID_REQUEST")
        assertEquals(err.requestId, Some("req-123"))
        assertEquals(err.objectId, None)
      case _ => fail("Expected Error message")
    }
  }

  test("ServerMessage - unknown type throws exception") {
    val json = """{"type":"unknown","data":"test"}"""
    intercept[IllegalArgumentException] {
      read[ServerMessage](json)
    }
  }

  // ========== Protocol Compatibility Tests ==========

  test("Protocol - message type discriminator is present") {
    val subMsg = ClientMessage.Subscribe(SubscribeMessage("obj-1"))
    val json = write(subMsg)
    assert(json.contains("\"type\":\"subscribe\""))

    val fullMsg = ServerMessage.FullObject(
      FullObjectMessage("obj-1", 1, ujson.Obj())
    )
    val json2 = write(fullMsg)
    assert(json2.contains("\"type\":\"fullObject\""))
  }

  test("ErrorCodes - constants are defined") {
    assertEquals(ErrorCodes.OBJECT_NOT_FOUND, "OBJECT_NOT_FOUND")
    assertEquals(ErrorCodes.INVALID_REQUEST, "INVALID_REQUEST")
    assertEquals(ErrorCodes.UNAUTHORIZED, "UNAUTHORIZED")
    assertEquals(ErrorCodes.INTERNAL_ERROR, "INTERNAL_ERROR")
    assertEquals(ErrorCodes.INVALID_FILTER, "INVALID_FILTER")
    assertEquals(ErrorCodes.INVALID_VERSION, "INVALID_VERSION")
  }
}
