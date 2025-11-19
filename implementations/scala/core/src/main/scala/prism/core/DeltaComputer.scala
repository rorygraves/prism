package prism.core

import prism.core.Types.{Delta, PrismObject}
import prism.core.PickleConfig._

import scala.collection.mutable

/** Delta computation and application using JSON Patch (RFC 6902).
  *
  * Implements a subset of JSON Patch operations sufficient for Prism's needs:
  * - replace: Replace a value at a path
  * - add: Add a value at a path (includes array append with "/-")
  * - remove: Remove a value at a path
  */
object DeltaComputer {

  /** Compute the delta between two versions of an object.
    *
    * @param fromObj
    *   The earlier version
    * @param toObj
    *   The later version
    * @param filterType
    *   Optional filter applied to both objects (currently unused)
    * @return
    *   Delta containing the JSON Patch operations
    * @throws IllegalArgumentException
    *   if objects have different IDs or invalid version ordering
    */
  def computeDelta(
      fromObj: PrismObject,
      toObj: PrismObject,
      filterType: Option[String] = None
  ): Delta = {
    require(
      fromObj.id == toObj.id,
      s"Cannot compute delta between different objects: ${fromObj.id} vs ${toObj.id}"
    )
    require(
      fromObj.version < toObj.version,
      s"Invalid version ordering: ${fromObj.version} >= ${toObj.version}"
    )

    // Compute JSON Patch operations
    val patches = computeJsonPatch(fromObj.data, toObj.data)

    Delta(
      objectId = fromObj.id,
      fromVersion = fromObj.version,
      toVersion = toObj.version,
      patches = patches
    )
  }

  /** Apply a delta to an object to produce the next version.
    *
    * @param obj
    *   The base object
    * @param delta
    *   The delta to apply
    * @return
    *   New PrismObject with the delta applied
    * @throws IllegalArgumentException
    *   if delta doesn't match object
    */
  def applyDelta(obj: PrismObject, delta: Delta): PrismObject = {
    require(
      obj.id == delta.objectId,
      s"Delta object ID mismatch: delta for ${delta.objectId}, applied to ${obj.id}"
    )
    require(
      obj.version == delta.fromVersion,
      s"Version mismatch: object is v${obj.version}, delta expects v${delta.fromVersion}"
    )

    // Apply JSON Patch operations
    val newData = applyJsonPatch(obj.data, delta.patches)

    PrismObject(id = obj.id, version = delta.toVersion, data = newData)
  }

  /** Estimate the size of a delta in bytes (for efficiency comparison). */
  def estimateDeltaSize(delta: Delta): Int = {
    write(delta).length
  }

  /** Estimate the size of an object in bytes. */
  def estimateObjectSize(obj: PrismObject): Int = {
    write(obj.data).length
  }

  /** Check if sending delta is more efficient than full object.
    *
    * @param delta
    *   The delta to check
    * @param fullObject
    *   The full object
    * @param threshold
    *   Delta must be less than this fraction of full object size (default: 0.7)
    * @return
    *   True if delta is more efficient
    */
  def isDeltaEfficient(delta: Delta, fullObject: PrismObject, threshold: Double = 0.7): Boolean = {
    val deltaSize = estimateDeltaSize(delta)
    val objectSize = estimateObjectSize(fullObject)

    deltaSize < (objectSize * threshold)
  }

  // ========== JSON Patch Implementation ==========

  /** Compute JSON Patch operations between two JSON values.
    *
    * This is a simplified implementation that generates replace, add, and remove operations.
    */
  private def computeJsonPatch(from: ujson.Value, to: ujson.Value, path: String = ""): List[ujson.Value] = {
    (from, to) match {
      // Both are objects - compare keys
      case (fromObj: ujson.Obj, toObj: ujson.Obj) =>
        val patches = mutable.ListBuffer[ujson.Value]()

        // Find removed keys
        fromObj.value.keys.foreach { key =>
          if (!toObj.value.contains(key)) {
            patches += ujson.Obj(
              "op" -> "remove",
              "path" -> s"$path/$key"
            )
          }
        }

        // Find added or modified keys
        toObj.value.foreach { case (key, toValue) =>
          fromObj.value.get(key) match {
            case None =>
              // Key added
              patches += ujson.Obj(
                "op" -> "add",
                "path" -> s"$path/$key",
                "value" -> toValue
              )
            case Some(fromValue) =>
              // Key exists - recurse if different
              if (fromValue != toValue) {
                patches ++= computeJsonPatch(fromValue, toValue, s"$path/$key")
              }
          }
        }

        patches.toList

      // Both are arrays - compare elements
      case (fromArr: ujson.Arr, toArr: ujson.Arr) =>
        val patches = mutable.ListBuffer[ujson.Value]()

        if (fromArr.value.length == toArr.value.length) {
          // Same length - check each element
          fromArr.value.zip(toArr.value).zipWithIndex.foreach { case ((fromElem, toElem), idx) =>
            if (fromElem != toElem) {
              patches ++= computeJsonPatch(fromElem, toElem, s"$path/$idx")
            }
          }
        } else {
          // Different lengths - replace entire array
          patches += ujson.Obj(
            "op" -> "replace",
            "path" -> path,
            "value" -> toArr
          )
        }

        patches.toList

      // Primitives or type mismatch - replace
      case _ =>
        if (from != to) {
          List(
            ujson.Obj(
              "op" -> "replace",
              "path" -> path,
              "value" -> to
            )
          )
        } else {
          List.empty
        }
    }
  }

