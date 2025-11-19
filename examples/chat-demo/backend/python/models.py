"""Chat domain models."""

from datetime import datetime

from pydantic import BaseModel, Field


class User(BaseModel):
    """User model."""

    id: str
    username: str
    display_name: str
    avatar_url: str | None = None
    created_at: datetime = Field(default_factory=datetime.utcnow)


class ChatRoom(BaseModel):
    """Chat room model."""

    id: str
    name: str
    description: str | None = None
    member_ids: list[str] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=datetime.utcnow)
    created_by: str


class Message(BaseModel):
    """Chat message model."""

    id: str
    room_id: str
    user_id: str
    content: str
    created_at: datetime = Field(default_factory=datetime.utcnow)
    edited_at: datetime | None = None


# Request/Response types


class CreateUserRequest(BaseModel):
    """Request to create a new user."""

    username: str
    display_name: str
    avatar_url: str | None = None


class CreateRoomRequest(BaseModel):
    """Request to create a new chat room."""

    name: str
    description: str | None = None
    creator_id: str


class JoinRoomRequest(BaseModel):
    """Request to join a chat room."""

    room_id: str
    user_id: str


class SendMessageRequest(BaseModel):
    """Request to send a message."""

    room_id: str
    user_id: str
    content: str


class GetRoomMessagesRequest(BaseModel):
    """Request to get messages from a room."""

    room_id: str
    limit: int = 50
    before_id: str | None = None
