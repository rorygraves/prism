package prism.filters

import munit.FunSuite
import prism.core.Types.PrismObject

class FilterSpec extends FunSuite {

  // ========== FilterRegistry Tests ==========

  test("FilterRegistry - register and get filter") {
    val registry = new FilterRegistry()
    val filter = new DefaultFilter()

    registry.register(filter)

    val retrieved = registry.get("default")
    assertEquals(retrieved.name, "default")
  }

  test("FilterRegistry - rejects duplicate registration") {
    val registry = new FilterRegistry()
    registry.register(new DefaultFilter())

    intercept[IllegalArgumentException] {
      registry.register(new DefaultFilter())
    }
  }

  test("FilterRegistry - get throws on missing filter") {
    val registry = new FilterRegistry()

    intercept[NoSuchElementException] {
      registry.get("nonexistent")
    }
  }

  test("FilterRegistry - has returns true for existing filter") {
    val registry = new FilterRegistry()
    registry.register(new DefaultFilter())

    assert(registry.has("default"))
  }

  test("FilterRegistry - has returns false for missing filter") {
    val registry = new FilterRegistry()
    assert(!registry.has("nonexistent"))
  }

  test("FilterRegistry - registerFunction") {
    val registry = new FilterRegistry()
    registry.registerFunction(
      "uppercase",
      (data, _) => {
        val result = ujson.Obj()
        data.obj.foreach { case (k, v) =>
          v match {
            case ujson.Str(s) => result(k) = ujson.Str(s.toUpperCase)
            case other        => result(k) = other
          }
        }
        result
      }
    )

    assert(registry.has("uppercase"))
    val filter = registry.get("uppercase")
    assertEquals(filter.cacheable, true)
    assertEquals(filter.clientMutable, false) // Default for registerFunction
  }

