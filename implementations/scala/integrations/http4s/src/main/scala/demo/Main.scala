package demo

import cats.effect._
import cats.effect.std.Queue
import cats.syntax.all._
import com.comcast.ip4s._
import demo.services.ChatBusinessHandler
import fs2.{Pipe, Stream}
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame._
import org.slf4j.LoggerFactory
import prism.core.Protocol._
import prism.core.Types._
import prism.core.DeltaComputer
import prism.filters.CommonFilters
import prism.server.{ObjectManager, RequestRouter}
import prism.storage.MemoryStorageAdapter
import prism.core.PickleConfig._

import java.util.UUID

object Main extends IOApp {

  private val logger = LoggerFactory.getLogger(getClass)

  /** Client connection state */
  case class ClientConnection(
      clientId: String,
      sendQueue: Queue[IO, WebSocketFrame],
      state: ClientState,
      chatHandler: ChatBusinessHandler,
      requestRouter: RequestRouter,
      objectManager: ObjectManager
  )

  def run(args: List[String]): IO[ExitCode] = {
    // Initialize Prism components
    val initIO = for {
      storage <- MemoryStorageAdapter.create
      filterRegistry = CommonFilters.createDefaultRegistry()
      objectManager <- ObjectManager.create(storage, filterRegistry)
      requestRouter = RequestRouter.create(objectManager)
      chatHandler = new ChatBusinessHandler(storage, objectManager)
    } yield (storage, objectManager, requestRouter, chatHandler)

    initIO.flatMap { case (storage, objectManager, requestRouter, chatHandler) =>
      logger.info("Prism components initialized")

      // HTTP routes
      val httpRoutes = HttpRoutes.of[IO] {
        case GET -> Root / "health" =>
          Ok("""{"status":"ok","service":"prism-http4s-demo"}""")
            .map(_.withContentType(org.http4s.headers.`Content-Type`(org.http4s.MediaType.application.json)))

        case GET -> Root =>
          Ok("""<!DOCTYPE html>
<html>
  <head>
    <title>Prism http4s Demo</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
  </head>
  <body>
    <h1>Prism http4s Demo</h1>
    <p>WebSocket server running on port 8000</p>
    <p>Connect to ws://localhost:8000/ws to use the chat demo</p>
    <p>Health check: <a href="/health">/health</a></p>
  </body>
</html>""").map(_.withContentType(org.http4s.headers.`Content-Type`(org.http4s.MediaType.text.html)))
      }

      // WebSocket route function
      def wsApp(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
        case GET -> Root / "ws" =>
          val clientId = s"client-${UUID.randomUUID().toString.take(12)}"
          logger.info(s"[WEBSOCKET] New WebSocket connection: $clientId")

          for {
            sendQueue <- Queue.unbounded[IO, WebSocketFrame]
            clientState = ClientState.empty
            connection = ClientConnection(clientId, sendQueue, clientState, chatHandler, requestRouter, objectManager)

            // Register client callback for receiving server messages
            _ <- objectManager.registerClient(clientId, (msg: ServerMessage) => {
              val json = write(msg)
              sendQueue.offer(Text(json))
            })

            // Subscribe client to object updates
            _ <- subscribeToObjectUpdates(connection)

            // Send stream from queue
            toClient = Stream.fromQueueUnterminated(sendQueue)

            // Receive stream handler
            fromClient: Pipe[IO, WebSocketFrame, Unit] = stream =>
              stream.evalMap {
                case Text(msg, _) =>
                  handleMessage(connection, msg)
                case Close(_) =>
                  logger.info(s"[WEBSOCKET] Client $clientId disconnected")
                  objectManager.removeClientState(clientId)
                case _ =>
                  IO.unit
              }

            response <- wsb.build(toClient, fromClient)
          } yield response
      }

      def allRoutes(wsb: WebSocketBuilder2[IO]): HttpApp[IO] =
        (httpRoutes <+> wsApp(wsb)).orNotFound

      // Start server
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8000")
        .withHttpWebSocketApp(allRoutes _)
        .build
        .use { server =>
          logger.info(s"Server started at ${server.address}")
          IO.never
        }
        .as(ExitCode.Success)
    }
  }

