"""PostgreSQL storage adapter for Prism objects."""

from sqlalchemy import JSON, Column, Integer, String, and_, select
from sqlalchemy import delete as sa_delete
from sqlalchemy.ext.asyncio import AsyncEngine, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from prism.core.types import PrismObject
from prism.storage.base import StorageAdapter


class Base(DeclarativeBase):
    """Base class for SQLAlchemy models."""

    pass


class PrismObjectModel(Base):
    """SQLAlchemy model for storing Prism objects."""

    __tablename__ = "prism_objects"

    id = Column(String, primary_key=True, index=True)
    version = Column(Integer, primary_key=True)
    data = Column(JSON, nullable=False)


class PostgresStorageAdapter(StorageAdapter):
    """PostgreSQL storage adapter using SQLAlchemy async."""

    def __init__(self, engine: AsyncEngine) -> None:
        """Initialize PostgreSQL adapter.

        Args:
            engine: SQLAlchemy async engine
        """
        self.engine = engine
        self.session_maker = async_sessionmaker(engine, expire_on_commit=False)

    @classmethod
    async def create(cls, database_url: str) -> "PostgresStorageAdapter":
        """Create a new PostgreSQL adapter and initialize the database.

        Args:
            database_url: PostgreSQL connection URL (async format)

        Returns:
            Initialized adapter
        """
        engine = create_async_engine(database_url, echo=False)
        adapter = cls(engine)
        await adapter.init_db()
        return adapter

    async def init_db(self) -> None:
        """Initialize database tables."""
        async with self.engine.begin() as conn:
            await conn.run_sync(Base.metadata.create_all)

    async def save(self, obj: PrismObject) -> None:
        """Save an object to PostgreSQL."""
        async with self.session_maker() as session:
            model = PrismObjectModel(id=obj.id, version=obj.version, data=obj.data)
            session.add(model)
            await session.commit()

    async def get_current(self, object_id: str) -> PrismObject | None:
        """Get the current (latest) version of an object."""
        async with self.session_maker() as session:
            stmt = (
                select(PrismObjectModel)
                .where(PrismObjectModel.id == object_id)
                .order_by(PrismObjectModel.version.desc())
                .limit(1)
            )
            result = await session.execute(stmt)
            model = result.scalar_one_or_none()

            if model is None:
                return None

            return PrismObject(id=model.id, version=model.version, data=model.data)

    async def get_version(self, object_id: str, version: int) -> PrismObject | None:
        """Get a specific version of an object."""
        async with self.session_maker() as session:
            stmt = select(PrismObjectModel).where(
                and_(PrismObjectModel.id == object_id, PrismObjectModel.version == version)
            )
            result = await session.execute(stmt)
            model = result.scalar_one_or_none()

            if model is None:
                return None

            return PrismObject(id=model.id, version=model.version, data=model.data)

    async def get_versions_range(
        self, object_id: str, from_version: int, to_version: int
    ) -> list[PrismObject]:
        """Get all versions of an object in a range."""
        async with self.session_maker() as session:
            stmt = (
                select(PrismObjectModel)
                .where(
                    and_(
                        PrismObjectModel.id == object_id,
                        PrismObjectModel.version >= from_version,
                        PrismObjectModel.version <= to_version,
                    )
                )
                .order_by(PrismObjectModel.version)
            )
            result = await session.execute(stmt)
            models = result.scalars().all()

            return [
                PrismObject(id=model.id, version=model.version, data=model.data)
                for model in models
            ]

    async def delete(self, object_id: str) -> None:
        """Delete all versions of an object."""
        async with self.session_maker() as session:
            stmt = sa_delete(PrismObjectModel).where(PrismObjectModel.id == object_id)
            await session.execute(stmt)
            await session.commit()

    async def list_objects(self, limit: int = 100, offset: int = 0) -> list[str]:
        """List unique object IDs."""
        async with self.session_maker() as session:
            stmt = (
                select(PrismObjectModel.id)
                .distinct()
                .limit(limit)
                .offset(offset)
            )
            result = await session.execute(stmt)
            return list(result.scalars().all())

    async def close(self) -> None:
        """Close the database connection."""
        await self.engine.dispose()
