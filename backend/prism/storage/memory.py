"""In-memory storage adapter for testing and development."""

from collections import defaultdict

from prism.core.types import PrismObject
from prism.storage.base import StorageAdapter


class MemoryStorageAdapter(StorageAdapter):
    """Simple in-memory storage adapter.

    This adapter stores all objects in memory and is useful for:
    - Testing
    - Development without database setup
    - Demos and prototypes

    Note: All data is lost when the application restarts.
    """

    def __init__(self) -> None:
        """Initialize the memory storage adapter."""
        # Store objects by ID and version: {object_id: {version: PrismObject}}
        self._storage: dict[str, dict[int, PrismObject]] = defaultdict(dict)
        # Track current version for each object: {object_id: max_version}
        self._current_versions: dict[str, int] = {}

    @classmethod
    async def create(cls) -> "MemoryStorageAdapter":
        """Create a new memory storage adapter.

        Returns:
            A new MemoryStorageAdapter instance
        """
        return cls()

    async def save(self, obj: PrismObject) -> None:
        """Save an object to memory.

        Args:
            obj: The object to save
        """
        self._storage[obj.id][obj.version] = obj
        # Update current version
        if obj.id not in self._current_versions or obj.version > self._current_versions[obj.id]:
            self._current_versions[obj.id] = obj.version

    async def get_current(self, object_id: str) -> PrismObject | None:
        """Get the current (latest) version of an object.

        Args:
            object_id: ID of the object

        Returns:
            The current object, or None if not found
        """
        if object_id not in self._current_versions:
            return None

        current_version = self._current_versions[object_id]
        return self._storage[object_id].get(current_version)

    async def get_version(self, object_id: str, version: int) -> PrismObject | None:
        """Get a specific version of an object.

        Args:
            object_id: ID of the object
            version: Version number

        Returns:
            The requested object version, or None if not found
        """
        return self._storage[object_id].get(version)

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
        if object_id not in self._storage:
            return []

        versions = self._storage[object_id]
        result = []

        for version in range(from_version, to_version + 1):
            if version in versions:
                result.append(versions[version])

        return result

    async def delete(self, object_id: str) -> None:
        """Delete all versions of an object.

        Args:
            object_id: ID of the object to delete
        """
        if object_id in self._storage:
            del self._storage[object_id]
        if object_id in self._current_versions:
            del self._current_versions[object_id]

    async def list_objects(self, limit: int = 100, offset: int = 0) -> list[str]:
        """List object IDs.

        Args:
            limit: Maximum number of IDs to return
            offset: Number of IDs to skip

        Returns:
            List of object IDs
        """
        all_ids = sorted(self._storage.keys())
        return all_ids[offset : offset + limit]
