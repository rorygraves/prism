package prism.core

import munit.FunSuite
import prism.core.Types._
import prism.core.Protocol._
import prism.core.PickleConfig._

class DeltaSerializationTest extends FunSuite {

  test("Delta serialization - check JSON format") {
    val delta = Delta(
      objectId = "test-1",
      fromVersion = 1,
      toVersion = 2,
      patches = List(
        ujson.Obj("op" -> "add", "path" -> "/name", "value" -> "Bob")
      )
    )

    val json = writeJs(delta)
    println(s"Delta JSON: ${json.render()}")

    // Check structure
    assert(json.obj.contains("object_id"))
    assert(json.obj.contains("from_version"))
    assert(json.obj.contains("to_version"))
    assert(json.obj.contains("patches"))

    // Check patches is an array
    val patches = json.obj("patches")
    assert(patches.isInstanceOf[ujson.Arr], s"patches should be an array, but got: ${patches}")
  }

  test("DeltaMessage serialization - check JSON format") {
    val deltaMsg = DeltaMessage(
      id = "test-1",
      fromVersion = 1,
      toVersion = 2,
      patches = List(
        ujson.Obj("op" -> "add", "path" -> "/name", "value" -> "Bob")
      ),
      filterType = None
    )

    val json = writeJs(deltaMsg)
    println(s"DeltaMessage JSON: ${json.render()}")

    // Check structure
    assert(json.obj.contains("id"))
    assert(json.obj.contains("from_version"))
    assert(json.obj.contains("to_version"))
    assert(json.obj.contains("patches"))

    // Check patches is an array
    val patches = json.obj("patches")
    assert(patches.isInstanceOf[ujson.Arr], s"patches should be an array, but got: ${patches}")
  }

  test("ServerMessage.Delta serialization - check JSON format") {
    val deltaMsg = DeltaMessage(
      id = "test-1",
      fromVersion = 1,
      toVersion = 2,
      patches = List(
        ujson.Obj("op" -> "add", "path" -> "/name", "value" -> "Bob")
      ),
      filterType = None
    )

    val serverMsg: ServerMessage = ServerMessage.Delta(deltaMsg)
    val json = write(serverMsg)
    println(s"ServerMessage JSON: $json")

    val parsed = ujson.read(json)
    println(s"Parsed: ${parsed.render()}")

    // Check type field
    assert(parsed.obj.contains("type"))
    assertEquals(parsed.obj("type").str, "delta")

    // Check patches is an array
    val patches = parsed.obj("patches")
    assert(patches.isInstanceOf[ujson.Arr], s"patches should be an array, but got: ${patches}")
  }

  test("HydratedReference with delta - check JSON format") {
    val delta = Delta(
      objectId = "test-1",
      fromVersion = 1,
      toVersion = 2,
      patches = List(
        ujson.Obj("op" -> "add", "path" -> "/name", "value" -> "Bob")
      )
    )

    val hydrated = HydratedReference(
      id = "test-1",
      version = 2,
      data = None,
      delta = Some(delta),
      cached = false
    )

    val json = writeJs(hydrated)
    println(s"HydratedReference JSON: ${json.render()}")

    // Check structure
    assert(json.obj.contains("id"))
    assert(json.obj.contains("version"))
    assert(json.obj.contains("delta"))

    // Check delta.patches is an array
    val deltaObj = json.obj("delta").obj
    val patches = deltaObj("patches")
    assert(patches.isInstanceOf[ujson.Arr], s"delta.patches should be an array, but got: ${patches}")
  }

  test("ResponseMessage with hydrated - check JSON format") {
    val delta = Delta(
      objectId = "test-1",
      fromVersion = 1,
      toVersion = 2,
      patches = List(
        ujson.Obj("op" -> "add", "path" -> "/name", "value" -> "Bob")
      )
    )

    val hydrated = HydratedReference(
      id = "test-1",
      version = 2,
      data = None,
      delta = Some(delta),
      cached = false
    )

    val response = ResponseMessage(
      requestId = "req-1",
      success = true,
      data = Some(ujson.Obj("user" -> ujson.Obj("id" -> "test-1", "version" -> 2))),
      hydrated = List(hydrated),
      error = None
    )

    val json = writeJs(response)
    println(s"ResponseMessage JSON: ${json.render()}")

    // Check hydrated is an array
    val hydratedField = json.obj("hydrated")
    assert(hydratedField.isInstanceOf[ujson.Arr], s"hydrated should be an array, but got: ${hydratedField}")

    // Check delta.patches in first hydrated reference
    val firstHydrated = hydratedField.arr(0).obj
    val deltaObj = firstHydrated("delta").obj
    val patches = deltaObj("patches")
    assert(patches.isInstanceOf[ujson.Arr], s"delta.patches should be an array, but got: ${patches}")
  }
}
