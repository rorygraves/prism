"""Chat business logic handler."""

import uuid
from typing import Any

from chat_demo.models import (
    ChatRoom,
    CreateRoomRequest,
    CreateUserRequest,
    GetRoomMessagesRequest,
    JoinRoomRequest,
    Message,
    SendMessageRequest,
    User,
)
from prism.core.types import ObjectReference, PrismObject
from prism.server.object_manager import PrismObjectManager
from prism.server.request_router import BusinessHandler
from prism.storage.base import StorageAdapter


class ChatBusinessHandler(BusinessHandler):
    """Handles chat-specific business logic."""

    def __init__(self, storage: StorageAdapter, object_manager: PrismObjectManager):
        """Initialize chat handler.

        Args:
            storage: Storage adapter for persisting objects
            object_manager: Prism object manager
        """
        self.storage = storage
        self.object_manager = object_manager

        # In-memory indexes for demo (in production, use database queries)
        self.users_by_username: dict[str, str] = {}  # username -> user_id
        self.rooms_by_name: dict[str, str] = {}  # room_name -> room_id
        self.room_messages: dict[str, list[str]] = {}  # room_id -> list[message_id]

    async def process(self, request_type: str, payload: dict[str, Any]) -> Any:
        """Process chat request.

        Args:
            request_type: Type of request
            payload: Request payload

        Returns:
            Response data with ObjectReferences
        """
        if request_type == "createUser":
            return await self.create_user(CreateUserRequest(**payload))
        elif request_type == "createRoom":
            return await self.create_room(CreateRoomRequest(**payload))
        elif request_type == "joinRoom":
            return await self.join_room(JoinRoomRequest(**payload))
        elif request_type == "sendMessage":
            return await self.send_message(SendMessageRequest(**payload))
        elif request_type == "getRoomMessages":
            return await self.get_room_messages(GetRoomMessagesRequest(**payload))
        elif request_type == "getUser":
            return await self.get_user(payload["user_id"])
        elif request_type == "getRoom":
            return await self.get_room(payload["room_id"])
        else:
            raise ValueError(f"Unknown request type: {request_type}")

    async def create_user(self, req: CreateUserRequest) -> dict[str, Any]:
        """Create a new user.

        Args:
            req: Create user request

        Returns:
            User object reference
        """
        user_id = f"user-{uuid.uuid4().hex[:12]}"

        user = User(
            id=user_id,
            username=req.username,
            display_name=req.display_name,
            avatar_url=req.avatar_url,
        )

        # Save as Prism object
        obj = PrismObject(id=user_id, version=1, data=user.model_dump(mode="json"))
        await self.storage.save(obj)

        # Update index
        self.users_by_username[user.username] = user_id

        # Return object reference
        return {
            "user": ObjectReference(id=user_id, version=1, filter_type="default").model_dump()
        }

    async def create_room(self, req: CreateRoomRequest) -> dict[str, Any]:
        """Create a new chat room.

        Args:
            req: Create room request

        Returns:
            Room object reference
        """
        room_id = f"room-{uuid.uuid4().hex[:12]}"

        room = ChatRoom(
            id=room_id,
            name=req.name,
            description=req.description,
            member_ids=[req.creator_id],
            created_by=req.creator_id,
        )

        # Save as Prism object
        obj = PrismObject(id=room_id, version=1, data=room.model_dump(mode="json"))
        await self.storage.save(obj)

        # Update indexes
        self.rooms_by_name[room.name] = room_id
        self.room_messages[room_id] = []

        # Return room and creator references
        return {
            "room": ObjectReference(id=room_id, version=1, filter_type="default").model_dump(),
            "creator": ObjectReference(
                id=req.creator_id, version=1, filter_type="default"
            ).model_dump(),
        }

    async def join_room(self, req: JoinRoomRequest) -> dict[str, Any]:
        """Join a chat room.

        Args:
            req: Join room request

        Returns:
            Updated room reference
        """
        # Get current room
        room_obj = await self.storage.get_current(req.room_id)
        if not room_obj:
            raise ValueError(f"Room {req.room_id} not found")

        room = ChatRoom(**room_obj.data)

        # Add user if not already a member
        if req.user_id not in room.member_ids:
            room.member_ids.append(req.user_id)

            # Save new version
            new_obj = PrismObject(
                id=room_obj.id, version=room_obj.version + 1, data=room.model_dump(mode="json")
            )
            await self.storage.save(new_obj)

            # Notify subscribers
            await self.object_manager.notify_object_updated(new_obj)

        return {
            "room": ObjectReference(
                id=req.room_id, version=room_obj.version + 1, filter_type="default"
            ).model_dump()
        }

    async def send_message(self, req: SendMessageRequest) -> dict[str, Any]:
        """Send a message to a chat room.

        Args:
            req: Send message request

        Returns:
            Message object reference
        """
        message_id = f"msg-{uuid.uuid4().hex[:12]}"

        message = Message(
            id=message_id, room_id=req.room_id, user_id=req.user_id, content=req.content
        )

        # Save as Prism object
        obj = PrismObject(id=message_id, version=1, data=message.model_dump(mode="json"))
        await self.storage.save(obj)

        # Update room messages index
        if req.room_id not in self.room_messages:
            self.room_messages[req.room_id] = []
        self.room_messages[req.room_id].append(message_id)

        # Notify subscribers about new message
        await self.object_manager.notify_object_updated(obj)

        # Return message reference with user reference
        return {
            "message": ObjectReference(
                id=message_id, version=1, filter_type="default", subscribe=True
            ).model_dump(),
            "user": ObjectReference(
                id=req.user_id, version=1, filter_type="default"
            ).model_dump(),
        }

    async def get_room_messages(self, req: GetRoomMessagesRequest) -> dict[str, Any]:
        """Get messages from a chat room.

        Args:
            req: Get messages request

        Returns:
            List of message references
        """
        message_ids = self.room_messages.get(req.room_id, [])

        # Get last N messages
        recent_ids = message_ids[-req.limit :]

        # Return as object references
        messages = [
            ObjectReference(id=msg_id, version=1, filter_type="default", subscribe=True).model_dump()
            for msg_id in recent_ids
        ]

        return {"messages": messages}

    async def get_user(self, user_id: str) -> dict[str, Any]:
        """Get a user by ID.

        Args:
            user_id: User ID

        Returns:
            User object reference
        """
        user_obj = await self.storage.get_current(user_id)
        if not user_obj:
            raise ValueError(f"User {user_id} not found")

        return {
            "user": ObjectReference(
                id=user_id, version=user_obj.version, filter_type="default"
            ).model_dump()
        }

    async def get_room(self, room_id: str) -> dict[str, Any]:
        """Get a room by ID.

        Args:
            room_id: Room ID

        Returns:
            Room object reference with member references
        """
        room_obj = await self.storage.get_current(room_id)
        if not room_obj:
            raise ValueError(f"Room {room_id} not found")

        room = ChatRoom(**room_obj.data)

        # Return room reference with member references
        return {
            "room": ObjectReference(
                id=room_id, version=room_obj.version, filter_type="default"
            ).model_dump(),
            "members": [
                ObjectReference(id=member_id, version=1, filter_type="default").model_dump()
                for member_id in room.member_ids
            ],
        }