  /** Handle incoming WebSocket message */
  private def handleMessage(conn: ClientConnection, msg: String): IO[Unit] = {
    // Try to extract requestId early for error handling
    val requestIdOpt = scala.util.Try {
      val json = ujson.read(msg)
      json.obj.get("request_id").map(_.str)
    }.toOption.flatten

    val result = for {
      json <- IO(ujson.read(msg))
      messageType = json("type").str
      _ = logger.info(s"[WEBSOCKET] Received from ${conn.clientId}: $messageType")

      _ <- messageType match {
        case "subscribe" =>
          handleSubscribe(conn, json)

        case "unsubscribe" =>
          handleUnsubscribe(conn, json)

        case "request" =>
          handleRequest(conn, json)

        case "updateFilter" =>
          handleUpdateFilter(conn, json)

        case "sync" =>
          handleSync(conn, json)

        case _ =>
          logger.error(s"[WEBSOCKET] Unknown message type: $messageType")
          sendError(conn, s"Unknown message type: $messageType", requestId = requestIdOpt)
      }
    } yield ()

    result.handleErrorWith { error =>
      logger.error(s"[WEBSOCKET] Message handling failed: ${error.getMessage}", error)
      sendError(conn, error.getMessage, requestId = requestIdOpt)
    }
  }

  private def handleSubscribe(conn: ClientConnection, json: ujson.Value): IO[Unit] = {
    val msg = read[SubscribeMessage](json)

    for {
      objOpt <- conn.objectManager.subscribe(
        conn.clientId,
        msg.objectId,
        msg.filterType.getOrElse("default"),
        msg.filterParams,
        ongoing = !msg.temporary
      )
      _ <- objOpt match {
        case Some(obj) =>
          val response = ServerMessage.FullObject(FullObjectMessage(
            id = obj.id,
            version = obj.version,
            data = obj.data
          ))
          sendMessage(conn, response)

        case None =>
          logger.warn(s"[WEBSOCKET] Subscribed to non-existent object: ${msg.objectId}")
          IO.unit
      }
    } yield ()
  }

  private def handleUnsubscribe(conn: ClientConnection, json: ujson.Value): IO[Unit] = {
    val msg = read[UnsubscribeMessage](json)
    conn.objectManager.unsubscribe(conn.clientId, msg.objectId).as {
      logger.info(s"[WEBSOCKET] Unsubscribed ${conn.clientId} from ${msg.objectId}")
    }
  }

  private def handleRequest(conn: ClientConnection, json: ujson.Value): IO[Unit] = {
    // Try to extract requestId early in case parsing fails later
    val requestIdOpt = scala.util.Try(json.obj.get("request_id").map(_.str)).toOption.flatten

    val result = for {
      msg <- IO(read[RequestMessage](json))

      // Handle business logic request
      resultData <- conn.chatHandler.process(msg.requestType, msg.payload)

      // Extract object references from result
      refs = extractObjectReferences(resultData)

      // Parse options if provided
      options = msg.options.map(read[RequestOptions](_)).getOrElse(RequestOptions.default)

      // Hydrate references
      hydrated <- if (refs.nonEmpty) {
        conn.requestRouter.processRequest(conn.clientId, refs, options)
      } else {
        IO.pure(List.empty[HydratedReference])
      }

      // Build response
      response = ServerMessage.Response(ResponseMessage(
        requestId = msg.requestId,
        success = true,
        data = Some(resultData),
        hydrated = hydrated,
        error = None
      ))

      _ <- sendMessage(conn, response)

      // Clear temporary subscriptions
      _ <- conn.objectManager.clearTemporarySubscriptions(conn.clientId)
    } yield ()

    result.handleErrorWith { error =>
      logger.error(s"[WEBSOCKET] Request failed: ${error.getMessage}", error)
      sendError(conn, error.getMessage, requestId = requestIdOpt)
    }
  }

