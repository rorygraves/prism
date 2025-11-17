package prism.core

import prism.core.Types.HydratedReference
import upickle.default._

/** Prism protocol message definitions.
  *
  * Defines the message types that flow between client and server over the transport layer.
  */
object Protocol {

  // ========== Client -> Server Messages ==========

  /** Subscribe to object updates. */
  final case class SubscribeMessage(
      objectId: String,
      filterType: Option[String] = None,
      filterParams: Option[Map[String, ujson.Value]] = None,
      temporary: Boolean = false
  )

  object SubscribeMessage {
    implicit val rw: ReadWriter[SubscribeMessage] = macroRW
  }

  /** Unsubscribe from object updates. */
  final case class UnsubscribeMessage(
      objectId: String
  )

  object UnsubscribeMessage {
    implicit val rw: ReadWriter[UnsubscribeMessage] = macroRW
  }

  /** Client's current state for an object during sync. */
  final case class SyncStateItem(
      id: String,
      version: Int,
      filterType: String
  )

  object SyncStateItem {
    implicit val rw: ReadWriter[SyncStateItem] = macroRW
  }

  /** Synchronize state after reconnection. */
  final case class SyncMessage(
      states: List[SyncStateItem]
  )

  object SyncMessage {
    implicit val rw: ReadWriter[SyncMessage] = macroRW
  }

  /** Business logic request with automatic hydration. */
  final case class RequestMessage(
      requestId: String,
      requestType: String,
      payload: ujson.Value,
      options: Option[ujson.Value] = None
  )

  object RequestMessage {
    implicit val rw: ReadWriter[RequestMessage] = macroRW
  }

  /** Update filter for existing subscription. */
  final case class UpdateFilterMessage(
      objectId: String,
      filterType: String,
      filterParams: Option[Map[String, ujson.Value]] = None
  )

  object UpdateFilterMessage {
    implicit val rw: ReadWriter[UpdateFilterMessage] = macroRW
  }

  /** Helper to merge ujson objects */
  private def mergeObj(base: ujson.Obj, other: ujson.Obj): ujson.Obj = {
    val result = ujson.Obj()
    base.value.foreach { case (k, v) => result(k) = v }
    other.value.foreach { case (k, v) => result(k) = v }
    result
  }

  /** Union type for all client messages. */
  sealed trait ClientMessage

  object ClientMessage {
    final case class Subscribe(msg: SubscribeMessage) extends ClientMessage
    final case class Unsubscribe(msg: UnsubscribeMessage) extends ClientMessage
    final case class Sync(msg: SyncMessage) extends ClientMessage
    final case class Request(msg: RequestMessage) extends ClientMessage
    final case class UpdateFilter(msg: UpdateFilterMessage) extends ClientMessage

    // Custom JSON reader/writer with discriminator
    implicit val rw: ReadWriter[ClientMessage] = readwriter[ujson.Value].bimap[ClientMessage](
      {
        case Subscribe(msg) =>
          mergeObj(ujson.Obj("type" -> "subscribe"), writeJs(msg).obj)
        case Unsubscribe(msg) =>
          mergeObj(ujson.Obj("type" -> "unsubscribe"), writeJs(msg).obj)
        case Sync(msg) =>
          mergeObj(ujson.Obj("type" -> "sync"), writeJs(msg).obj)
        case Request(msg) =>
          mergeObj(ujson.Obj("type" -> "request"), writeJs(msg).obj)
        case UpdateFilter(msg) =>
          mergeObj(ujson.Obj("type" -> "updateFilter"), writeJs(msg).obj)
      },
      json => {
        val msgType = json("type").str
        msgType match {
          case "subscribe"    => Subscribe(read[SubscribeMessage](json))
          case "unsubscribe"  => Unsubscribe(read[UnsubscribeMessage](json))
          case "sync"         => Sync(read[SyncMessage](json))
          case "request"      => Request(read[RequestMessage](json))
          case "updateFilter" => UpdateFilter(read[UpdateFilterMessage](json))
          case _              => throw new IllegalArgumentException(s"Unknown client message type: $msgType")
        }
      }
    )
  }

  // ========== Server -> Client Messages ==========

  /** Full object transmission. */
  final case class FullObjectMessage(
      id: String,
      version: Int,
      data: ujson.Value,
      filtered: Boolean = false,
      filterType: Option[String] = None
  )

  object FullObjectMessage {
    implicit val rw: ReadWriter[FullObjectMessage] = macroRW
  }

  /** Delta update for an object. */
  final case class DeltaMessage(
      id: String,
      fromVersion: Int,
      toVersion: Int,
      patches: List[ujson.Value],
      filterType: Option[String] = None
  )

  object DeltaMessage {
    implicit val rw: ReadWriter[DeltaMessage] = macroRW
  }

  /** Response to a request with smart hydration. */
  final case class ResponseMessage(
      requestId: String,
      success: Boolean,
      data: Option[ujson.Value] = None,
      hydrated: List[HydratedReference] = List.empty,
      error: Option[String] = None
  )

  object ResponseMessage {
    implicit val rw: ReadWriter[ResponseMessage] = macroRW
  }

  /** Error notification. */
  final case class ErrorMessage(
      code: String,
      message: String,
      objectId: Option[String] = None,
      requestId: Option[String] = None
  )

  object ErrorMessage {
    implicit val rw: ReadWriter[ErrorMessage] = macroRW
  }

  /** Union type for all server messages. */
  sealed trait ServerMessage

  object ServerMessage {
    final case class FullObject(msg: FullObjectMessage) extends ServerMessage
    final case class Delta(msg: DeltaMessage) extends ServerMessage
    final case class Response(msg: ResponseMessage) extends ServerMessage
    final case class Error(msg: ErrorMessage) extends ServerMessage

    // Custom JSON reader/writer with discriminator
    implicit val rw: ReadWriter[ServerMessage] = readwriter[ujson.Value].bimap[ServerMessage](
      {
        case FullObject(msg) =>
          mergeObj(ujson.Obj("type" -> "fullObject"), writeJs(msg).obj)
        case Delta(msg) =>
          mergeObj(ujson.Obj("type" -> "delta"), writeJs(msg).obj)
        case Response(msg) =>
          mergeObj(ujson.Obj("type" -> "response"), writeJs(msg).obj)
        case Error(msg) =>
          mergeObj(ujson.Obj("type" -> "error"), writeJs(msg).obj)
      },
      json => {
        val msgType = json("type").str
        msgType match {
          case "fullObject" => FullObject(read[FullObjectMessage](json))
          case "delta"      => Delta(read[DeltaMessage](json))
          case "response"   => Response(read[ResponseMessage](json))
          case "error"      => Error(read[ErrorMessage](json))
          case _            => throw new IllegalArgumentException(s"Unknown server message type: $msgType")
        }
      }
    )
  }

  // ========== Error Codes ==========

  object ErrorCodes {
    val OBJECT_NOT_FOUND = "OBJECT_NOT_FOUND"
    val INVALID_REQUEST = "INVALID_REQUEST"
    val UNAUTHORIZED = "UNAUTHORIZED"
    val INTERNAL_ERROR = "INTERNAL_ERROR"
    val INVALID_FILTER = "INVALID_FILTER"
    val INVALID_VERSION = "INVALID_VERSION"
  }
}
