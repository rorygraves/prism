"""Prism protocol message definitions.

Defines the message types that flow between client and server over the transport layer.
"""

from typing import Annotated, Any, Literal

from pydantic import BaseModel, Discriminator, Field

from prism.core.types import HydratedReference

# Client -> Server Messages


class SubscribeMessage(BaseModel):
    """Subscribe to object updates."""

    type: Literal["subscribe"] = "subscribe"
    object_id: str
    filter_type: str | None = None
    filter_params: dict[str, Any] | None = None
    temporary: bool = Field(False, description="One-time fetch vs ongoing subscription")


class UnsubscribeMessage(BaseModel):
    """Unsubscribe from object updates."""

    type: Literal["unsubscribe"] = "unsubscribe"
    object_id: str


class SyncStateItem(BaseModel):
    """Client's current state for an object."""

    id: str
    version: int
    filter_type: str


class SyncMessage(BaseModel):
    """Synchronize state after reconnection."""

    type: Literal["sync"] = "sync"
    states: list[SyncStateItem]


class RequestMessage(BaseModel):
    """Business logic request with automatic hydration."""

    type: Literal["request"] = "request"
    request_id: str
    request_type: str
    payload: dict[str, Any]
    options: dict[str, Any] | None = None


class UpdateFilterMessage(BaseModel):
    """Update filter for existing subscription."""

    type: Literal["updateFilter"] = "updateFilter"
    object_id: str
    filter_type: str
    filter_params: dict[str, Any] | None = None


def get_client_message_type(v: Any) -> str:
    """Discriminator function for client messages."""
    if isinstance(v, dict):
        return str(v.get("type", "unknown"))
    return str(getattr(v, "type", "unknown"))


ClientMessage = Annotated[
    SubscribeMessage
    | UnsubscribeMessage
    | SyncMessage
    | RequestMessage
    | UpdateFilterMessage,
    Discriminator(get_client_message_type),
]


# Server -> Client Messages


class FullObjectMessage(BaseModel):
    """Full object transmission."""

    type: Literal["fullObject"] = "fullObject"
    id: str
    version: int
    data: dict[str, Any]
    filtered: bool = False
    filter_type: str | None = None


class DeltaMessage(BaseModel):
    """Delta update for an object."""

    type: Literal["delta"] = "delta"
    id: str
    from_version: int
    to_version: int
    patches: list[dict[str, Any]]
    filter_type: str | None = None


class ResponseMessage(BaseModel):
    """Response to a request with smart hydration."""

    type: Literal["response"] = "response"
    request_id: str
    success: bool
    data: dict[str, Any] | None = None
    hydrated: list[HydratedReference] = Field(default_factory=list)
    error: str | None = None


class ErrorMessage(BaseModel):
    """Error notification."""

    type: Literal["error"] = "error"
    code: str
    message: str
    object_id: str | None = None
    request_id: str | None = None


def get_server_message_type(v: Any) -> str:
    """Discriminator function for server messages."""
    if isinstance(v, dict):
        return str(v.get("type", "unknown"))
    return str(getattr(v, "type", "unknown"))


ServerMessage = Annotated[
    FullObjectMessage | DeltaMessage | ResponseMessage | ErrorMessage,
    Discriminator(get_server_message_type),
]
