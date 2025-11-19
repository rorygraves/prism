package prism.core

import munit.FunSuite
import prism.core.Protocol._
import prism.core.PickleConfig._

class ProtocolJsonSpec extends FunSuite {

  // ========== SubscribeMessage JSON Tests ==========

  test("DEBUG - Print actual serialization") {
    val msg = SubscribeMessage(
      objectId = "obj-1",
      filterType = Some("fields")
    )

    val json = write(msg)
    println(s"ACTUAL JSON: $json")
    val parsed = ujson.read(json)
    println(s"PARSED: $parsed")
  }

  test("SubscribeMessage - serialize with all fields") {
    val msg = SubscribeMessage(
      objectId = "obj-1",
      filterType = Some("fields"),
      filterParams = Some(ujson.Obj("fields" -> ujson.Arr("name", "age"))),
      temporary = true
    )

    val json = write(msg)
    val parsed = ujson.read(json)

    // Verify field names are snake_case
    assertEquals(parsed("object_id").str, "obj-1")
    assertEquals(parsed("filter_type").str, "fields")
    assert(parsed.obj.contains("filter_params"))
    assertEquals(parsed("temporary").bool, true)

    // Verify filter_params structure
    val filterParams = parsed("filter_params").obj
    assert(filterParams.contains("fields"))
    assertEquals(filterParams("fields").arr.length, 2)
  }

  test("SubscribeMessage - serialize with minimal fields") {
    val msg = SubscribeMessage(objectId = "obj-1")

    val json = write(msg)
    val parsed = ujson.read(json)

    assertEquals(parsed("object_id").str, "obj-1")
    // Optional fields with None should either be null or omitted
    assert(!parsed.obj.contains("filter_type") || parsed("filter_type") == ujson.Null)
    assert(!parsed.obj.contains("filter_params") || parsed("filter_params") == ujson.Null)
    // temporary should be false or omitted (default value)
    assert(!parsed.obj.contains("temporary") || parsed("temporary").bool == false)
  }

  test("SubscribeMessage - deserialize from spec JSON") {
    val json = """{"object_id":"obj-1","filter_type":"fields","filter_params":{"fields":["name","age"]},"temporary":true}"""

    val msg = read[SubscribeMessage](json)

    assertEquals(msg.objectId, "obj-1")
    assertEquals(msg.filterType, Some("fields"))
    assert(msg.filterParams.isDefined)
    assertEquals(msg.temporary, true)

    // Verify filter_params structure
    val filterParams = msg.filterParams.get.obj
    assert(filterParams.contains("fields"))
    assertEquals(filterParams("fields").arr.length, 2)
  }

  test("SubscribeMessage - deserialize with null filter_params") {
    val json = """{"object_id":"obj-1","filter_type":null,"filter_params":null,"temporary":false}"""

    val msg = read[SubscribeMessage](json)

    assertEquals(msg.objectId, "obj-1")
    assertEquals(msg.filterType, None)
    assertEquals(msg.filterParams, None)
    assertEquals(msg.temporary, false)
  }

  test("SubscribeMessage - deserialize with missing optional fields") {
    val json = """{"object_id":"obj-1"}"""

    val msg = read[SubscribeMessage](json)

    assertEquals(msg.objectId, "obj-1")
    assertEquals(msg.filterType, None)
    assertEquals(msg.filterParams, None)
    assertEquals(msg.temporary, false)
  }

  // ========== UpdateFilterMessage JSON Tests ==========

  test("UpdateFilterMessage - serialize") {
    val msg = UpdateFilterMessage(
      objectId = "obj-1",
      filterType = "exclude",
      filterParams = Some(ujson.Obj("fields" -> ujson.Arr("password")))
    )

    val json = write(msg)
    val parsed = ujson.read(json)

    assertEquals(parsed("object_id").str, "obj-1")
    assertEquals(parsed("filter_type").str, "exclude")
    assert(parsed.obj.contains("filter_params"))
  }

  test("UpdateFilterMessage - deserialize from spec JSON") {
    val json = """{"object_id":"obj-1","filter_type":"exclude","filter_params":{"fields":["password"]}}"""

    val msg = read[UpdateFilterMessage](json)

    assertEquals(msg.objectId, "obj-1")
    assertEquals(msg.filterType, "exclude")
    assert(msg.filterParams.isDefined)
  }

  // ========== RequestMessage JSON Tests ==========

  test("RequestMessage - serialize with options") {
    val msg = RequestMessage(
      requestId = "req-123",
      requestType = "createUser",
      payload = ujson.Obj("username" -> "alice"),
      options = Some(ujson.Obj("hydrateRefs" -> true))
    )

    val json = write(msg)
    val parsed = ujson.read(json)

    assertEquals(parsed("request_id").str, "req-123")
    assertEquals(parsed("request_type").str, "createUser")
    assert(parsed.obj.contains("payload"))
    assert(parsed.obj.contains("options"))
  }

  test("RequestMessage - deserialize from spec JSON") {
    val json = """{"request_id":"req-123","request_type":"createUser","payload":{"username":"alice"},"options":{"hydrateRefs":true}}"""

    val msg = read[RequestMessage](json)

    assertEquals(msg.requestId, "req-123")
    assertEquals(msg.requestType, "createUser")
    assertEquals(msg.payload("username").str, "alice")
    assert(msg.options.isDefined)
    assertEquals(msg.options.get("hydrateRefs").bool, true)
  }

  // ========== Server Message JSON Tests ==========

  test("FullObjectMessage - serialize") {
    val msg = FullObjectMessage(
      id = "obj-1",
      version = 5,
      data = ujson.Obj("name" -> "Alice"),
      filtered = true,
      filterType = Some("fields")
    )

    val json = write(msg)
    val parsed = ujson.read(json)

    assertEquals(parsed("id").str, "obj-1")
    assertEquals(parsed("version").num.toInt, 5)
    assert(parsed.obj.contains("data"))
    assertEquals(parsed("filtered").bool, true)
    assert(parsed.obj.contains("filter_type"))
  }

  test("FullObjectMessage - deserialize from spec JSON") {
    val json = """{"id":"obj-1","version":5,"data":{"name":"Alice"},"filtered":true,"filter_type":"fields"}"""

    val msg = read[FullObjectMessage](json)

    assertEquals(msg.id, "obj-1")
    assertEquals(msg.version, 5)
    assertEquals(msg.data("name").str, "Alice")
    assertEquals(msg.filtered, true)
    assertEquals(msg.filterType, Some("fields"))
  }
}
