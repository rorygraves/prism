"""Base storage adapter interface."""

from abc import ABC, abstractmethod

from prism.core.types import PrismObject


class StorageAdapter(ABC):
    """Abstract base class for storage adapters.

    Storage adapters are responsible for persisting PrismObjects and
    retrieving them by ID and version.
    """

    @abstractmethod
    async def save(self, obj: PrismObject) -> None:
        """Save an object to storage.

        Args:
            obj: The object to save
        """
        pass

    @abstractmethod
    async def get_current(self, object_id: str) -> PrismObject | None:
        """Get the current (latest) version of an object.

        Args:
            object_id: ID of the object

        Returns:
            The current object, or None if not found
        """
        pass

    @abstractmethod
    async def get_version(self, object_id: str, version: int) -> PrismObject | None:
        """Get a specific version of an object.

        Args:
            object_id: ID of the object
            version: Version number

        Returns:
            The requested object version, or None if not found
        """
        pass

    @abstractmethod
    async def get_versions_range(
        self, object_id: str, from_version: int, to_version: int
    ) -> list[PrismObject]:
        """Get all versions of an object in a range.

        Args:
            object_id: ID of the object
            from_version: Starting version (inclusive)
            to_version: Ending version (inclusive)

        Returns:
            List of object versions in the range, ordered by version
        """
        pass

    @abstractmethod
    async def delete(self, object_id: str) -> None:
        """Delete all versions of an object.

        Args:
            object_id: ID of the object to delete
        """
        pass

    @abstractmethod
    async def list_objects(self, limit: int = 100, offset: int = 0) -> list[str]:
        """List object IDs.

        Args:
            limit: Maximum number of IDs to return
            offset: Number of IDs to skip

        Returns:
            List of object IDs
        """
        pass
