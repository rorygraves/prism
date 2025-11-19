package prism.core

import munit.FunSuite
import prism.core.PickleConfig._

class PickleConfigTest extends FunSuite {

  case class SimpleMessage(
      objectId: String,
      filterType: Option[String] = None
  )

  object SimpleMessage {
    implicit val rw: ReadWriter[SimpleMessage] = macroRW
  }

  test("Simple Option[String] serialization") {
    val msg = SimpleMessage(
      objectId = "test-1",
      filterType = Some("fields")
    )

    val json = write(msg)
    println(s"JSON: $json")

    val parsed = ujson.read(json)
    println(s"Parsed: $parsed")
    println(s"object_id type: ${parsed("object_id").getClass}")
    println(s"filter_type type: ${parsed("filter_type").getClass}")
    println(s"filter_type value: ${parsed("filter_type")}")
  }
}
