package actors

import akka.actor._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import play.api.Logger
import prism.core.Protocol._
import prism.core.Types._
import prism.core.DeltaComputer
import prism.core.PickleConfig.{read, write}
import prism.server.{ObjectManager, RequestRouter}
import prism.storage.StorageAdapter
import services.ChatBusinessHandler
import upickle.default.ReadWriter

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
    // Register client callback for receiving server messages
    objectManager.registerClient(clientId, (msg: ServerMessage) => {
      val json = write(msg)
      out ! json
      IO.unit
    }).unsafeRunSync()
  }

  override def postStop(): Unit = {
    logger.info(s"[WEBSOCKET] Client $clientId disconnected")
    // Clean up client state
    objectManager.removeClientState(clientId).unsafeRunSync()
  }

  def receive: Receive = {
    case msg: String =>
      // Try to extract requestId early for error handling
      val requestIdOpt = scala.util.Try {
        val json = ujson.read(msg)
        json.obj.get("request_id").map(_.str)
      }.toOption.flatten

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
            sendError(s"Unknown message type: $messageType", requestId = json.obj.get("request_id").map(_.str))
        }
      } catch {
        case e: Exception =>
          logger.error(s"[WEBSOCKET] Error processing message: ${e.getMessage}", e)
          sendError(e.getMessage, requestId = requestIdOpt)
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
    logger.info(s"[WEBSOCKET] Sync requested with ${msg.states.length} states")

    // Get server's view of client state
    val syncIO = for {
      clientState <- objectManager.getClientState(clientId)

      // Create map of client's reported state for quick lookup
      clientStateMap = msg.states.map(s => s.id -> s).toMap

      // Process each object the client reports having
      _ <- msg.states.traverse { syncItem =>
        clientState.subscriptions.get(syncItem.id) match {
          case Some(serverSub) =>
            // Client has subscription that server knows about - check version
            if (syncItem.version < serverSub.currentVersion) {
              // Client is behind, send update
              logger.info(s"[WEBSOCKET] Sync: Client behind on ${syncItem.id} (client: ${syncItem.version}, server: ${serverSub.currentVersion})")
              objectManager.getObject(syncItem.id, Some(serverSub.currentVersion)).flatMap {
                case Some(obj) =>
                  // Check if filter changed
                  if (syncItem.filterType != serverSub.filterType) {
                    // Filter changed, send full object
                    logger.info(s"[WEBSOCKET] Sync: Filter changed for ${syncItem.id}, sending full object")
                    val response = ServerMessage.FullObject(FullObjectMessage(
                      id = obj.id,
                      version = obj.version,
                      data = obj.data
                    ))
                    sendMessage(response)
                    IO.unit
                  } else {
                    // Same filter, can send delta
                    objectManager.getObject(syncItem.id, Some(syncItem.version)).flatMap {
                      case Some(oldObj) =>
                        val delta = DeltaComputer.computeDelta(oldObj, obj)
                        if (delta.patches.nonEmpty) {
                          logger.info(s"[WEBSOCKET] Sync: Sending delta for ${syncItem.id}")
                          val response = ServerMessage.Delta(DeltaMessage(
                            id = obj.id,
                            fromVersion = oldObj.version,
                            toVersion = obj.version,
                            patches = delta.patches,
                            filterType = Some(serverSub.filterType)
                          ))
                          sendMessage(response)
                        }
                        IO.unit
                      case None =>
                        // Old version not found, send full object
                        logger.info(s"[WEBSOCKET] Sync: Old version not found for ${syncItem.id}, sending full object")
                        val response = ServerMessage.FullObject(FullObjectMessage(
                          id = obj.id,
                          version = obj.version,
                          data = obj.data
                        ))
                        sendMessage(response)
                        IO.unit
                    }
                  }
                case None =>
                  logger.warn(s"[WEBSOCKET] Sync: Object ${syncItem.id} not found despite subscription")
                  IO.unit
              }
            } else if (syncItem.version > serverSub.currentVersion) {
              // Client is ahead (shouldn't happen normally)
              logger.warn(s"[WEBSOCKET] Sync: Client ahead on ${syncItem.id} (client: ${syncItem.version}, server: ${serverSub.currentVersion})")
              // Send full object to resync
              objectManager.getObject(syncItem.id).flatMap {
                case Some(obj) =>
                  val response = ServerMessage.FullObject(FullObjectMessage(
                    id = obj.id,
                    version = obj.version,
                    data = obj.data
                  ))
                  sendMessage(response)
                  IO.unit
                case None =>
                  IO.unit
              }
            } else {
              // Versions match, no update needed
              IO.unit
            }

          case None =>
            // Client has subscription that server doesn't know about
            // This happens after reconnection - re-subscribe
            logger.info(s"[WEBSOCKET] Sync: Re-subscribing to ${syncItem.id}")
            objectManager.subscribe(
              clientId,
              syncItem.id,
              syncItem.filterType,
              None,
              ongoing = true  // Assume ongoing subscription
            ).flatMap {
              case Some(obj) =>
                val response = ServerMessage.FullObject(FullObjectMessage(
                  id = obj.id,
                  version = obj.version,
                  data = obj.data
                ))
                sendMessage(response)
                IO.unit
              case None =>
                logger.warn(s"[WEBSOCKET] Sync: Failed to subscribe to ${syncItem.id}")
                IO.unit
            }
        }
      }

      // Find subscriptions server has that client doesn't
      serverOnlySubscriptions = clientState.subscriptions.filter {
        case (objectId, _) => !clientStateMap.contains(objectId)
      }

      // Send full objects for subscriptions client is missing
      _ <- serverOnlySubscriptions.toList.traverse { case (objectId, _) =>
        logger.info(s"[WEBSOCKET] Sync: Client missing subscription to $objectId, sending full object")
        objectManager.getObject(objectId).flatMap {
          case Some(obj) =>
            val response = ServerMessage.FullObject(FullObjectMessage(
              id = obj.id,
              version = obj.version,
              data = obj.data
            ))
            sendMessage(response)
            IO.unit
          case None =>
            logger.warn(s"[WEBSOCKET] Sync: Object $objectId not found despite server subscription")
            IO.unit
        }
      }

      _ = logger.info(s"[WEBSOCKET] Sync completed for $clientId")
    } yield ()

    // Execute the sync IO
    syncIO.unsafeRunSync()
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
