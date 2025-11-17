"""Chat business logic handler."""

import logging
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
        self.logger = logging.getLogger(__name__)

        # In-memory indexes for demo (in production, use database queries)
        self.users_by_username: dict[str, str] = {}  # username -> user_id
        self.rooms_by_name: dict[str, str] = {}  # room_name -> room_id
        self.room_messages: dict[str, list[str]] = {}  # room_id -> list[message_id]

        # Special singleton object ID for the global room list
        self.ROOM_LIST_ID = "global-room-list"

        self.logger.info("ChatBusinessHandler initialized")

    async def process(self, request_type: str, payload: dict[str, Any]) -> Any:
        """Process chat request.

        Args:
            request_type: Type of request
            payload: Request payload

        Returns:
            Response data with ObjectReferences
        """
        self.logger.info(f"[PROCESS] Received request: {request_type}, payload: {payload}")

        if request_type == "createUser":
            result = await self.create_user(CreateUserRequest(**payload))
            self.logger.info(f"[PROCESS] createUser completed, result: {result}")
            return result
        elif request_type == "createRoom":
            result = await self.create_room(CreateRoomRequest(**payload))
            self.logger.info(f"[PROCESS] createRoom completed, result: {result}")
            return result
        elif request_type == "joinRoom":
            result = await self.join_room(JoinRoomRequest(**payload))
            self.logger.info(f"[PROCESS] joinRoom completed, result: {result}")
            return result
        elif request_type == "sendMessage":
            result = await self.send_message(SendMessageRequest(**payload))
            self.logger.info(f"[PROCESS] sendMessage completed, result: {result}")
            return result
        elif request_type == "getRoomMessages":
            result = await self.get_room_messages(GetRoomMessagesRequest(**payload))
            self.logger.info(f"[PROCESS] getRoomMessages completed, result: {result}")
            return result
        elif request_type == "getUser":
            result = await self.get_user(payload["user_id"])
            self.logger.info(f"[PROCESS] getUser completed, result: {result}")
            return result
        elif request_type == "getRoom":
            result = await self.get_room(payload["room_id"])
            self.logger.info(f"[PROCESS] getRoom completed, result: {result}")
            return result
        elif request_type == "listRooms":
            result = await self.list_rooms()
            self.logger.info(f"[PROCESS] listRooms completed, result: {result}")
            return result
        else:
            self.logger.error(f"[PROCESS] Unknown request type: {request_type}")
            raise ValueError(f"Unknown request type: {request_type}")

    async def create_user(self, req: CreateUserRequest) -> dict[str, Any]:
        """Create a new user.

        Args:
            req: Create user request

        Returns:
            User object reference
        """
        self.logger.info(f"[CREATE_USER] Creating user: {req.username}")
        user_id = f"user-{uuid.uuid4().hex[:12]}"

        user = User(
            id=user_id,
            username=req.username,
            display_name=req.display_name,
            avatar_url=req.avatar_url,
        )

        self.logger.info(f"[CREATE_USER] Generated user ID: {user_id}")

        # Save as Prism object
        obj = PrismObject(id=user_id, version=1, data=user.model_dump(mode="json"))
        await self.storage.save(obj)
        self.logger.info(f"[CREATE_USER] User saved to storage: {user_id}")

        # Update index
        self.users_by_username[user.username] = user_id
        self.logger.info(f"[CREATE_USER] Updated users_by_username index: {self.users_by_username}")

        # Return object reference
        result = {
            "user": ObjectReference(id=user_id, version=1, filter_type="default").model_dump()
        }
        self.logger.info(f"[CREATE_USER] Returning result: {result}")
        return result

    async def create_room(self, req: CreateRoomRequest) -> dict[str, Any]:
        """Create a new chat room.

        Args:
            req: Create room request

        Returns:
            Room object reference
        """
        self.logger.info(f"[CREATE_ROOM] Creating room: {req.name}, creator: {req.creator_id}")
        room_id = f"room-{uuid.uuid4().hex[:12]}"

        room = ChatRoom(
            id=room_id,
            name=req.name,
            description=req.description,
            member_ids=[req.creator_id],
            created_by=req.creator_id,
        )

        self.logger.info(f"[CREATE_ROOM] Generated room ID: {room_id}, initial members: {room.member_ids}")

        # Save as Prism object
        obj = PrismObject(id=room_id, version=1, data=room.model_dump(mode="json"))
        await self.storage.save(obj)
        self.logger.info(f"[CREATE_ROOM] Room saved to storage: {room_id}")

        # Update indexes
        self.rooms_by_name[room.name] = room_id
        self.room_messages[room_id] = []
        self.logger.info(f"[CREATE_ROOM] Updated indexes - rooms_by_name: {self.rooms_by_name}, room_messages keys: {list(self.room_messages.keys())}")

        # Update global room list and notify all subscribers
        await self._update_room_list()

        # Return room and creator references
        result = {
            "room": ObjectReference(id=room_id, version=1, filter_type="default").model_dump(),
            "creator": ObjectReference(
                id=req.creator_id, version=1, filter_type="default"
            ).model_dump(),
        }
        self.logger.info(f"[CREATE_ROOM] Returning result: {result}")
        return result

    async def join_room(self, req: JoinRoomRequest) -> dict[str, Any]:
        """Join a chat room.

        Args:
            req: Join room request

        Returns:
            Updated room reference
        """
        self.logger.info(f"[JOIN_ROOM] User {req.user_id} joining room {req.room_id}")

        # Get current room
        room_obj = await self.storage.get_current(req.room_id)
        if not room_obj:
            self.logger.error(f"[JOIN_ROOM] Room {req.room_id} not found")
            raise ValueError(f"Room {req.room_id} not found")

        room = ChatRoom(**room_obj.data)
        self.logger.info(f"[JOIN_ROOM] Current room members: {room.member_ids}")

        # Add user if not already a member
        if req.user_id not in room.member_ids:
            room.member_ids.append(req.user_id)
            self.logger.info(f"[JOIN_ROOM] Added user, new members: {room.member_ids}")

            # Save new version
            new_obj = PrismObject(
                id=room_obj.id, version=room_obj.version + 1, data=room.model_dump(mode="json")
            )
            await self.storage.save(new_obj)
            self.logger.info(f"[JOIN_ROOM] Saved new room version: {new_obj.version}")

            # Notify subscribers
            await self.object_manager.notify_object_updated(new_obj)
            self.logger.info(f"[JOIN_ROOM] Notified subscribers")
        else:
            self.logger.info(f"[JOIN_ROOM] User already a member, no changes made")

        result = {
            "room": ObjectReference(
                id=req.room_id, version=room_obj.version + 1, filter_type="default"
            ).model_dump()
        }
        self.logger.info(f"[JOIN_ROOM] Returning result: {result}")
        return result

    async def send_message(self, req: SendMessageRequest) -> dict[str, Any]:
        """Send a message to a chat room.

        Args:
            req: Send message request

        Returns:
            Message object reference
        """
        self.logger.info(f"[SEND_MESSAGE] Sending message to room {req.room_id} from user {req.user_id}")
        message_id = f"msg-{uuid.uuid4().hex[:12]}"

        message = Message(
            id=message_id, room_id=req.room_id, user_id=req.user_id, content=req.content
        )

        self.logger.info(f"[SEND_MESSAGE] Generated message ID: {message_id}, content length: {len(req.content)}")

        # Save as Prism object
        obj = PrismObject(id=message_id, version=1, data=message.model_dump(mode="json"))
        await self.storage.save(obj)
        self.logger.info(f"[SEND_MESSAGE] Message saved to storage: {message_id}")

        # Update room messages index
        if req.room_id not in self.room_messages:
            self.room_messages[req.room_id] = []
        self.room_messages[req.room_id].append(message_id)
        self.logger.info(f"[SEND_MESSAGE] Updated room_messages - room {req.room_id} now has {len(self.room_messages[req.room_id])} messages")

        # Notify subscribers about new message
        await self.object_manager.notify_object_updated(obj)
        self.logger.info(f"[SEND_MESSAGE] Notified subscribers about new message")

        # Return message reference with user reference
        result = {
            "message": ObjectReference(
                id=message_id, version=1, filter_type="default", subscribe=True
            ).model_dump(),
            "user": ObjectReference(
                id=req.user_id, version=1, filter_type="default"
            ).model_dump(),
        }
        self.logger.info(f"[SEND_MESSAGE] Returning result: {result}")
        return result

    async def get_room_messages(self, req: GetRoomMessagesRequest) -> dict[str, Any]:
        """Get messages from a chat room.

        Args:
            req: Get messages request

        Returns:
            List of message references
        """
        self.logger.info(f"[GET_ROOM_MESSAGES] Getting messages for room {req.room_id}, limit: {req.limit}")
        message_ids = self.room_messages.get(req.room_id, [])
        self.logger.info(f"[GET_ROOM_MESSAGES] Total messages in room: {len(message_ids)}")

        # Get last N messages
        recent_ids = message_ids[-req.limit :]
        self.logger.info(f"[GET_ROOM_MESSAGES] Returning {len(recent_ids)} recent messages: {recent_ids}")

        # Return as object references
        messages = [
            ObjectReference(id=msg_id, version=1, filter_type="default", subscribe=True).model_dump()
            for msg_id in recent_ids
        ]

        result = {"messages": messages}
        self.logger.info(f"[GET_ROOM_MESSAGES] Returning result: {result}")
        return result

    async def get_user(self, user_id: str) -> dict[str, Any]:
        """Get a user by ID.

        Args:
            user_id: User ID

        Returns:
            User object reference
        """
        self.logger.info(f"[GET_USER] Getting user: {user_id}")
        user_obj = await self.storage.get_current(user_id)
        if not user_obj:
            self.logger.error(f"[GET_USER] User {user_id} not found")
            raise ValueError(f"User {user_id} not found")

        self.logger.info(f"[GET_USER] Found user: {user_id}, version: {user_obj.version}")
        result = {
            "user": ObjectReference(
                id=user_id, version=user_obj.version, filter_type="default"
            ).model_dump()
        }
        self.logger.info(f"[GET_USER] Returning result: {result}")
        return result

    async def get_room(self, room_id: str) -> dict[str, Any]:
        """Get a room by ID.

        Args:
            room_id: Room ID

        Returns:
            Room object reference with member references
        """
        self.logger.info(f"[GET_ROOM] Getting room: {room_id}")
        room_obj = await self.storage.get_current(room_id)
        if not room_obj:
            self.logger.error(f"[GET_ROOM] Room {room_id} not found")
            raise ValueError(f"Room {room_id} not found")

        room = ChatRoom(**room_obj.data)
        self.logger.info(f"[GET_ROOM] Found room: {room_id}, version: {room_obj.version}, members: {room.member_ids}")

        # Return room reference with member references
        result = {
            "room": ObjectReference(
                id=room_id, version=room_obj.version, filter_type="default"
            ).model_dump(),
            "members": [
                ObjectReference(id=member_id, version=1, filter_type="default").model_dump()
                for member_id in room.member_ids
            ],
        }
        self.logger.info(f"[GET_ROOM] Returning result: {result}")
        return result

    async def _update_room_list(self) -> None:
        """Update the global room list object with room IDs and notify subscribers."""
        self.logger.info(f"[UPDATE_ROOM_LIST] Updating global room list")

        # Get all room IDs (just IDs, not full data)
        room_ids = list(self.rooms_by_name.values())
        self.logger.info(f"[UPDATE_ROOM_LIST] Room IDs: {room_ids}")

        # Get current room list object
        room_list_obj = await self.storage.get_current(self.ROOM_LIST_ID)

        if room_list_obj:
            # Update existing
            new_version = room_list_obj.version + 1
            self.logger.info(f"[UPDATE_ROOM_LIST] Updating existing room list, new version: {new_version}")
        else:
            # Create new
            new_version = 1
            self.logger.info(f"[UPDATE_ROOM_LIST] Creating new room list object")

        # Store just the room IDs
        new_obj = PrismObject(
            id=self.ROOM_LIST_ID,
            version=new_version,
            data={"room_ids": room_ids},
        )

        # Save triggers automatic notification to all subscribers
        await self.storage.save(new_obj)
        self.logger.info(f"[UPDATE_ROOM_LIST] Saved room list, version {new_version}, {len(room_ids)} room IDs")

        # Notify all subscribers (Prism protocol behavior)
        await self.object_manager.notify_object_updated(new_obj)
        self.logger.info(f"[UPDATE_ROOM_LIST] Notified all subscribers about room list update")

    async def list_rooms(self) -> dict[str, Any]:
        """List all available chat rooms.

        Returns:
            ObjectReference to the global room list for subscription
        """
        self.logger.info(f"[LIST_ROOMS] Returning room list object reference")

        # Ensure room list object exists
        room_list_obj = await self.storage.get_current(self.ROOM_LIST_ID)
        if not room_list_obj:
            # Create it
            await self._update_room_list()
            room_list_obj = await self.storage.get_current(self.ROOM_LIST_ID)

        # Return ObjectReference so client can subscribe
        result = {
            "roomList": ObjectReference(
                id=self.ROOM_LIST_ID,
                version=room_list_obj.version if room_list_obj else 1,
                filter_type="default",
                subscribe=True,
            ).model_dump()
        }
        self.logger.info(f"[LIST_ROOMS] Returning result: {result}")
        return result
