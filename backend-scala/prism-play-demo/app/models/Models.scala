package models

import upickle.default._
import java.time.Instant

// Custom serialization for Instant
object Implicits {
  implicit val instantRW: ReadWriter[Instant] = readwriter[String].bimap[Instant](
    instant => instant.toString,
    str => Instant.parse(str)
  )
}

import Implicits._

/** User model */
case class User(
    id: String,
    username: String,
    displayName: String,
    avatarUrl: Option[String] = None,
    createdAt: Instant = Instant.now()
)

object User {
  implicit val rw: ReadWriter[User] = macroRW
}

/** Chat room model */
case class ChatRoom(
    id: String,
    name: String,
    description: Option[String] = None,
    memberIds: List[String] = List.empty,
    createdAt: Instant = Instant.now(),
    createdBy: String
)

object ChatRoom {
  implicit val rw: ReadWriter[ChatRoom] = macroRW
}

/** Chat message model */
case class Message(
    id: String,
    roomId: String,
    userId: String,
    content: String,
    createdAt: Instant = Instant.now(),
    editedAt: Option[Instant] = None
)

object Message {
  implicit val rw: ReadWriter[Message] = macroRW
}

// Request types

/** Request to create a new user */
case class CreateUserRequest(
    username: String,
    displayName: String,
    avatarUrl: Option[String] = None
)

object CreateUserRequest {
  implicit val rw: ReadWriter[CreateUserRequest] = macroRW
}

/** Request to create a new chat room */
case class CreateRoomRequest(
    name: String,
    description: Option[String] = None,
    creatorId: String
)

object CreateRoomRequest {
  implicit val rw: ReadWriter[CreateRoomRequest] = macroRW
}

/** Request to join a chat room */
case class JoinRoomRequest(
    roomId: String,
    userId: String
)

object JoinRoomRequest {
  implicit val rw: ReadWriter[JoinRoomRequest] = macroRW
}

/** Request to send a message */
case class SendMessageRequest(
    roomId: String,
    userId: String,
    content: String
)

object SendMessageRequest {
  implicit val rw: ReadWriter[SendMessageRequest] = macroRW
}

/** Request to get messages from a room */
case class GetRoomMessagesRequest(
    roomId: String,
    limit: Int = 50,
    beforeId: Option[String] = None
)

object GetRoomMessagesRequest {
  implicit val rw: ReadWriter[GetRoomMessagesRequest] = macroRW
}
