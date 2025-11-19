"""Delta computation and application using JSON Patch (RFC 6902)."""

import json

import jsonpatch

from prism.core.types import Delta, PrismObject


class DeltaComputer:
    """Computes and applies deltas between object versions."""

    @staticmethod
    def compute_delta(
        from_obj: PrismObject, to_obj: PrismObject, filter_type: str | None = None
    ) -> Delta:
        """Compute the delta between two versions of an object.

        Args:
            from_obj: The earlier version
            to_obj: The later version
            filter_type: Optional filter applied to both objects

        Returns:
            Delta containing the JSON Patch operations

        Raises:
            ValueError: If objects have different IDs or invalid version ordering
        """
        if from_obj.id != to_obj.id:
            raise ValueError(
                f"Cannot compute delta between different objects: " f"{from_obj.id} vs {to_obj.id}"
            )

        if from_obj.version >= to_obj.version:
            raise ValueError(f"Invalid version ordering: {from_obj.version} >= {to_obj.version}")

        # Compute JSON Patch
        patch = jsonpatch.make_patch(from_obj.data, to_obj.data)

        return Delta(
            object_id=from_obj.id,
            from_version=from_obj.version,
            to_version=to_obj.version,
            patches=list(patch),  # Convert to list of dicts
        )

    @staticmethod
    def apply_delta(obj: PrismObject, delta: Delta) -> PrismObject:
        """Apply a delta to an object to produce the next version.

        Args:
            obj: The base object
            delta: The delta to apply

        Returns:
            New PrismObject with the delta applied

        Raises:
            ValueError: If delta doesn't match object
        """
        if obj.id != delta.object_id:
            raise ValueError(
                f"Delta object ID mismatch: delta for {delta.object_id}, " f"applied to {obj.id}"
            )

        if obj.version != delta.from_version:
            raise ValueError(
                f"Version mismatch: object is v{obj.version}, "
                f"delta expects v{delta.from_version}"
            )

        # Apply JSON Patch
        patch = jsonpatch.JsonPatch(delta.patches)
        new_data = patch.apply(obj.data)

        return PrismObject(id=obj.id, version=delta.to_version, data=new_data)

    @staticmethod
    def estimate_delta_size(delta: Delta) -> int:
        """Estimate the size of a delta in bytes (for efficiency comparison)."""
        return len(json.dumps(delta.model_dump()))

    @staticmethod
    def estimate_object_size(obj: PrismObject) -> int:
        """Estimate the size of an object in bytes."""
        return len(json.dumps(obj.data))

    @staticmethod
    def is_delta_efficient(delta: Delta, full_object: PrismObject, threshold: float = 0.7) -> bool:
        """Check if sending delta is more efficient than full object.

        Args:
            delta: The delta to check
            full_object: The full object
            threshold: Delta must be less than this fraction of full object size

        Returns:
            True if delta is more efficient
        """
        delta_size = DeltaComputer.estimate_delta_size(delta)
        object_size = DeltaComputer.estimate_object_size(full_object)

        return delta_size < (object_size * threshold)
