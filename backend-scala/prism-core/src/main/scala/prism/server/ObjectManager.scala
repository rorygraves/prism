package prism.server

import cats.effect.{IO, Ref}
import cats.syntax.all._
import prism.cache.LRUCache
import prism.core.DeltaComputer
import prism.core.Types._
import prism.core.Protocol._
import prism.filters.FilterRegistry
import prism.storage.StorageAdapter

/** Central manager for object storage, subscriptions, and notifications.
  *
  * The ObjectManager is the core of the Prism server, managing:
  *   - Object storage and versioning
  *   - Per-client subscription state
  *   - Filter application
  *   - Delta computation and caching
  *   - Notification of subscribers on updates
  *
  * Thread-safe implementation using cats-effect IO and Ref.
  *
  * @param storage
  *   Storage adapter for persisting objects
  * @param filterRegistry
  *   Registry of available filters
  * @param clientStates
  *   Mapping of client IDs to their state
  * @param sendCallbacks
  *   Mapping of client IDs to their send message callbacks
  * @param versionCache
  *   Cache for object versions
  * @param deltaCache
  *   Cache for computed deltas
  * @param filterCache
  *   Cache for filtered objects
  */
class ObjectManager private (
    storage: StorageAdapter,
    filterRegistry: FilterRegistry,
    clientStates: Ref[IO, Map[String, ClientState]],
    sendCallbacks: Ref[IO, Map[String, ServerMessage => IO[Unit]]],
    versionCache: LRUCache[String, PrismObject],
    deltaCache: LRUCache[(String, Int, Int, Option[String]), Delta],
    filterCache: LRUCache[(String, Int, String, Option[ujson.Value]), PrismObject]
) {

  /** Register a client's send callback for receiving server messages.
    *
    * @param clientId
    *   Client identifier
    * @param callback
    *   Function to send ServerMessage to this client
    */
  def registerClient(clientId: String, callback: ServerMessage => IO[Unit]): IO[Unit] = {
    sendCallbacks.update(_ + (clientId -> callback))
  }

  /** Get or create client state for a given client ID.
    *
    * @param clientId
    *   Unique client identifier
    * @return
    *   ClientState for this client
    */
  def getClientState(clientId: String): IO[ClientState] = {
    clientStates.modify { states =>
      states.get(clientId) match {
        case Some(state) => (states, state)
        case None =>
          val newState = new ClientState()
          (states + (clientId -> newState), newState)
      }
    }
  }

  /** Remove client state when a client disconnects.
    *
    * @param clientId
    *   Client identifier to remove
    */
  def removeClientState(clientId: String): IO[Unit] = {
    for {
      _ <- clientStates.update(_ - clientId)
      _ <- sendCallbacks.update(_ - clientId)
    } yield ()
  }

  /** Subscribe a client to an object.
    *
    * @param clientId
    *   Client identifier
    * @param objectId
    *   Object to subscribe to
    * @param filterType
    *   Filter to apply (default: "default")
    * @param filterParams
    *   Optional filter parameters
    * @param ongoing
    *   Whether this is an ongoing subscription (vs temporary)
    * @return
    *   The current object (filtered if requested)
    */
  def subscribe(
      clientId: String,
      objectId: String,
      filterType: String = "default",
      filterParams: Option[ujson.Value] = None,
      ongoing: Boolean = false
  ): IO[Option[PrismObject]] = {
    for {
      clientState <- getClientState(clientId)
      currentVersion <- storage.getCurrent(objectId).map(_.map(_.version))
      _ = currentVersion.foreach { version =>
        val subscription = Subscription(
          objectId = objectId,
          currentVersion = version,
          filterType = filterType,
          filterParams = filterParams,
          temporary = !ongoing
        )
        clientState.addSubscription(subscription)
      }
      obj <- storage.getCurrent(objectId)
      filtered <- obj match {
        case Some(o) => applyFilter(o, if (filterType == "default") None else Some(filterType), filterParams).map(Some(_))
        case None    => IO.pure(None)
      }
    } yield filtered
  }

  /** Unsubscribe a client from an object.
    *
    * @param clientId
    *   Client identifier
    * @param objectId
    *   Object to unsubscribe from
    */
  def unsubscribe(clientId: String, objectId: String): IO[Unit] = {
    for {
      clientState <- getClientState(clientId)
      _ = clientState.removeSubscription(objectId)
    } yield ()
  }

  /** Update the filter for a subscription.
    *
    * @param clientId
    *   Client identifier
    * @param objectId
    *   Object ID
    * @param filterType
    *   New filter type
    * @param filterParams
    *   New filter parameters
    */
  def updateFilter(
      clientId: String,
      objectId: String,
      filterType: String,
      filterParams: Option[ujson.Value]
  ): IO[Unit] = {
    for {
      clientState <- getClientState(clientId)
      sub <- IO(clientState.getSubscription(objectId))
      _ <- sub match {
        case Some(s) =>
          val updated = s.copy(filterType = filterType, filterParams = filterParams)
          IO(clientState.addSubscription(updated))
        case None =>
          IO.unit
      }
    } yield ()
  }

  /** Save an object and notify all subscribers.
    *
    * @param obj
    *   Object to save
    * @return
    *   List of client IDs that were notified
    */
  def saveAndNotify(obj: PrismObject): IO[List[String]] = {
    for {
      _ <- storage.save(obj)
      _ <- versionCache.put(s"${obj.id}:${obj.version}", obj)
      notified <- notifySubscribers(obj)
    } yield notified
  }

  /** Notify all subscribers of an object update.
    *
    * @param obj
    *   Updated object
    * @return
    *   List of client IDs that were notified
    */
  def notifySubscribers(obj: PrismObject): IO[List[String]] = {
    for {
      states <- clientStates.get
      notified <- states.toList.flatTraverse { case (clientId, state) =>
        state.getSubscription(obj.id) match {
          case Some(sub) if !sub.temporary =>
            // Update client's version tracking
            state.updateVersion(obj.id, obj.version)
            IO.pure(List(clientId))
          case _ =>
            IO.pure(List.empty[String])
        }
      }
    } yield notified
  }

  /** Get an object from storage, with caching.
    *
    * @param objectId
    *   Object ID
    * @param version
    *   Optional specific version (None = current)
    * @return
    *   The requested object
    */
  def getObject(objectId: String, version: Option[Int] = None): IO[Option[PrismObject]] = {
    version match {
      case Some(v) =>
        val cacheKey = s"$objectId:$v"
        for {
          cached <- versionCache.get(cacheKey)
          result <- cached match {
            case Some(obj) => IO.pure(Some(obj))
            case None =>
              for {
                obj <- storage.getVersion(objectId, v)
                _ <- obj.traverse(o => versionCache.put(cacheKey, o))
              } yield obj
          }
        } yield result

      case None =>
        storage.getCurrent(objectId)
    }
  }

  /** Get a delta between two versions of an object.
    *
    * Uses delta cache for performance.
    *
    * @param objectId
    *   Object ID
    * @param fromVersion
    *   Starting version
    * @param toVersion
    *   Target version
    * @param filterType
    *   Optional filter applied to target object
    * @return
    *   Delta if both versions exist
    */
  def getDelta(
      objectId: String,
      fromVersion: Int,
      toVersion: Int,
      filterType: Option[String] = None
  ): IO[Option[Delta]] = {
    val cacheKey = (objectId, fromVersion, toVersion, filterType)

    for {
      cached <- deltaCache.get(cacheKey)
      result <- cached match {
        case Some(delta) => IO.pure(Some(delta))
        case None =>
          for {
            from <- getObject(objectId, Some(fromVersion))
            to <- getObject(objectId, Some(toVersion))
            delta = (from, to) match {
              case (Some(f), Some(t)) =>
                Some(DeltaComputer.computeDelta(f, t, filterType))
              case _ => None
            }
            _ <- delta.traverse(d => deltaCache.put(cacheKey, d))
          } yield delta
      }
    } yield result
  }

  /** Apply a filter to an object.
    *
    * Uses filter cache for performance.
    *
    * @param obj
    *   Object to filter
    * @param filterType
    *   Optional filter name
    * @param filterParams
    *   Optional filter parameters
    * @return
    *   Filtered object
    */
  def applyFilter(
      obj: PrismObject,
      filterType: Option[String],
      filterParams: Option[ujson.Value] = None
  ): IO[PrismObject] = {
    filterType match {
      case None => IO.pure(obj)
      case Some(ft) =>
        val cacheKey = (obj.id, obj.version, ft, filterParams)

        for {
          cached <- filterCache.get(cacheKey)
          result <- cached match {
            case Some(filtered) => IO.pure(filtered)
            case None =>
              val filter = filterRegistry.get(ft)
              val filtered = filter.apply(obj, filterParams)
              for {
                _ <- if (filter.cacheable) filterCache.put(cacheKey, filtered) else IO.unit
              } yield filtered
          }
        } yield result
    }
  }

  /** Delete an object and notify all subscribers.
    *
    * @param objectId
    *   Object to delete
    * @return
    *   List of client IDs that were notified
    */
  def deleteAndNotify(objectId: String): IO[List[String]] = {
    for {
      _ <- storage.delete(objectId)
      // Remove from caches
      _ <- versionCache.remove(objectId)
      // Notify subscribers - they need to know the object was deleted
      states <- clientStates.get
      notified = states.collect {
        case (clientId, state) if state.getSubscription(objectId).exists(!_.temporary) =>
          state.removeSubscription(objectId)
          clientId
      }.toList
    } yield notified
  }

  /** List all object IDs.
    *
    * @param limit
    *   Maximum number of results
    * @param offset
    *   Starting offset
    * @return
    *   List of object IDs
    */
  def listObjects(limit: Int = 100, offset: Int = 0): IO[List[String]] = {
    storage.listObjects(limit, offset)
  }

  /** Clear temporary subscriptions for a client.
    *
    * This should be called after processing a request to clean up temporary subscriptions.
    *
    * @param clientId
    *   Client identifier
    */
  def clearTemporarySubscriptions(clientId: String): IO[Unit] = {
    for {
      clientState <- getClientState(clientId)
      _ = clientState.clearTemporarySubscriptions()
    } yield ()
  }

  /** Get all active subscriptions for a client.
    *
    * @param clientId
    *   Client identifier
    * @return
    *   List of active (non-temporary) subscriptions
    */
  def getActiveSubscriptions(clientId: String): IO[Iterable[Subscription]] = {
    for {
      clientState <- getClientState(clientId)
    } yield clientState.getActiveSubscriptions
  }

  /** Notify all subscribed clients when an object is updated.
    *
    * This is the key method for real-time synchronization. When an object changes,
    * this method sends delta or fullObject messages to all subscribed clients.
    *
    * @param obj
    *   The updated object
    * @return
    *   Number of clients notified
    */
  def notifyObjectUpdated(obj: PrismObject): IO[Int] = {
    for {
      // Update version cache
      _ <- versionCache.put(s"${obj.id}:${obj.version}", obj)

      // Get all clients and their subscriptions
      states <- clientStates.get

      // Send updates to all subscribed clients
      _ <- states.toList.traverse { case (clientId, state) =>
        state.getSubscription(obj.id) match {
          case Some(subscription) if !subscription.temporary =>
            sendUpdate(clientId, obj, subscription)
          case _ =>
            IO.unit
        }
      }
    } yield states.count { case (_, state) =>
      state.getSubscription(obj.id).exists(!_.temporary)
    }
  }

  /** Send update to a specific client.
    *
    * @param clientId
    *   Client identifier
    * @param obj
    *   Updated object
    * @param subscription
    *   Client's subscription
    */
  private def sendUpdate(clientId: String, obj: PrismObject, subscription: Subscription): IO[Unit] = {
    for {
      // Apply filter
      filtered <- applyFilter(obj, if (subscription.filterType == "default") None else Some(subscription.filterType), subscription.filterParams)

      // Smart sync
      _ <- smartSync(clientId, obj.id, filtered)
    } yield ()
  }

  /** Intelligently sync object with client based on their current state.
    *
    * @param clientId
    *   Client identifier
    * @param objectId
    *   Object ID
    * @param currentObj
    *   Current filtered object
    * @param knownVersion
    *   Version client has (if known)
    */
  private def smartSync(clientId: String, objectId: String, currentObj: PrismObject, knownVersion: Option[Int] = None): IO[Unit] = {
    for {
      clientState <- getClientState(clientId)

      version = knownVersion.orElse(clientState.getVersion(objectId))

      _ <- version match {
        case None =>
          // Client doesn't have object - send full
          sendFullObject(clientId, currentObj)
        case Some(v) if v < currentObj.version =>
          // Client has older version - try delta
          sendDeltaOrFull(clientId, objectId, v, currentObj)
        case _ =>
          // Client has current version, no update needed
          IO.unit
      }

      // Update client's known version
      _ = clientState.updateVersion(objectId, currentObj.version)
    } yield ()
  }

  /** Send delta if efficient, otherwise full object.
    *
    * @param clientId
    *   Client identifier
    * @param objectId
    *   Object ID
    * @param fromVersion
    *   Client's current version
    * @param toObj
    *   Target object
    */
  private def sendDeltaOrFull(clientId: String, objectId: String, fromVersion: Int, toObj: PrismObject): IO[Unit] = {
    for {
      // Try to compute delta
      deltaOpt <- getDelta(objectId, fromVersion, toObj.version)

      _ <- deltaOpt match {
        case Some(delta) if DeltaComputer.isDeltaEfficient(delta, toObj) =>
          // Delta is efficient - send it
          sendDelta(clientId, delta)
        case _ =>
          // Delta too large or unavailable - send full object
          sendFullObject(clientId, toObj)
      }
    } yield ()
  }

  /** Send full object to client.
    *
    * @param clientId
    *   Client identifier
    * @param obj
    *   Object to send
    */
  private def sendFullObject(clientId: String, obj: PrismObject): IO[Unit] = {
    val message = ServerMessage.FullObject(FullObjectMessage(
      id = obj.id,
      version = obj.version,
      data = obj.data,
      filtered = true
    ))
    sendMessage(clientId, message)
  }

  /** Send delta to client.
    *
    * @param clientId
    *   Client identifier
    * @param delta
    *   Delta to send
    */
  private def sendDelta(clientId: String, delta: Delta): IO[Unit] = {
    val message = ServerMessage.Delta(DeltaMessage(
      id = delta.objectId,
      fromVersion = delta.fromVersion,
      toVersion = delta.toVersion,
      patches = delta.patches
    ))
    sendMessage(clientId, message)
  }

  /** Send message to client via callback.
    *
    * @param clientId
    *   Client identifier
    * @param message
    *   Message to send
    */
  private def sendMessage(clientId: String, message: ServerMessage): IO[Unit] = {
    for {
      callbacks <- sendCallbacks.get
      _ <- callbacks.get(clientId) match {
        case Some(callback) => callback(message)
        case None => IO.unit // Client not connected or callback not registered
      }
    } yield ()
  }
}