  test("FilterRegistry - apply filter to object") {
    val registry = new FilterRegistry()
    registry.register(new FieldsFilter())

    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30, "email" -> "alice@example.com"))
    val params = Some(Map("fields" -> ujson.Arr("name", "age")))

    val filtered = registry.apply(obj, "fields", params)

    assert(filtered.data.obj.contains("name"))
    assert(filtered.data.obj.contains("age"))
    assert(!filtered.data.obj.contains("email"))
  }

  test("FilterRegistry - listFilters") {
    val registry = new FilterRegistry()
    registry.register(new DefaultFilter())
    registry.register(new FieldsFilter())
    registry.register(new ExcludeFieldsFilter())

    val filters = registry.listFilters
    assertEquals(filters.sorted, List("default", "exclude", "fields"))
  }

  // ========== DefaultFilter Tests ==========

  test("DefaultFilter - returns object unchanged") {
    val filter = new DefaultFilter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30))

    val filtered = filter.apply(obj)

    assertEquals(filtered.id, obj.id)
    assertEquals(filtered.version, obj.version)
    assertEquals(filtered.data, obj.data)
  }

  test("DefaultFilter - properties") {
    val filter = new DefaultFilter()
    assertEquals(filter.name, "default")
    assertEquals(filter.cacheable, true)
    assertEquals(filter.clientMutable, true)
  }

  // ========== FieldsFilter Tests ==========

  test("FieldsFilter - includes only specified fields") {
    val filter = new FieldsFilter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30, "email" -> "alice@example.com"))
    val params = Some(Map("fields" -> ujson.Arr("name", "age")))

    val filtered = filter.apply(obj, params)

    assertEquals(filtered.id, "obj-1")
    assertEquals(filtered.version, 1)
    assert(filtered.data.obj.contains("name"))
    assert(filtered.data.obj.contains("age"))
    assert(!filtered.data.obj.contains("email"))
    assertEquals(filtered.data("name").str, "Alice")
  }

  test("FieldsFilter - returns original if no params") {
    val filter = new FieldsFilter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val filtered = filter.apply(obj, None)

    assertEquals(filtered.data, obj.data)
  }

  test("FieldsFilter - returns original if params missing 'fields' key") {
    val filter = new FieldsFilter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val params = Some(Map("other" -> ujson.Str("value")))

    val filtered = filter.apply(obj, params)

    assertEquals(filtered.data, obj.data)
  }

  test("FieldsFilter - handles empty fields list") {
    val filter = new FieldsFilter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30))
    val params = Some(Map("fields" -> ujson.Arr()))

    val filtered = filter.apply(obj, params)

    assertEquals(filtered.data.obj.size, 0)
  }

  test("FieldsFilter - handles non-existent fields") {
    val filter = new FieldsFilter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val params = Some(Map("fields" -> ujson.Arr("name", "nonexistent")))

    val filtered = filter.apply(obj, params)

    assertEquals(filtered.data.obj.size, 1)
    assert(filtered.data.obj.contains("name"))
  }

  test("FieldsFilter - properties") {
    val filter = new FieldsFilter()
    assertEquals(filter.name, "fields")
    assertEquals(filter.cacheable, true)
    assertEquals(filter.clientMutable, true)
  }

  // ========== ExcludeFieldsFilter Tests ==========

  test("ExcludeFieldsFilter - excludes specified fields") {
    val filter = new ExcludeFieldsFilter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "age" -> 30, "password" -> "secret"))
    val params = Some(Map("fields" -> ujson.Arr("password")))

    val filtered = filter.apply(obj, params)

    assert(filtered.data.obj.contains("name"))
    assert(filtered.data.obj.contains("age"))
    assert(!filtered.data.obj.contains("password"))
  }

  test("ExcludeFieldsFilter - returns original if no params") {
    val filter = new ExcludeFieldsFilter()
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))

    val filtered = filter.apply(obj, None)

    assertEquals(filtered.data, obj.data)
  }

  test("ExcludeFieldsFilter - excludes multiple fields") {
    val filter = new ExcludeFieldsFilter()
    val obj = PrismObject(
      "obj-1",
      1,
      ujson.Obj("name" -> "Alice", "age" -> 30, "password" -> "secret", "ssn" -> "123-45-6789")
    )
    val params = Some(Map("fields" -> ujson.Arr("password", "ssn")))

    val filtered = filter.apply(obj, params)

    assertEquals(filtered.data.obj.size, 2)
    assert(filtered.data.obj.contains("name"))
    assert(filtered.data.obj.contains("age"))
  }

  test("ExcludeFieldsFilter - properties") {
    val filter = new ExcludeFieldsFilter()
    assertEquals(filter.name, "exclude")
    assertEquals(filter.cacheable, true)
    assertEquals(filter.clientMutable, true)
  }

  // ========== SecurityFilter Tests ==========

  test("SecurityFilter - hides sensitive fields") {
    val filter = new SecurityFilter(Set("password", "ssn"))
    val obj = PrismObject(
      "obj-1",
      1,
      ujson.Obj("name" -> "Alice", "password" -> "secret", "ssn" -> "123-45-6789")
    )

    val filtered = filter.apply(obj)

    assert(filtered.data.obj.contains("name"))
    assert(!filtered.data.obj.contains("password"))
    assert(!filtered.data.obj.contains("ssn"))
  }

  test("SecurityFilter - ignores params (server-enforced)") {
    val filter = new SecurityFilter(Set("password"))
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice", "password" -> "secret"))
    val params = Some(Map("fields" -> ujson.Arr("password"))) // Client tries to get password

    val filtered = filter.apply(obj, params)

    // Password should still be hidden regardless of params
    assert(!filtered.data.obj.contains("password"))
  }

  test("SecurityFilter - properties") {
    val filter = new SecurityFilter(Set("password"))
    assertEquals(filter.name, "security")
    assertEquals(filter.cacheable, true)
    assertEquals(filter.clientMutable, false) // Server-only!
  }

  // ========== FunctionFilter Tests ==========

  test("FunctionFilter - applies custom transformation") {
    val uppercaseTransform: (ujson.Value, Option[Map[String, ujson.Value]]) => ujson.Value =
      (data, _) => {
        val result = ujson.Obj()
        data.obj.foreach { case (k, v) =>
          v match {
            case ujson.Str(s) => result(k) = ujson.Str(s.toUpperCase)
            case other        => result(k) = other
          }
        }
        result
      }

    val filter = new FunctionFilter("uppercase", uppercaseTransform)
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "alice", "city" -> "nyc"))

    val filtered = filter.apply(obj)

    assertEquals(filtered.data("name").str, "ALICE")
    assertEquals(filtered.data("city").str, "NYC")
  }

  test("FunctionFilter - uses params") {
    val addPrefixTransform: (ujson.Value, Option[Map[String, ujson.Value]]) => ujson.Value =
      (data, params) => {
        val prefix = params.flatMap(_.get("prefix")).collect { case ujson.Str(s) => s }.getOrElse("")
        val result = ujson.Obj()
        data.obj.foreach { case (k, v) =>
          v match {
            case ujson.Str(s) => result(k) = ujson.Str(prefix + s)
            case other        => result(k) = other
          }
        }
        result
      }

    val filter = new FunctionFilter("addPrefix", addPrefixTransform)
    val obj = PrismObject("obj-1", 1, ujson.Obj("name" -> "Alice"))
    val params = Some(Map("prefix" -> ujson.Str("Ms. ")))

    val filtered = filter.apply(obj, params)

    assertEquals(filtered.data("name").str, "Ms. Alice")
  }

  // ========== CommonFilters Tests ==========

  test("CommonFilters - createDefaultRegistry has standard filters") {
    val registry = CommonFilters.createDefaultRegistry()

    assert(registry.has("default"))
    assert(registry.has("fields"))
    assert(registry.has("exclude"))
  }

  test("CommonFilters - createSecurityFilter") {
    val filter = CommonFilters.createSecurityFilter("password", "ssn")
    val obj = PrismObject(
      "obj-1",
      1,
      ujson.Obj("name" -> "Alice", "password" -> "secret", "ssn" -> "123-45-6789")
    )

    val filtered = filter.apply(obj)

    assert(filtered.data.obj.contains("name"))
    assert(!filtered.data.obj.contains("password"))
    assert(!filtered.data.obj.contains("ssn"))
  }
}
