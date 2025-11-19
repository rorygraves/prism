package models

import upickle.default._
import java.time.Instant

// Custom serialization for Instant
object Implicits {
  implicit val instantRW: ReadWriter[Instant] = readwriter[String].bimap[Instant](
    instant => instant.toString,
    str => Instant.parse(str)
  )

  // Helper to convert camelCase to snake_case
  def toSnakeCase(s: String): String = {
    s.replaceAll("([A-Z])", "_$1").toLowerCase
  }
}

import Implicits._

/** User model */
case class User(
    id: String,
    username: String,
    @upickle.implicits.key("display_name")
    displayName: String,
    @upickle.implicits.key("avatar_url")
    avatarUrl: Option[String] = None,
    @upickle.implicits.key("created_at")
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
    @upickle.implicits.key("member_ids")
    memberIds: List[String] = List.empty,
    @upickle.implicits.key("created_at")
    createdAt: Instant = Instant.now(),
    @upickle.implicits.key("created_by")
    createdBy: String
)

object ChatRoom {
  implicit val rw: ReadWriter[ChatRoom] = macroRW
}

/** Chat message model */
case class Message(
    id: String,
    @upickle.implicits.key("room_id")
    roomId: String,
    @upickle.implicits.key("user_id")
    userId: String,
    content: String,
    @upickle.implicits.key("created_at")
    createdAt: Instant = Instant.now(),
    @upickle.implicits.key("edited_at")
    editedAt: Option[Instant] = None
)

object Message {
  implicit val rw: ReadWriter[Message] = macroRW
}

// Request types

/** Request to create a new user */
case class CreateUserRequest(
    username: String,
    @upickle.implicits.key("display_name")
    displayName: String,
    @upickle.implicits.key("avatar_url")
    avatarUrl: Option[String] = None
)

object CreateUserRequest {
  implicit val rw: ReadWriter[CreateUserRequest] = macroRW
}

/** Request to create a new chat room */
case class CreateRoomRequest(
    name: String,
    description: Option[String] = None,
    @upickle.implicits.key("creator_id")
    creatorId: String
)

object CreateRoomRequest {
  implicit val rw: ReadWriter[CreateRoomRequest] = macroRW
}

/** Request to join a chat room */
case class JoinRoomRequest(
    @upickle.implicits.key("room_id")
    roomId: String,
    @upickle.implicits.key("user_id")
    userId: String
)

object JoinRoomRequest {
  implicit val rw: ReadWriter[JoinRoomRequest] = macroRW
}

/** Request to send a message */
case class SendMessageRequest(
    @upickle.implicits.key("room_id")
    roomId: String,
    @upickle.implicits.key("user_id")
    userId: String,
    content: String
)

object SendMessageRequest {
  implicit val rw: ReadWriter[SendMessageRequest] = macroRW
}

/** Request to get messages from a room */
case class GetRoomMessagesRequest(
    @upickle.implicits.key("room_id")
    roomId: String,
    limit: Int = 50,
    @upickle.implicits.key("before_id")
    beforeId: Option[String] = None
)

object GetRoomMessagesRequest {
  implicit val rw: ReadWriter[GetRoomMessagesRequest] = macroRW
}
