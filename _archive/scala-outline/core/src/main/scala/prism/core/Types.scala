package prism.core

import ujson.Value

/**
 * Core Prism types implemented as Scala case classes.
 */

/** Immutable versioned object */
case class PrismObject(
  id: String,
  version: Int,
  data: Value  // ujson.Value for JSON data
) {
  require(version >= 0, "Version must be non-negative")
}

/** Delta between two object versions using JSON Patch */
case class Delta(
  objectId: String,
  fromVersion: Int,
  toVersion: Int,
  patches: List[Value]  // List of JSON Patch operations
) {
  require(fromVersion < toVersion, "Invalid version ordering")
}

/** Reference to an object that may need hydration */
case class ObjectReference(
  id: String,
  version: Int,
  filterType: Option[String] = None,
  subscribe: Boolean = false
)

/** Hydrated reference result */
case class HydratedReference(
  id: String,
  version: Int,
  data: Option[Value] = None,
  delta: Option[Delta] = None,
  cached: Boolean = false
)

/** Request options */
case class RequestOptions(
  hydrateRefs: Boolean = true,
  subscribeToRefs: Boolean = false,
  filterType: Option[String] = None
)

/** Subscription to an object */
case class Subscription(
  objectId: String,
  filterType: String = "default",
  filterParams: Option[Map[String, Value]] = None,
  currentVersion: Int = 0,
  temporary: Boolean = false
)

/** Client state tracking */
case class ClientState(
  subscriptions: Map[String, Subscription] = Map.empty,
  objectVersions: Map[String, Int] = Map.empty
) {
  def getVersion(objectId: String): Option[Int] =
    objectVersions.get(objectId)

  def updateVersion(objectId: String, version: Int): ClientState =
    copy(objectVersions = objectVersions + (objectId -> version))

  def hasSubscription(objectId: String): Boolean =
    subscriptions.contains(objectId)
}

// Protocol Messages

/** Client to Server messages */
sealed trait ClientMessage

case class SubscribeMessage(
  `type`: String = "subscribe",
  objectId: String,
  filterType: Option[String] = None,
  filterParams: Option[Map[String, Value]] = None,
  temporary: Boolean = false
) extends ClientMessage

case class UnsubscribeMessage(
  `type`: String = "unsubscribe",
  objectId: String
) extends ClientMessage

case class SyncStateItem(
  id: String,
  version: Int,
  filterType: String
)

case class SyncMessage(
  `type`: String = "sync",
  states: List[SyncStateItem]
) extends ClientMessage

case class RequestMessage(
  `type`: String = "request",
  requestId: String,
  requestType: String,
  payload: Value,
  options: Option[RequestOptions] = None
) extends ClientMessage

case class UpdateFilterMessage(
  `type`: String = "updateFilter",
  objectId: String,
  filterType: String,
  filterParams: Option[Map[String, Value]] = None
) extends ClientMessage

/** Server to Client messages */
sealed trait ServerMessage

case class FullObjectMessage(
  `type`: String = "fullObject",
  id: String,
  version: Int,
  data: Value,
  filtered: Boolean = false,
  filterType: Option[String] = None
) extends ServerMessage

case class DeltaMessage(
  `type`: String = "delta",
  id: String,
  fromVersion: Int,
  toVersion: Int,
  patches: List[Value],
  filterType: Option[String] = None
) extends ServerMessage

case class ResponseMessage(
  `type`: String = "response",
  requestId: String,
  success: Boolean,
  data: Option[Value] = None,
  hydrated: List[HydratedReference] = List.empty,
  error: Option[String] = None
) extends ServerMessage

case class ErrorMessage(
  `type`: String = "error",
  code: String,
  message: String,
  objectId: Option[String] = None,
  requestId: Option[String] = None
) extends ServerMessage
