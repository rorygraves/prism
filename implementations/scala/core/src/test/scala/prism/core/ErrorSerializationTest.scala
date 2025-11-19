package prism.core

import munit.FunSuite
import prism.core.Protocol._
import prism.core.PickleConfig._

class ErrorSerializationTest extends FunSuite {

  test("ErrorMessage serialization - check JSON format with requestId") {
    val errorMsg = ErrorMessage(
      code = "INTERNAL_ERROR",
      message = "Test error message",
      requestId = Some("req-123")
    )

    val json = writeJs(errorMsg)
    println(s"ErrorMessage JSON: ${json.render()}")

    // Check structure
    assert(json.obj.contains("code"))
    assert(json.obj.contains("message"))
    assert(json.obj.contains("request_id"), "ErrorMessage should have request_id field")

    // Check values
    assertEquals(json.obj("code").str, "INTERNAL_ERROR")
    assertEquals(json.obj("message").str, "Test error message")
    assertEquals(json.obj("request_id").str, "req-123")
  }

  test("ServerMessage.Error serialization - check JSON format") {
    val errorMsg = ErrorMessage(
      code = "INTERNAL_ERROR",
      message = "Test error",
      requestId = Some("req-456")
    )

    val serverMsg: ServerMessage = ServerMessage.Error(errorMsg)
    val json = write(serverMsg)
    println(s"ServerMessage.Error JSON: $json")

    val parsed = ujson.read(json)
    println(s"Parsed: ${parsed.render()}")

    // Check type field
    assert(parsed.obj.contains("type"))
    assertEquals(parsed.obj("type").str, "error")

    // Check error fields
    assert(parsed.obj.contains("code"))
    assert(parsed.obj.contains("message"))
    assert(parsed.obj.contains("request_id"), "ServerMessage.Error should have request_id field")

    // Check values
    assertEquals(parsed.obj("code").str, "INTERNAL_ERROR")
    assertEquals(parsed.obj("message").str, "Test error")
    assertEquals(parsed.obj("request_id").str, "req-456")
  }

  test("ErrorMessage without requestId") {
    val errorMsg = ErrorMessage(
      code = "INTERNAL_ERROR",
      message = "Test error without requestId",
      requestId = None
    )

    val json = writeJs(errorMsg)
    println(s"ErrorMessage without requestId JSON: ${json.render()}")

    // request_id should not be present when None
    assert(!json.obj.contains("request_id"), "ErrorMessage should not have request_id field when None")
  }
}