object ObjectManager {

  /** Create a new ObjectManager.
    *
    * @param storage
    *   Storage adapter
    * @param filterRegistry
    *   Filter registry
    * @param versionCacheSize
    *   Maximum size of version cache (default: 10000)
    * @param deltaCacheSize
    *   Maximum size of delta cache (default: 5000)
    * @param filterCacheSize
    *   Maximum size of filter cache (default: 5000)
    * @return
    *   A new ObjectManager instance
    */
  def create(
      storage: StorageAdapter,
      filterRegistry: FilterRegistry,
      versionCacheSize: Int = 10000,
      deltaCacheSize: Int = 5000,
      filterCacheSize: Int = 5000
  ): IO[ObjectManager] = {
    for {
      clientStates <- Ref.of[IO, Map[String, ClientState]](Map.empty)
      sendCallbacks <- Ref.of[IO, Map[String, ServerMessage => IO[Unit]]](Map.empty)
      versionCache <- LRUCache.create[String, PrismObject](versionCacheSize)
      deltaCache <- LRUCache.create[(String, Int, Int, Option[String]), Delta](deltaCacheSize)
      filterCache <- LRUCache.create[(String, Int, String, Option[ujson.Value]), PrismObject](
        filterCacheSize
      )
    } yield new ObjectManager(storage, filterRegistry, clientStates, sendCallbacks, versionCache, deltaCache, filterCache)
  }
}
