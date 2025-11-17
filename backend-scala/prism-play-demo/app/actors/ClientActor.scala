package actors

import akka.actor._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import play.api.Logger
import prism.core.Protocol._
import prism.core.Types._
import prism.server.{ObjectManager, RequestRouter}
import prism.storage.StorageAdapter
import services.ChatBusinessHandler
import upickle.default._

import scala.concurrent.ExecutionContext

/** Actor handling a single WebSocket client connection */
class ClientActor(
    clientId: String,
    out: ActorRef,
    storage: StorageAdapter,
    objectManager: ObjectManager,
    requestRouter: RequestRouter,
    chatHandler: ChatBusinessHandler
)(implicit ec: ExecutionContext) extends Actor {

  private val logger = Logger(getClass)

  override def preStart(): Unit = {
    logger.info(s"[WEBSOCKET] Client $clientId connected")
  }

  override def postStop(): Unit = {
    logger.info(s"[WEBSOCKET] Client $clientId disconnected")
    // Clean up client state
    objectManager.removeClientState(clientId).unsafeRunSync()
  }

  def receive: Receive = {
    case msg: String =>
      try {
        val json = ujson.read(msg)
        val messageType = json("type").str

        logger.info(s"[WEBSOCKET] Received from $clientId: $messageType")

        messageType match {
          case "subscribe" =>
            handleSubscribe(json)

          case "unsubscribe" =>
            handleUnsubscribe(json)

          case "request" =>
            handleRequest(json)

          case "updateFilter" =>
            handleUpdateFilter(json)

          case "sync" =>
            handleSync(json)

          case _ =>
            logger.error(s"[WEBSOCKET] Unknown message type: $messageType")
            sendError(s"Unknown message type: $messageType", requestId = json.obj.get("requestId").map(_.str))
        }
      } catch {
        case e: Exception =>
          logger.error(s"[WEBSOCKET] Error processing message: ${e.getMessage}", e)
          sendError(e.getMessage)
      }
  }

  private def handleSubscribe(json: ujson.Value): Unit = {
    val msg = read[SubscribeMessage](json)

    val result = for {
      obj <- objectManager.subscribe(
        clientId,
        msg.objectId,
        msg.filterType.getOrElse("default"),
        msg.filterParams,
        ongoing = !msg.temporary  // temporary is inverse of ongoing
      )
    } yield obj

    result.unsafeToFuture().foreach {
      case Some(obj) =>
        val response = ServerMessage.FullObject(FullObjectMessage(
          id = obj.id,
          version = obj.version,
          data = obj.data
        ))
        sendMessage(response)

      case None =>
        logger.warn(s"[WEBSOCKET] Subscribed to non-existent object: ${msg.objectId}")
    }
  }

  private def handleUnsubscribe(json: ujson.Value): Unit = {
    val msg = read[UnsubscribeMessage](json)

    objectManager.unsubscribe(clientId, msg.objectId).unsafeRunSync()
    logger.info(s"[WEBSOCKET] Unsubscribed $clientId from ${msg.objectId}")
  }

  private def handleRequest(json: ujson.Value): Unit = {
    val msg = read[RequestMessage](json)

    // Handle business logic request
    val futureResult = chatHandler.process(msg.requestType, msg.payload)

    futureResult.foreach { resultData =>
      // Extract object references from result
      val refs = extractObjectReferences(resultData)

      // Parse options if provided
      val options = msg.options.map(read[RequestOptions](_)).getOrElse(RequestOptions.default)

      // Hydrate references
      val hydratedIO = if (refs.nonEmpty) {
        requestRouter.processRequest(clientId, refs, options)
      } else {
        IO.pure(List.empty[HydratedReference])
      }

      hydratedIO.unsafeToFuture().foreach { hydrated =>
        // Build response
        val response = ServerMessage.Response(ResponseMessage(
          requestId = msg.requestId,
          success = true,
          data = Some(resultData),
          hydrated = hydrated,
          error = None
        ))

        sendMessage(response)

        // Clear temporary subscriptions
        objectManager.clearTemporarySubscriptions(clientId).unsafeRunSync()
      }
    }

    futureResult.failed.foreach { error =>
      logger.error(s"[WEBSOCKET] Request failed: ${error.getMessage}", error)
      sendError(error.getMessage, requestId = Some(msg.requestId))
    }
  }

  private def handleUpdateFilter(json: ujson.Value): Unit = {
    val msg = read[UpdateFilterMessage](json)

    objectManager.updateFilter(
      clientId,
      msg.objectId,
      msg.filterType,
      msg.filterParams
    ).unsafeRunSync()

    logger.info(s"[WEBSOCKET] Updated filter for $clientId on ${msg.objectId}")
  }

  private def handleSync(json: ujson.Value): Unit = {
    val msg = read[SyncMessage](json)

    // Sync message is for reconnection synchronization
    // For now, just log it - full sync implementation would check each state
    logger.info(s"[WEBSOCKET] Sync requested with ${msg.states.length} states")

    // In a full implementation, we would:
    // 1. For each SyncStateItem, check if client's version is current
    // 2. Send deltas or full objects as needed
    // 3. Notify client of any objects they're missing

    // For the demo, we'll just acknowledge
    logger.info(s"[WEBSOCKET] Sync acknowledged (demo mode - full sync not implemented)")
  }

  private def sendMessage(msg: ServerMessage): Unit = {
    val json = write(msg)
    out ! json
  }

  private def sendError(message: String, code: String = "INTERNAL_ERROR", requestId: Option[String] = None): Unit = {
    val errorMsg = ServerMessage.Error(ErrorMessage(
      code = code,
      message = message,
      requestId = requestId
    ))
    sendMessage(errorMsg)
  }

  /** Extract ObjectReferences from ujson result */
  private def extractObjectReferences(json: ujson.Value): List[ObjectReference] = {
    def extractFromValue(value: ujson.Value): List[ObjectReference] = value match {
      case obj: ujson.Obj =>
        // Check if this is an ObjectReference
        val isRef = obj.value.contains("id")

        if (isRef) {
          val id = obj("id").str
          val version = obj.value.get("version").map(_.num.toInt).getOrElse(0)
          val filterType = obj.value.get("filterType")
            .orElse(obj.value.get("filter_type"))
            .map(_.str)
          val subscribe = obj.value.get("subscribe").map(_.bool).getOrElse(false)

          List(ObjectReference(
            id = id,
            version = version,
            filterType = filterType,
            subscribe = subscribe
          ))
        } else {
          // Recursively extract from nested objects
          obj.value.values.flatMap(extractFromValue).toList
        }

      case arr: ujson.Arr =>
        arr.value.flatMap(extractFromValue).toList

      case _ =>
        List.empty
    }

    extractFromValue(json)
  }
}

object ClientActor {
  def props(
      clientId: String,
      out: ActorRef,
      storage: StorageAdapter,
      objectManager: ObjectManager,
      requestRouter: RequestRouter,
      chatHandler: ChatBusinessHandler
  )(implicit ec: ExecutionContext): Props = {
    Props(new ClientActor(clientId, out, storage, objectManager, requestRouter, chatHandler))
  }
}
