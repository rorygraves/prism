# Prism Python Backend

Python implementation of the Prism protocol with FastAPI and PostgreSQL.

## Features

- **Core Prism Library**: Versioned objects, delta computation, filters
- **Object Manager**: Subscription management, intelligent caching
- **Request Router**: Smart reference resolution and hydration
- **PostgreSQL Storage**: Async SQLAlchemy adapter
- **WebSocket Transport**: Real-time bidirectional communication
- **Type Safety**: Full mypy type checking
- **Code Quality**: Ruff linting and formatting

## Installation

```bash
# Install dependencies
poetry install

# Run type checking
poetry run mypy prism chat_demo

# Run linting
poetry run ruff check prism chat_demo

# Format code
poetry run ruff format prism chat_demo

# Run tests
poetry run pytest
```

## Database Setup

```bash
# Create PostgreSQL database
createdb prism_chat

# Or with custom credentials
psql -U postgres -c "CREATE DATABASE prism_chat;"
```

Update the database URL in `chat_demo/main.py` if needed:
```python
database_url = "postgresql+asyncpg://user:password@localhost/prism_chat"
```

## Running the Chat Demo

```bash
# Start the server
poetry run python -m chat_demo.main

# Or with uvicorn directly
poetry run uvicorn chat_demo.main:app --reload --port 8000
```

The server will be available at:
- WebSocket: `ws://localhost:8000/ws`
- HTTP API: `http://localhost:8000`
- Health check: `http://localhost:8000/health`

## Architecture

```
backend/
├── prism/                  # Core Prism library
│   ├── core/              # Protocol, types, delta computation
│   ├── filters/           # Filter system
│   ├── server/            # Server components
│   └── storage/           # Storage adapters
└── chat_demo/             # Chat demo application
    ├── models.py          # Domain models
    ├── handler.py         # Business logic
    └── main.py            # FastAPI app
```

## Usage Example

### Subscribe to an object

```json
{
  "type": "subscribe",
  "object_id": "room-abc123",
  "filter_type": "default"
}
```

### Send a request with smart hydration

```json
{
  "type": "request",
  "request_id": "req-123",
  "request_type": "sendMessage",
  "payload": {
    "room_id": "room-abc123",
    "user_id": "user-xyz789",
    "content": "Hello, world!"
  },
  "options": {
    "hydrate_refs": true,
    "subscribe_to_refs": true
  }
}
```

## Development

### Type Checking

The codebase uses strict mypy type checking:

```bash
poetry run mypy prism chat_demo
```

### Linting and Formatting

Uses ruff for fast linting and formatting:

```bash
# Check for issues
poetry run ruff check prism chat_demo

# Auto-fix issues
poetry run ruff check --fix prism chat_demo

# Format code
poetry run ruff format prism chat_demo
```

### Testing

```bash
# Run all tests
poetry run pytest

# Run with coverage
poetry run pytest --cov=prism --cov=chat_demo

# Run specific test file
poetry run pytest tests/test_delta.py
```

## API Reference

See the [Protocol Documentation](../docs/protocol.md) for detailed API reference.
