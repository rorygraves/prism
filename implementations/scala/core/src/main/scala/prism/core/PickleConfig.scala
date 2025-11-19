package prism.core

import upickle.AttributeTagged

/** Custom upickle configuration for snake_case JSON field names.
  *
  * Automatically converts Scala camelCase field names to JSON snake_case.
  */
object PickleConfig extends AttributeTagged {

  /** Convert camelCase to snake_case for JSON writing */
  override def objectAttributeKeyWriteMap(s: CharSequence): CharSequence = {
    camelToSnake(s.toString)
  }

  /** Convert snake_case to camelCase for JSON reading */
  override def objectAttributeKeyReadMap(s: CharSequence): CharSequence = {
    snakeToCamel(s.toString)
  }

  /** Custom Option reader that deserializes null as None and value as Some(value) */
  implicit override def OptionReader[T: Reader]: Reader[Option[T]] =
    reader[ujson.Value].map[Option[T]] {
      case ujson.Null => None
      case jsValue    => Some(read[T](jsValue))
    }

  /** Custom Option writer that serializes Some(value) as value and None as null */
  implicit override def OptionWriter[T: Writer]: Writer[Option[T]] =
    writer[ujson.Value].comap {
      case Some(value) => writeJs(value)
      case None        => ujson.Null
    }

  /** Convert camelCase string to snake_case */
  private def camelToSnake(s: String): String = {
    s.replaceAll("([A-Z])", "_$1").toLowerCase.replaceFirst("^_", "")
  }

  /** Convert snake_case string to camelCase */
  private def snakeToCamel(s: String): String = {
    val parts = s.split("_")
    if (parts.length == 1) {
      parts(0)
    } else {
      parts.head + parts.tail.map(_.capitalize).mkString
    }
  }
}