  private def handleUpdateFilter(conn: ClientConnection, json: ujson.Value): IO[Unit] = {
    val msg = read[UpdateFilterMessage](json)
    conn.objectManager.updateFilter(
      conn.clientId,
      msg.objectId,
      msg.filterType,
      msg.filterParams
    ).as {
      logger.info(s"[WEBSOCKET] Updated filter for ${conn.clientId} on ${msg.objectId}")
    }
  }

  private def handleSync(conn: ClientConnection, json: ujson.Value): IO[Unit] = {
    val msg = read[SyncMessage](json)
    logger.info(s"[WEBSOCKET] Sync requested with ${msg.states.length} states")

    for {
      // Get server's view of client state
      clientState <- conn.objectManager.getClientState(conn.clientId)

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
              conn.objectManager.getObject(syncItem.id, Some(serverSub.currentVersion)).flatMap {
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
                    sendMessage(conn, response)
                  } else {
                    // Same filter, can send delta
                    conn.objectManager.getObject(syncItem.id, Some(syncItem.version)).flatMap {
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
                          sendMessage(conn, response)
                        } else {
                          IO.unit
                        }
                      case None =>
                        // Old version not found, send full object
                        logger.info(s"[WEBSOCKET] Sync: Old version not found for ${syncItem.id}, sending full object")
                        val response = ServerMessage.FullObject(FullObjectMessage(
                          id = obj.id,
                          version = obj.version,
                          data = obj.data
                        ))
                        sendMessage(conn, response)
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
              conn.objectManager.getObject(syncItem.id).flatMap {
                case Some(obj) =>
                  val response = ServerMessage.FullObject(FullObjectMessage(
                    id = obj.id,
                    version = obj.version,
                    data = obj.data
                  ))
                  sendMessage(conn, response)
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
            conn.objectManager.subscribe(
              conn.clientId,
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
                sendMessage(conn, response)
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
      _ <- serverOnlySubscriptions.toList.traverse { case (objectId, subscription) =>
        logger.info(s"[WEBSOCKET] Sync: Client missing subscription to $objectId, sending full object")
        conn.objectManager.getObject(objectId).flatMap {
          case Some(obj) =>
            val response = ServerMessage.FullObject(FullObjectMessage(
              id = obj.id,
              version = obj.version,
              data = obj.data
            ))
            sendMessage(conn, response)
          case None =>
            logger.warn(s"[WEBSOCKET] Sync: Object $objectId not found despite server subscription")
            IO.unit
        }
      }

      _ = logger.info(s"[WEBSOCKET] Sync completed for ${conn.clientId}")
    } yield ()
  }

  /** Subscribe client to object update notifications */
  private def subscribeToObjectUpdates(conn: ClientConnection): IO[Unit] = {
    val _ = conn // Unused for now, but kept for future use
    // This would set up a listener for object updates
    // For now, we'll rely on the ObjectManager's notification system
    IO.unit
  }

  /** Send a server message to the client */
  private def sendMessage(conn: ClientConnection, msg: ServerMessage): IO[Unit] = {
    val json = write(msg)
    conn.sendQueue.offer(Text(json))
  }

  /** Send an error message to the client */
  private def sendError(conn: ClientConnection, message: String, code: String = "INTERNAL_ERROR", requestId: Option[String] = None): IO[Unit] = {
    val errorMsg = ServerMessage.Error(ErrorMessage(
      code = code,
      message = message,
      requestId = requestId
    ))
    sendMessage(conn, errorMsg)
  }

  /** Extract ObjectReferences from ujson result */
  private def extractObjectReferences(json: ujson.Value): List[ObjectReference] = {
    def extractFromValue(value: ujson.Value): List[ObjectReference] = value match {
      case obj: ujson.Obj =>
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
