package prism.storage

import cats.effect.IO
import prism.core.Types.PrismObject

/** Abstract base trait for storage adapters.
  *
  * Storage adapters are responsible for persisting PrismObjects and retrieving them by ID and version. All operations
  * are async using cats-effect IO.
  */
trait StorageAdapter {

  /** Save an object to storage.
    *
    * @param obj
    *   The object to save
    */
  def save(obj: PrismObject): IO[Unit]

  /** Get the current (latest) version of an object.
    *
    * @param objectId
    *   ID of the object
    * @return
    *   The current object, or None if not found
    */
  def getCurrent(objectId: String): IO[Option[PrismObject]]

  /** Get a specific version of an object.
    *
    * @param objectId
    *   ID of the object
    * @param version
    *   Version number
    * @return
    *   The requested object version, or None if not found
    */
  def getVersion(objectId: String, version: Int): IO[Option[PrismObject]]

  /** Get all versions of an object in a range.
    *
    * @param objectId
    *   ID of the object
    * @param fromVersion
    *   Starting version (inclusive)
    * @param toVersion
    *   Ending version (inclusive)
    * @return
    *   List of object versions in the range, ordered by version
    */
  def getVersionsRange(objectId: String, fromVersion: Int, toVersion: Int): IO[List[PrismObject]]

  /** Delete all versions of an object.
    *
    * @param objectId
    *   ID of the object to delete
    */
  def delete(objectId: String): IO[Unit]

  /** List object IDs.
    *
    * @param limit
    *   Maximum number of IDs to return (default: 100)
    * @param offset
    *   Number of IDs to skip (default: 0)
    * @return
    *   List of object IDs
    */
  def listObjects(limit: Int = 100, offset: Int = 0): IO[List[String]]
}
