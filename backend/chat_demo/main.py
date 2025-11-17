"""Main FastAPI application for chat demo."""

import uuid
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket
from fastapi.middleware.cors import CORSMiddleware

from chat_demo.handler import ChatBusinessHandler
from prism.filters.common import create_default_registry
from prism.server.object_manager import PrismObjectManager
from prism.server.request_router import RequestRouter
from prism.server.websocket import WebSocketConnection
from prism.storage.postgres import PostgresStorageAdapter

# Global state
storage: PostgresStorageAdapter | None = None
object_manager: PrismObjectManager | None = None
request_router: RequestRouter | None = None


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Application lifespan manager."""
    global storage, object_manager, request_router

    # Initialize storage
    database_url = "postgresql+asyncpg://postgres:postgres@localhost/prism_chat"
    storage = await PostgresStorageAdapter.create(database_url)

    # Initialize filter registry
    filters = create_default_registry()

    # Initialize object manager
    object_manager = PrismObjectManager(storage, filters)

    # Initialize business handler
    business_handler = ChatBusinessHandler(storage, object_manager)

    # Initialize request router
    request_router = RequestRouter(object_manager, business_handler)

    print("Chat demo server started")

    yield

    # Cleanup
    if storage:
        await storage.close()

    print("Chat demo server stopped")


# Create FastAPI app
app = FastAPI(
    title="Prism Chat Demo",
    description="Multi-user chat application demonstrating Prism protocol",
    version="0.1.0",
    lifespan=lifespan,
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify actual origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
async def root() -> dict[str, str]:
    """Root endpoint."""
    return {"message": "Prism Chat Demo Server", "version": "0.1.0"}


@app.get("/health")
async def health() -> dict[str, str]:
    """Health check endpoint."""
    return {"status": "healthy"}


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket) -> None:
    """WebSocket endpoint for Prism protocol.

    Each connection gets a unique client ID and maintains its own state
    through the PrismObjectManager.
    """
    if object_manager is None or request_router is None:
        await websocket.close(code=1011, reason="Server not initialized")
        return

    # Generate unique client ID
    client_id = f"client-{uuid.uuid4().hex[:12]}"

    # Create connection handler
    connection = WebSocketConnection(websocket, client_id, object_manager, request_router)

    # Handle connection
    await connection.handle()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "chat_demo.main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level="info",
    )
