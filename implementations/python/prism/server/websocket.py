"""WebSocket transport for Prism protocol."""

import asyncio
import json
from typing import Any

from fastapi import WebSocket, WebSocketDisconnect
from pydantic import ValidationError

from prism.core.protocol import ClientMessage, RequestMessage, ServerMessage
from prism.server.object_manager import PrismObjectManager
from prism.server.request_router import RequestRouter


class WebSocketConnection:
    """Manages a single WebSocket connection for a client."""

    def __init__(
        self,
        websocket: WebSocket,
        client_id: str,
        object_manager: PrismObjectManager,
        request_router: RequestRouter,
    ):
        """Initialize WebSocket connection.

        Args:
            websocket: FastAPI WebSocket instance
            client_id: Unique client identifier
            object_manager: Prism object manager
            request_router: Request router
        """
        self.websocket = websocket
        self.client_id = client_id
        self.object_manager = object_manager
        self.request_router = request_router
        self._send_lock = asyncio.Lock()

    async def handle(self) -> None:
        """Handle WebSocket connection lifecycle."""
        await self.websocket.accept()

        # Register this client's send callback
        self.object_manager.register_client(self.client_id, self.send_message)

        try:
            while True:
                # Receive message from client
                data = await self.websocket.receive_text()
                await self._handle_message(data)

        except WebSocketDisconnect:
            pass
        except Exception as e:
            print(f"WebSocket error for client {self.client_id}: {e}")
        finally:
            # Clean up client state
            self.object_manager.remove_client(self.client_id)

    async def _handle_message(self, data: str) -> None:
        """Handle incoming message from client.

        Args:
            data: JSON-encoded message
        """
        try:
            message_dict = json.loads(data)
            message_type = message_dict.get("type")

            if message_type == "request":
                # Handle request through router
                request = RequestMessage(**message_dict)
                response = await self.request_router.handle_request(self.client_id, request)
                await self.send_message(response)
            else:
                # Handle other message types through object manager
                message = self._parse_client_message(message_dict)
                await self.object_manager.handle_message(self.client_id, message)

        except ValidationError as e:
            await self.object_manager.send_error(
                self.client_id, "INVALID_MESSAGE", f"Invalid message format: {e}"
            )
        except json.JSONDecodeError as e:
            await self.object_manager.send_error(
                self.client_id, "INVALID_JSON", f"Invalid JSON: {e}"
            )
        except Exception as e:
            await self.object_manager.send_error(
                self.client_id, "INTERNAL_ERROR", f"Error processing message: {e}"
            )

    def _parse_client_message(self, data: dict[str, Any]) -> ClientMessage:
        """Parse client message based on type.

        Args:
            data: Message dict

        Returns:
            Parsed ClientMessage

        Raises:
            ValidationError: If message is invalid
        """
        from prism.core.protocol import (
            SubscribeMessage,
            SyncMessage,
            UnsubscribeMessage,
            UpdateFilterMessage,
        )

        msg_type = data.get("type")

        if msg_type == "subscribe":
            return SubscribeMessage(**data)
        elif msg_type == "unsubscribe":
            return UnsubscribeMessage(**data)
        elif msg_type == "sync":
            return SyncMessage(**data)
        elif msg_type == "updateFilter":
            return UpdateFilterMessage(**data)
        else:
            raise ValueError(f"Unknown message type: {msg_type}")

    async def send_message(self, message: ServerMessage) -> None:
        """Send message to client.

        Args:
            message: Server message to send
        """
        async with self._send_lock:
            try:
                await self.websocket.send_text(message.model_dump_json())
            except Exception as e:
                print(f"Error sending message to client {self.client_id}: {e}")