  /** Apply JSON Patch operations to a JSON value. */
  private def applyJsonPatch(base: ujson.Value, patches: List[ujson.Value]): ujson.Value = {
    var current = base

    patches.foreach { patch =>
      val op = patch("op").str
      val path = patch("path").str

      op match {
        case "replace" =>
          val value = patch("value")
          current = setAtPath(current, path, value)

        case "add" =>
          val value = patch("value")
          current = setAtPath(current, path, value, isAdd = true)

        case "remove" =>
          current = removeAtPath(current, path)

        case _ =>
          throw new IllegalArgumentException(s"Unsupported patch operation: $op")
      }
    }

    current
  }

  /** Set a value at a JSON path. */
  private def setAtPath(root: ujson.Value, path: String, value: ujson.Value, isAdd: Boolean = false): ujson.Value = {
    if (path.isEmpty || path == "/") {
      return value
    }

    val parts = path.split("/").filter(_.nonEmpty)
    setAtPathRecursive(root, parts.toList, value, isAdd)
  }

  private def setAtPathRecursive(
      current: ujson.Value,
      parts: List[String],
      value: ujson.Value,
      isAdd: Boolean
  ): ujson.Value = {
    parts match {
      case Nil => value

      case key :: Nil =>
        current match {
          case obj: ujson.Obj =>
            val newObj = ujson.Obj()
            obj.value.foreach { case (k, v) => newObj(k) = v }
            newObj(key) = value
            newObj

          case arr: ujson.Arr =>
            val newArr = ujson.Arr(arr.value.toSeq: _*)
            if (key == "-") {
              // Append to array
              newArr.value += value
            } else {
              val idx = key.toInt
              newArr.value(idx) = value
            }
            newArr

          case _ => throw new IllegalArgumentException(s"Cannot set key '$key' on non-object/array")
        }

      case key :: rest =>
        current match {
          case obj: ujson.Obj =>
            val newObj = ujson.Obj()
            obj.value.foreach { case (k, v) => newObj(k) = v }
            val nested = obj.value.getOrElse(key, ujson.Obj())
            newObj(key) = setAtPathRecursive(nested, rest, value, isAdd)
            newObj

          case arr: ujson.Arr =>
            val newArr = ujson.Arr(arr.value.toSeq: _*)
            val idx = key.toInt
            newArr.value(idx) = setAtPathRecursive(arr.value(idx), rest, value, isAdd)
            newArr

          case _ => throw new IllegalArgumentException(s"Cannot traverse key '$key' on non-object/array")
        }
    }
  }

  /** Remove a value at a JSON path. */
  private def removeAtPath(root: ujson.Value, path: String): ujson.Value = {
    if (path.isEmpty || path == "/") {
      throw new IllegalArgumentException("Cannot remove root")
    }

    val parts = path.split("/").filter(_.nonEmpty)
    removeAtPathRecursive(root, parts.toList)
  }

  private def removeAtPathRecursive(current: ujson.Value, parts: List[String]): ujson.Value = {
    parts match {
      case Nil => throw new IllegalArgumentException("Empty path")

      case key :: Nil =>
        current match {
          case obj: ujson.Obj =>
            val newObj = ujson.Obj()
            obj.value.foreach {
              case (k, v) if k != key => newObj(k) = v
              case _                  => // Skip the key to remove
            }
            newObj

          case arr: ujson.Arr =>
            val newArr = ujson.Arr()
            val idx = key.toInt
            arr.value.zipWithIndex.foreach {
              case (v, i) if i != idx => newArr.value += v
              case _                  => // Skip the index to remove
            }
            newArr

          case _ => throw new IllegalArgumentException(s"Cannot remove key '$key' from non-object/array")
        }

      case key :: rest =>
        current match {
          case obj: ujson.Obj =>
            val newObj = ujson.Obj()
            obj.value.foreach { case (k, v) =>
              if (k == key) {
                newObj(k) = removeAtPathRecursive(v, rest)
              } else {
                newObj(k) = v
              }
            }
            newObj

          case arr: ujson.Arr =>
            val newArr = ujson.Arr(arr.value.toSeq: _*)
            val idx = key.toInt
            newArr.value(idx) = removeAtPathRecursive(arr.value(idx), rest)
            newArr

          case _ => throw new IllegalArgumentException(s"Cannot traverse key '$key' on non-object/array")
        }
    }
  }
}
