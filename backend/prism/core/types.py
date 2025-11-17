"""Core Prism types and data structures."""

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class PrismObject(BaseModel):
    """Immutable versioned object.

    Every object in Prism contains an ID, version number, and data payload.
    Objects reference other objects by ID only, never by direct reference.
    """

    model_config = ConfigDict(frozen=True)

    id: str = Field(..., description="Unique identifier for the object")
    version: int = Field(..., ge=0, description="Monotonically increasing version number")
    data: dict[str, Any] = Field(..., description="The object's actual content")


class Delta(BaseModel):
    """Represents a change between two versions of an object using JSON Patch."""

    model_config = ConfigDict(frozen=True)

    object_id: str = Field(..., description="ID of the object this delta applies to")
    from_version: int = Field(..., ge=0, description="Starting version")
    to_version: int = Field(..., ge=0, description="Target version")
    patches: list[dict[str, Any]] = Field(
        ..., description="JSON Patch operations (RFC 6902)"
    )


class ObjectReference(BaseModel):
    """Intelligent reference to a Prism object.

    Used in responses to indicate an object that may need to be hydrated
    based on the client's current state.
    """

    model_config = ConfigDict(frozen=True)

    id: str = Field(..., description="Object ID being referenced")
    version: int = Field(..., ge=0, description="Version of the object")
    filter_type: str | None = Field(None, description="Filter to apply when hydrating")
    subscribe: bool = Field(False, description="Whether client should auto-subscribe")


class HydratedReference(BaseModel):
    """Result of hydrating an ObjectReference based on client state."""

    model_config = ConfigDict(frozen=True)

    id: str
    version: int
    data: dict[str, Any] | None = Field(None, description="Full object data if needed")
    delta: Delta | None = Field(None, description="Delta if client has older version")
    cached: bool = Field(False, description="True if client already has this version")


class RequestOptions(BaseModel):
    """Options for how to process a request and its response."""

    model_config = ConfigDict(frozen=True)

    hydrate_refs: bool = Field(True, description="Auto-hydrate object references in response")
    subscribe_to_refs: bool = Field(
        False, description="Auto-subscribe to referenced objects"
    )
    filter_type: str | None = Field(None, description="Default filter for references")


class Subscription(BaseModel):
    """Represents a client subscription to an object."""

    object_id: str
    filter_type: str = "default"
    filter_params: dict[str, Any] | None = None
    current_version: int = 0
    temporary: bool = Field(False, description="One-time fetch vs ongoing subscription")


class ClientState(BaseModel):
    """Tracks what a client knows about objects."""

    subscriptions: dict[str, Subscription] = Field(default_factory=dict)
    object_versions: dict[str, int] = Field(
        default_factory=dict, description="Known object versions"
    )

    def get_version(self, object_id: str) -> int | None:
        """Get the version the client has for an object, if any."""
        return self.object_versions.get(object_id)

    def update_version(self, object_id: str, version: int) -> None:
        """Update the client's known version for an object."""
        self.object_versions[object_id] = version

    def has_subscription(self, object_id: str) -> bool:
        """Check if client is subscribed to an object."""
        return object_id in self.subscriptions
