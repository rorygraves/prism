package prism.storage

import cats.effect.{IO, Ref}
import prism.core.Types.PrismObject

/** In-memory storage adapter for testing and development.
  *
  * This adapter stores all objects in memory and is useful for:
  *   - Testing
  *   - Development without database setup
  *   - Demos and prototypes
  *
  * Note: All data is lost when the application restarts.
  *
  * Thread-safe implementation using cats-effect Ref for concurrent access.
  */
class MemoryStorageAdapter private (
    storage: Ref[IO, Map[String, Map[Int, PrismObject]]],
    currentVersions: Ref[IO, Map[String, Int]]
) extends StorageAdapter {

  override def save(obj: PrismObject): IO[Unit] = {
    for {
      // Add object to storage
      _ <- storage.update { s =>
        val versions = s.getOrElse(obj.id, Map.empty)
        s + (obj.id -> (versions + (obj.version -> obj)))
      }
      // Update current version
      _ <- currentVersions.update { cv =>
        val currentVersion = cv.getOrElse(obj.id, -1)
        if (obj.version > currentVersion) {
          cv + (obj.id -> obj.version)
        } else {
          cv
        }
      }
    } yield ()
  }

  override def getCurrent(objectId: String): IO[Option[PrismObject]] = {
    for {
      cv <- currentVersions.get
      currentVersion = cv.get(objectId)
      result <- currentVersion match {
        case Some(version) => getVersion(objectId, version)
        case None          => IO.pure(None)
      }
    } yield result
  }

  override def getVersion(objectId: String, version: Int): IO[Option[PrismObject]] = {
    storage.get.map { s =>
      s.get(objectId).flatMap(_.get(version))
    }
  }

  override def getVersionsRange(objectId: String, fromVersion: Int, toVersion: Int): IO[List[PrismObject]] = {
    storage.get.map { s =>
      s.get(objectId) match {
        case Some(versions) =>
          (fromVersion to toVersion).flatMap { v =>
            versions.get(v)
          }.toList
        case None => List.empty
      }
    }
  }

  override def delete(objectId: String): IO[Unit] = {
    for {
      _ <- storage.update(_.removed(objectId))
      _ <- currentVersions.update(_.removed(objectId))
    } yield ()
  }

  override def listObjects(limit: Int = 100, offset: Int = 0): IO[List[String]] = {
    storage.get.map { s =>
      val allIds = s.keys.toList.sorted
      allIds.slice(offset, offset + limit)
    }
  }
}

object MemoryStorageAdapter {

  /** Create a new memory storage adapter.
    *
    * @return
    *   A new MemoryStorageAdapter instance wrapped in IO
    */
  def create: IO[MemoryStorageAdapter] = {
    for {
      storage <- Ref.of[IO, Map[String, Map[Int, PrismObject]]](Map.empty)
      currentVersions <- Ref.of[IO, Map[String, Int]](Map.empty)
    } yield new MemoryStorageAdapter(storage, currentVersions)
  }
}
