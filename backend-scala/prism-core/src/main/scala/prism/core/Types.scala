package prism.core

import upickle.default._

/** Core Prism types and data structures. *
  *
  * These types are immutable and represent the fundamental building blocks of the Prism protocol.
  */
object Types {

  /** Immutable versioned object.
    *
    * Every object in Prism contains an ID, version number, and data payload. Objects reference other objects by ID
    * only, never by direct reference.
    *
    * @param id
    *   Unique identifier for the object
    * @param version
    *   Monotonically increasing version number (must be >= 0)
    * @param data
    *   The object's actual content as JSON
    */
  final case class PrismObject(id: String, version: Int, data: ujson.Value) {
    require(version >= 0, s"Version must be >= 0, got $version")
    require(id.nonEmpty, "Object ID cannot be empty")
  }

  object PrismObject {
    implicit val rw: ReadWriter[PrismObject] = macroRW
  }

  /** Represents a change between two versions of an object using JSON Patch (RFC 6902).
    *
    * @param objectId
    *   ID of the object this delta applies to
    * @param fromVersion
    *   Starting version
    * @param toVersion
    *   Target version
    * @param patches
    *   JSON Patch operations
    */
  final case class Delta(
      objectId: String,
      fromVersion: Int,
      toVersion: Int,
      patches: List[ujson.Value]
  ) {
    require(fromVersion >= 0, s"From version must be >= 0, got $fromVersion")
    require(toVersion >= 0, s"To version must be >= 0, got $toVersion")
    require(toVersion > fromVersion, s"To version ($toVersion) must be > from version ($fromVersion)")
  }

  object Delta {
    implicit val rw: ReadWriter[Delta] = macroRW
  }

  /** Intelligent reference to a Prism object.
    *
    * Used in responses to indicate an object that may need to be hydrated based on the client's current state.
    *
    * @param id
    *   Object ID being referenced
    * @param version
    *   Version of the object
    * @param filterType
    *   Filter to apply when hydrating (optional)
    * @param subscribe
    *   Whether client should auto-subscribe to this object
    */
  final case class ObjectReference(
      id: String,
      version: Int,
      filterType: Option[String] = None,
      subscribe: Boolean = false
  ) {
    require(version >= 0, s"Version must be >= 0, got $version")

    /** Convert to Map for JSON serialization */
    def toMap: Map[String, Any] = {
      val base: Map[String, Any] = Map("id" -> id, "version" -> version)
      val withFilter = filterType.fold(base)(ft => base + ("filterType" -> ft))
      if (subscribe) withFilter + ("subscribe" -> true) else withFilter
    }
  }

  object ObjectReference {
    implicit val rw: ReadWriter[ObjectReference] = macroRW
  }

  /** Result of hydrating an ObjectReference based on client state.
    *
    * Exactly one of `data`, `delta`, or `cached` should be true/populated.
    *
    * @param id
    *   Object ID
    * @param version
    *   Current version of the object
    * @param data
    *   Full object data (if client has no version or delta is inefficient)
    * @param delta
    *   Delta to apply (if client has older version and delta is efficient)
    * @param cached
    *   True if client already has this exact version
    */
  final case class HydratedReference(
      id: String,
      version: Int,
      data: Option[ujson.Value] = None,
      delta: Option[Delta] = None,
      cached: Boolean = false
  )

  object HydratedReference {
    implicit val rw: ReadWriter[HydratedReference] = macroRW
  }

  /** Options for how to process a request and its response.
    *
    * @param hydrateRefs
    *   Auto-hydrate object references in response (default: true)
    * @param subscribeToRefs
    *   Auto-subscribe to referenced objects (default: false)
    * @param filterType
    *   Default filter to apply to references (optional)
    * @param maxHydrationDepth
    *   Maximum depth for recursive hydration (default: 5)
    */
  final case class RequestOptions(
      hydrateRefs: Boolean = true,
      subscribeToRefs: Boolean = false,
      filterType: Option[String] = None,
      maxHydrationDepth: Int = 5
  ) {
    require(maxHydrationDepth >= 0, s"Max hydration depth must be >= 0, got $maxHydrationDepth")
  }

  object RequestOptions {
    implicit val rw: ReadWriter[RequestOptions] = macroRW

    val default: RequestOptions = RequestOptions()
  }

  /** Represents a client subscription to an object.
    *
    * @param objectId
    *   ID of the object being subscribed to
    * @param filterType
    *   Type of filter to apply (default: "default")
    * @param filterParams
    *   Parameters for the filter (optional)
    * @param currentVersion
    *   Last version sent to the client (default: 0)
    * @param temporary
    *   True for one-time fetch, false for ongoing subscription
    */
  final case class Subscription(
      objectId: String,
      filterType: String = "default",
      filterParams: Option[Map[String, ujson.Value]] = None,
      currentVersion: Int = 0,
      temporary: Boolean = false
  ) {
    require(currentVersion >= 0, s"Current version must be >= 0, got $currentVersion")
  }

  object Subscription {
    implicit val rw: ReadWriter[Subscription] = macroRW
  }

  /** Tracks what a client knows about objects.
    *
    * This is mutable state that tracks a client's subscriptions and known object versions. Thread safety must be
    * handled by the caller.
    *
    * @param subscriptions
    *   Map of object ID -> Subscription
    * @param objectVersions
    *   Map of object ID -> known version
    */
  final case class ClientState(
      subscriptions: scala.collection.mutable.Map[String, Subscription] =
        scala.collection.mutable.Map.empty,
      objectVersions: scala.collection.mutable.Map[String, Int] =
        scala.collection.mutable.Map.empty
  ) {

    /** Get the version the client has for an object, if any. */
    def getVersion(objectId: String): Option[Int] =
      objectVersions.get(objectId)

    /** Update the client's known version for an object. */
    def updateVersion(objectId: String, version: Int): Unit = {
      objectVersions(objectId) = version
    }

    /** Check if the client has a subscription to an object. */
    def hasSubscription(objectId: String): Boolean =
      subscriptions.contains(objectId)

    /** Add a subscription for the client. */
    def addSubscription(subscription: Subscription): Unit = {
      subscriptions(subscription.objectId) = subscription
    }

    /** Remove a subscription for the client. */
    def removeSubscription(objectId: String): Unit = {
      val _ = subscriptions.remove(objectId)
    }

    /** Get a subscription by object ID. */
    def getSubscription(objectId: String): Option[Subscription] =
      subscriptions.get(objectId)

    /** Get all active (non-temporary) subscriptions. */
    def getActiveSubscriptions: Iterable[Subscription] =
      subscriptions.values.filterNot(_.temporary)

    /** Remove all temporary subscriptions. */
    def clearTemporarySubscriptions(): Unit = {
      subscriptions.filterInPlace((_, sub) => !sub.temporary)
    }

    /** Create a deep copy of this client state. */
    def copy(): ClientState = {
      ClientState(
        subscriptions = scala.collection.mutable.Map(subscriptions.toSeq: _*),
        objectVersions = scala.collection.mutable.Map(objectVersions.toSeq: _*)
      )
    }
  }

  object ClientState {
    def empty: ClientState = ClientState()
  }
}
