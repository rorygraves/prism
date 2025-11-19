package controllers

import actors.ClientActor
import akka.actor.ActorSystem
import akka.stream.Materializer
import cats.effect.unsafe.implicits.global
import play.api.Logger
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import prism.filters.CommonFilters
import prism.server.{ObjectManager, RequestRouter}
import prism.storage.MemoryStorageAdapter
import services.ChatBusinessHandler

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ChatController @Inject()(
    cc: ControllerComponents
)(implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext)
    extends AbstractController(cc) {

  private val logger = Logger(getClass)

  // Initialize Prism components
  private val (storage, objectManager, requestRouter, chatHandler) = {
    val initIO = for {
      storage <- MemoryStorageAdapter.create
      filterRegistry = CommonFilters.createDefaultRegistry()
      objectManager <- ObjectManager.create(storage, filterRegistry)
      requestRouter = RequestRouter.create(objectManager)
      chatHandler = ChatBusinessHandler(storage, objectManager)
    } yield (storage, objectManager, requestRouter, chatHandler)

    initIO.unsafeRunSync()
  }

  logger.info("ChatController initialized with Prism components")

  /** WebSocket endpoint */
  def ws: WebSocket = WebSocket.accept[String, String] { request =>
    val clientId = s"client-${UUID.randomUUID().toString.take(12)}"
    logger.info(s"[WEBSOCKET] New WebSocket connection: $clientId")

    ActorFlow.actorRef { out =>
      ClientActor.props(clientId, out, storage, objectManager, requestRouter, chatHandler)
    }
  }

  /** Health check endpoint */
  def health: Action[AnyContent] = Action { request =>
    Ok(play.api.libs.json.Json.obj(
      "status" -> "ok",
      "service" -> "prism-play-demo"
    ))
  }

  /** Index page - serves frontend */
  def index: Action[AnyContent] = Action { request =>
    Ok(views.html.index())
  }
}
