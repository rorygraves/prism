package prism.filters

import prism.core.Types.PrismObject

/** Filter that includes only specified fields. */
class FieldsFilter(override val clientMutable: Boolean = true) extends Filter {
  override val name: String = "fields"
  override val cacheable: Boolean = true

  override def apply(obj: PrismObject, params: Option[Map[String, ujson.Value]] = None): PrismObject = {
    params match {
      case Some(p) if p.contains("fields") =>
        p("fields") match {
          case ujson.Arr(fields) =>
            val fieldNames = fields.collect { case ujson.Str(s) => s }.toSet
            val filteredData = ujson.Obj()
            obj.data.obj.foreach {
              case (k, v) if fieldNames.contains(k) => filteredData(k) = v
              case _                                => // Skip
            }
            PrismObject(id = obj.id, version = obj.version, data = filteredData)
          case _ => obj
        }
      case _ => obj
    }
  }
}

/** Filter that excludes specified fields. */
class ExcludeFieldsFilter(override val clientMutable: Boolean = true) extends Filter {
  override val name: String = "exclude"
  override val cacheable: Boolean = true

  override def apply(obj: PrismObject, params: Option[Map[String, ujson.Value]] = None): PrismObject = {
    params match {
      case Some(p) if p.contains("fields") =>
        p("fields") match {
          case ujson.Arr(fields) =>
            val excludeFields = fields.collect { case ujson.Str(s) => s }.toSet
            val filteredData = ujson.Obj()
            obj.data.obj.foreach {
              case (k, v) if !excludeFields.contains(k) => filteredData(k) = v
              case _                                    => // Skip
            }
            PrismObject(id = obj.id, version = obj.version, data = filteredData)
          case _ => obj
        }
      case _ => obj
    }
  }
}

/** Filter for security-sensitive data (server-enforced only). */
class SecurityFilter(hiddenFields: Set[String]) extends Filter {
  override val name: String = "security"
  override val cacheable: Boolean = true
  override val clientMutable: Boolean = false // Server-only

  override def apply(obj: PrismObject, params: Option[Map[String, ujson.Value]] = None): PrismObject = {
    val filteredData = ujson.Obj()
    obj.data.obj.foreach {
      case (k, v) if !hiddenFields.contains(k) => filteredData(k) = v
      case _                                   => // Skip sensitive fields
    }
    PrismObject(id = obj.id, version = obj.version, data = filteredData)
  }
}

/** Default passthrough filter (no transformation). */
class DefaultFilter extends Filter {
  override val name: String = "default"
  override val cacheable: Boolean = true
  override val clientMutable: Boolean = true

  override def apply(obj: PrismObject, params: Option[Map[String, ujson.Value]] = None): PrismObject = obj
}

object CommonFilters {

  /** Create a filter registry with common filters.
    *
    * @return
    *   FilterRegistry with standard filters registered
    */
  def createDefaultRegistry(): FilterRegistry = {
    val registry = new FilterRegistry()
    registry.register(new FieldsFilter())
    registry.register(new ExcludeFieldsFilter())
    registry.register(new DefaultFilter())
    registry
  }

  /** Create a security filter for specific hidden fields.
    *
    * @param hiddenFields
    *   Fields to always exclude
    * @return
    *   A SecurityFilter instance
    */
  def createSecurityFilter(hiddenFields: String*): SecurityFilter = {
    new SecurityFilter(hiddenFields.toSet)
  }
}
