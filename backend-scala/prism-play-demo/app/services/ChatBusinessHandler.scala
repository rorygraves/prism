package services

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import models._
import play.api.Logger
import prism.core.Types._
import prism.server.ObjectManager
import prism.storage.StorageAdapter
import upickle.default._

import java.util.UUID
import scala.concurrent.Future
import scala.collection.concurrent.TrieMap

/** Handles chat-specific business logic */
class ChatBusinessHandler(
    storage: StorageAdapter,
    objectManager: ObjectManager
) {

  private val logger = Logger(getClass)

  // In-memory indexes for demo (in production, use database queries)
  private val usersByUsername = TrieMap.empty[String, String]  // username -> user_id
  private val roomsByName = TrieMap.empty[String, String]      // room_name -> room_id
  private val roomMessages = TrieMap.empty[String, List[String]] // room_id -> list[message_id]

  // Special singleton object ID for the global room list
  private val ROOM_LIST_ID = "global-room-list"

  logger.info("ChatBusinessHandler initialized")

  /** Helper to convert ObjectReference to ujson */
  private def refToJson(ref: ObjectReference): ujson.Obj = {
    val base = ujson.Obj("id" -> ref.id, "version" -> ref.version)
    ref.filterType.foreach { ft => base("filterType") = ft }
    if (ref.subscribe) base("subscribe") = true
    base
  }

  /** Process chat request */
  def process(requestType: String, payload: ujson.Value): Future[ujson.Value] = {
    logger.info(s"[PROCESS] Received request: $requestType")

    val resultIO = requestType match {
      case "createUser" =>
        val req = read[CreateUserRequest](payload)
        createUser(req)

      case "createRoom" =>
        logger.info(s"[PROCESS] createRoom payload: ${payload.render()}")
        val req = read[CreateRoomRequest](payload)
        logger.info(s"[PROCESS] createRoom parsed: $req")
        createRoom(req)

      case "joinRoom" =>
        val req = read[JoinRoomRequest](payload)
        joinRoom(req)

      case "sendMessage" =>
        val req = read[SendMessageRequest](payload)
        sendMessage(req)

      case "getRoomMessages" =>
        val req = read[GetRoomMessagesRequest](payload)
        getRoomMessages(req)

      case "getUser" =>
        val userId = payload("user_id").str
        getUser(userId)

      case "getRoom" =>
        val roomId = payload("room_id").str
        getRoom(roomId)

      case "listRooms" =>
        listRooms()

      case _ =>
        logger.error(s"[PROCESS] Unknown request type: $requestType")
        IO.raiseError(new IllegalArgumentException(s"Unknown request type: $requestType"))
    }

    // Convert IO to Future
    resultIO.unsafeToFuture()
  }

  /** Create a new user */
  private def createUser(req: CreateUserRequest): IO[ujson.Value] = {
    logger.info(s"[CREATE_USER] Creating user: ${req.username}")
    val userId = s"user-${UUID.randomUUID().toString.take(12)}"

    val user = User(
      id = userId,
      username = req.username,
      displayName = req.displayName,
      avatarUrl = req.avatarUrl
    )

    logger.info(s"[CREATE_USER] Generated user ID: $userId")

    // Convert to ujson for storage
    val userData = writeJs(user).obj

    // Save as Prism object
    val obj = PrismObject(userId, 1, userData)

    for {
      _ <- storage.save(obj)
      _ = logger.info(s"[CREATE_USER] User saved to storage: $userId")
      _ = usersByUsername.put(user.username, userId)
      _ = logger.info(s"[CREATE_USER] Updated users_by_username index")
    } yield {
      ujson.Obj("user" -> refToJson(ObjectReference(id = userId, version = 1)))
    }
  }

  /** Create a new chat room */
  private def createRoom(req: CreateRoomRequest): IO[ujson.Value] = {
    logger.info(s"[CREATE_ROOM] Creating room: ${req.name}, creator: ${req.creatorId}")
    val roomId = s"room-${UUID.randomUUID().toString.take(12)}"

    val room = ChatRoom(
      id = roomId,
      name = req.name,
      description = req.description,
      memberIds = List(req.creatorId),
      createdBy = req.creatorId
    )

    logger.info(s"[CREATE_ROOM] Generated room ID: $roomId, initial members: ${room.memberIds}")

    // Convert to ujson for storage
    val roomData = writeJs(room).obj

    // Save as Prism object
    val obj = PrismObject(roomId, 1, roomData)

    for {
      _ <- storage.save(obj)
      _ = logger.info(s"[CREATE_ROOM] Room saved to storage: $roomId")
      _ = roomsByName.put(room.name, roomId)
      _ = roomMessages.put(roomId, List.empty)
      _ = logger.info(s"[CREATE_ROOM] Updated indexes")
      // Update global room list and notify all subscribers
      _ <- updateRoomList()
    } yield {
      ujson.Obj(
        "room" -> refToJson(ObjectReference(id = roomId, version = 1)),
        "creator" -> refToJson(ObjectReference(id = req.creatorId, version = 1))
      )
    }
  }

  /** Join a chat room */
  private def joinRoom(req: JoinRoomRequest): IO[ujson.Value] = {
    logger.info(s"[JOIN_ROOM] User ${req.userId} joining room ${req.roomId}")

    for {
      roomObjOpt <- storage.getCurrent(req.roomId)
      roomObj <- roomObjOpt match {
        case Some(obj) => IO.pure(obj)
        case None =>
          logger.error(s"[JOIN_ROOM] Room ${req.roomId} not found")
          IO.raiseError(new IllegalArgumentException(s"Room ${req.roomId} not found"))
      }

      // Parse room from ujson
      room = read[ChatRoom](roomObj.data)
      _ = logger.info(s"[JOIN_ROOM] Current room members: ${room.memberIds}")

      result <- if (!room.memberIds.contains(req.userId)) {
        val updatedRoom = room.copy(memberIds = room.memberIds :+ req.userId)
        logger.info(s"[JOIN_ROOM] Added user, new members: ${updatedRoom.memberIds}")

        // Convert back to ujson
        val updatedData = writeJs(updatedRoom).obj

        // Save new version
        val newObj = PrismObject(roomObj.id, roomObj.version + 1, updatedData)

        for {
          _ <- storage.save(newObj)
          _ = logger.info(s"[JOIN_ROOM] Saved new room version: ${newObj.version}")
          // Notify subscribers
          _ <- objectManager.notifyObjectUpdated(newObj)
          _ = logger.info(s"[JOIN_ROOM] Notified subscribers")
        } yield ujson.Obj("room" -> refToJson(ObjectReference(id = req.roomId, version = newObj.version)))
      } else {
        logger.info(s"[JOIN_ROOM] User already a member, no changes made")
        IO.pure(ujson.Obj("room" -> refToJson(ObjectReference(id = req.roomId, version = roomObj.version))))
      }
    } yield result
  }

  /** Send a message to a chat room */
  private def sendMessage(req: SendMessageRequest): IO[ujson.Value] = {
    logger.info(s"[SEND_MESSAGE] Sending message to room ${req.roomId} from user ${req.userId}")
    val messageId = s"msg-${UUID.randomUUID().toString.take(12)}"

    val message = Message(
      id = messageId,
      roomId = req.roomId,
      userId = req.userId,
      content = req.content
    )

    logger.info(s"[SEND_MESSAGE] Generated message ID: $messageId")

    // Convert to ujson for storage
    val messageData = writeJs(message).obj

    // Save as Prism object
    val obj = PrismObject(messageId, 1, messageData)

    for {
      _ <- storage.save(obj)
      _ = logger.info(s"[SEND_MESSAGE] Message saved to storage: $messageId")
      _ = {
        val currentMessages = roomMessages.getOrElse(req.roomId, List.empty)
        roomMessages.put(req.roomId, currentMessages :+ messageId)
        logger.info(s"[SEND_MESSAGE] Updated room_messages")
      }
      // Notify subscribers about new message
      _ <- objectManager.notifyObjectUpdated(obj)
      _ = logger.info(s"[SEND_MESSAGE] Notified subscribers about new message")
    } yield {
      ujson.Obj(
        "message" -> refToJson(ObjectReference(id = messageId, version = 1, subscribe = true)),
        "user" -> refToJson(ObjectReference(id = req.userId, version = 1))
      )
    }
  }

  /** Get messages from a chat room */
  private def getRoomMessages(req: GetRoomMessagesRequest): IO[ujson.Value] = {
    logger.info(s"[GET_ROOM_MESSAGES] Getting messages for room ${req.roomId}, limit: ${req.limit}")

    val messageIds = roomMessages.getOrElse(req.roomId, List.empty)
    logger.info(s"[GET_ROOM_MESSAGES] Total messages in room: ${messageIds.length}")

    // Get last N messages
    val recentIds = messageIds.takeRight(req.limit)
    logger.info(s"[GET_ROOM_MESSAGES] Returning ${recentIds.length} recent messages")

    // Return as object references
    val messages = ujson.Arr(
      recentIds.map { msgId =>
        refToJson(ObjectReference(id = msgId, version = 1, subscribe = true))
      }: _*
    )

    IO.pure(ujson.Obj("messages" -> messages))
  }

  /** Get a user by ID */
  private def getUser(userId: String): IO[ujson.Value] = {
    logger.info(s"[GET_USER] Getting user: $userId")

    for {
      userObjOpt <- storage.getCurrent(userId)
      userObj <- userObjOpt match {
        case Some(obj) => IO.pure(obj)
        case None =>
          logger.error(s"[GET_USER] User $userId not found")
          IO.raiseError(new IllegalArgumentException(s"User $userId not found"))
      }
    } yield {
      logger.info(s"[GET_USER] Found user: $userId, version: ${userObj.version}")
      ujson.Obj("user" -> refToJson(ObjectReference(id = userId, version = userObj.version)))
    }
  }

  /** Get a room by ID */
  private def getRoom(roomId: String): IO[ujson.Value] = {
    logger.info(s"[GET_ROOM] Getting room: $roomId")

    for {
      roomObjOpt <- storage.getCurrent(roomId)
      roomObj <- roomObjOpt match {
        case Some(obj) => IO.pure(obj)
        case None =>
          logger.error(s"[GET_ROOM] Room $roomId not found")
          IO.raiseError(new IllegalArgumentException(s"Room $roomId not found"))
      }

      // Parse room from ujson
      room = read[ChatRoom](roomObj.data)
      _ = logger.info(s"[GET_ROOM] Found room: $roomId, version: ${roomObj.version}, members: ${room.memberIds}")
    } yield {
      val members = ujson.Arr(
        room.memberIds.map { memberId =>
          refToJson(ObjectReference(id = memberId, version = 1))
        }: _*
      )

      ujson.Obj(
        "room" -> refToJson(ObjectReference(id = roomId, version = roomObj.version)),
        "members" -> members
      )
    }
  }

  /** Update the global room list object with room IDs and notify subscribers */
  private def updateRoomList(): IO[Unit] = {
    logger.info(s"[UPDATE_ROOM_LIST] Updating global room list")

    val roomIds = roomsByName.values.toList
    logger.info(s"[UPDATE_ROOM_LIST] Room IDs: $roomIds")

    for {
      roomListObjOpt <- storage.getCurrent(ROOM_LIST_ID)

      newVersion = roomListObjOpt.map(_.version + 1).getOrElse(1)
      _ = logger.info(s"[UPDATE_ROOM_LIST] New version: $newVersion")

      // Store just the room IDs
      roomListData = ujson.Obj("room_ids" -> ujson.Arr(roomIds.map(ujson.Str(_)): _*))
      newObj = PrismObject(ROOM_LIST_ID, newVersion, roomListData)

      _ <- storage.save(newObj)
      _ = logger.info(s"[UPDATE_ROOM_LIST] Saved room list, version $newVersion, ${roomIds.length} room IDs")

      // Notify all subscribers
      _ <- objectManager.notifyObjectUpdated(newObj)
      _ = logger.info(s"[UPDATE_ROOM_LIST] Notified all subscribers about room list update")
    } yield ()
  }

  /** List all available chat rooms */
  private def listRooms(): IO[ujson.Value] = {
    logger.info(s"[LIST_ROOMS] Returning room list object reference")

    for {
      // Ensure room list object exists
      roomListObjOpt <- storage.getCurrent(ROOM_LIST_ID)
      _ <- if (roomListObjOpt.isEmpty) updateRoomList() else IO.unit
      roomListObj <- storage.getCurrent(ROOM_LIST_ID)
    } yield {
      val version = roomListObj.map(_.version).getOrElse(1)
      ujson.Obj(
        "roomList" -> refToJson(ObjectReference(
          id = ROOM_LIST_ID,
          version = version,
          subscribe = true
        ))
      )
    }
  }
}

object ChatBusinessHandler {
  def apply(storage: StorageAdapter, objectManager: ObjectManager): ChatBusinessHandler = {
    new ChatBusinessHandler(storage, objectManager)
  }
}
