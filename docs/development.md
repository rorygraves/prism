# Development Guide

This guide covers the development workflow, tooling, and best practices for contributing to the Prism Chat Demo project.

## Table of Contents

- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Project Architecture](#project-architecture)
- [Making Changes](#making-changes)
- [Debugging](#debugging)
- [Code Quality](#code-quality)
- [Best Practices](#best-practices)

## Getting Started

### Prerequisites

- **Node.js** >= 18
- **Python** >= 3.11
- **PostgreSQL** >= 14
- **pnpm** (installed automatically by setup script)
- **Poetry** (installed automatically by setup script)

### Initial Setup

```bash
# Clone the repository
git clone <repository-url>
cd prism

# Run automated setup
./scripts/setup.sh
```

This script will:
1. Check all prerequisites
2. Install pnpm and Poetry if needed
3. Install all dependencies
4. Build frontend packages
5. Create the PostgreSQL database
6. Install Playwright browsers

### Starting Development Servers

```bash
# Start both servers with logging
./scripts/start-all.sh

# Or start individually
./scripts/start-backend.sh
./scripts/start-frontend.sh
```

Access the application:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8000
- Health Check: http://localhost:8000/health
- WebSocket: ws://localhost:8000/ws

## Development Workflow

### 1. Check Status

Before starting work, check what's running:

```bash
./scripts/status.sh
./scripts/health-check.sh
```

### 2. Make Changes

#### Backend Development

Backend uses Python with FastAPI and auto-reloads on changes:

```bash
# Backend files are in:
backend/
├── prism/           # Core protocol library
│   ├── core/       # Protocol types and delta computation
│   ├── filters/    # Filter system
│   ├── server/     # Server components (WebSocket, object manager)
│   └── storage/    # Storage adapters (PostgreSQL)
└── chat_demo/      # Chat application
    ├── models.py   # Pydantic models
    ├── handler.py  # Business logic
    └── main.py     # FastAPI application

# Watch backend logs
./scripts/logs.sh --backend

# Run backend tests
./scripts/test-backend.sh
```

Key files for chat demo:
- `chat_demo/models.py` - Data models (User, ChatRoom, Message)
- `chat_demo/handler.py` - Business logic (create user, create room, etc.)
- `chat_demo/main.py` - FastAPI app and WebSocket endpoint

#### Frontend Development

Frontend uses Vue 3 with Vite and supports HMR (Hot Module Replacement):

```bash
# Frontend files are in:
frontend/
├── packages/
│   ├── prism-client/    # Core TypeScript client
│   │   └── src/
│   │       ├── client.ts    # PrismClient implementation
│   │       └── types.ts     # Protocol types
│   └── prism-vue/       # Vue composables
│       └── src/
│           └── composables.ts  # usePrismObject, usePrismRequest
└── chat-demo/           # Chat demo application
    └── src/
        ├── views/       # Vue components (HomeView, ChatView)
        ├── router.ts    # Vue Router configuration
        └── main.ts      # Application entry point

# Watch frontend logs
./scripts/logs.sh --frontend

# Run frontend tests (when available)
./scripts/test-frontend.sh
```

**Important**: If you modify `prism-client` or `prism-vue`, rebuild:

```bash
cd frontend/packages/prism-client
pnpm run build

cd ../prism-vue
pnpm run build

# Or restart frontend (which rebuilds if needed)
./scripts/restart-all.sh
```

### 3. Test Changes

```bash
# Run all tests
./scripts/test-all.sh

# Or run individually
./scripts/test-backend.sh    # Unit tests
./scripts/test-frontend.sh   # Unit tests (when available)
./scripts/run-e2e.sh         # End-to-end tests

# Run E2E tests in UI mode for debugging
./scripts/run-e2e.sh --ui

# Keep servers running after E2E tests
./scripts/run-e2e.sh --keep-alive
```

### 4. View Logs

```bash
# Watch all logs
./scripts/logs.sh

# Watch specific server
./scripts/logs.sh --backend
./scripts/logs.sh --frontend

# Filter for specific events
./scripts/logs.sh --filter "CREATE_ROOM"
./scripts/logs.sh --filter "ERROR"
```

### 5. Commit Changes

```bash
git add .
git commit -m "Description of changes"
```

## Project Architecture

### Prism Protocol

The Prism protocol is the core of this project:

1. **Versioned Objects**: Every object has an ID and version number
2. **Delta Updates**: Only changed fields are transmitted
3. **Subscriptions**: Clients subscribe to objects for real-time updates
4. **Filters**: Transform objects based on client permissions/needs
5. **Request/Response**: Automatic object reference resolution

Example flow:
```
Client                          Server
  |                               |
  |--- subscribe(room-123) ------>|
  |<-- object(v1) ----------------|
  |                               |
  |                          (room updated)
  |                               |
  |<-- delta(v1→v2) -------------|
```

### Backend Architecture

```
FastAPI Application
    ├── WebSocket Endpoint (/ws)
    │   ├── WebSocketConnection (per client)
    │   │   ├── PrismObjectManager (subscriptions, cache)
    │   │   └── RequestRouter (business logic)
    │   │       └── ChatBusinessHandler
    │   │           ├── create_user()
    │   │           ├── create_room()
    │   │           ├── send_message()
    │   │           └── ...
    │   │
    │   └── PostgresStorageAdapter
    │       ├── save(obj)
    │       ├── get_current(id)
    │       └── get_version(id, version)
    │
    └── Filter Registry
        └── DefaultFilter
```

### Frontend Architecture

```
Vue Application
    ├── PrismClient (WebSocket client)
    │   ├── subscribe(id)
    │   ├── request(type, payload)
    │   ├── watch(id, callback)
    │   └── cache
    │
    ├── Vue Composables
    │   ├── usePrismObject(id) → reactive object
    │   ├── usePrismRequest() → execute requests
    │   └── usePrismObjects(ids) → multiple objects
    │
    └── Views
        ├── HomeView (login, room list)
        └── ChatView (messages, send)
```

### Real-time Synchronization

The chat demo uses a special "global room list" object for real-time sync:

1. On mount, all clients subscribe to `global-room-list`
2. Room list contains array of room IDs
3. When a room is created:
   - Room object is saved
   - Room list is updated (version incremented)
   - All subscribers receive delta update
   - Clients subscribe to new room ID
4. New room appears instantly in all windows

## Making Changes

### Adding a New Feature

Example: Add "delete room" functionality

1. **Backend**: Add handler method
```python
# backend/chat_demo/handler.py
async def delete_room(self, req: DeleteRoomRequest) -> dict[str, Any]:
    self.logger.info(f"[DELETE_ROOM] Deleting room {req.room_id}")

    # Get room
    room_obj = await self.storage.get_current(req.room_id)
    if not room_obj:
        raise ValueError(f"Room {req.room_id} not found")

    # Mark as deleted (or actually delete)
    # ... implementation ...

    # Update room list
    await self._update_room_list()

    return {"success": True}
```

2. **Backend**: Register request type
```python
# backend/chat_demo/handler.py - in process()
elif request_type == "deleteRoom":
    result = await self.delete_room(DeleteRoomRequest(**payload))
    return result
```

3. **Frontend**: Call from UI
```typescript
// frontend/chat-demo/src/views/HomeView.vue
const handleDeleteRoom = async (roomId: string) => {
  await execute('deleteRoom', { room_id: roomId });
  // Room list updates automatically via subscription
};
```

4. **Test**: Add E2E test
```typescript
// e2e/tests/multi-user-chat.spec.ts
test('user can delete room', async ({ page }) => {
  // ... create room ...
  await page.click('[data-test="delete-room"]');
  // ... verify room deleted ...
});
```

5. **Run tests**
```bash
./scripts/test-all.sh
```

### Modifying the Protocol

If you need to change core protocol behavior:

1. Update `backend/prism/core/types.py` (data models)
2. Update `backend/prism/core/protocol.py` (messages)
3. Update `frontend/packages/prism-client/src/types.ts`
4. Update `frontend/packages/prism-client/src/client.ts`
5. Rebuild frontend packages
6. Run all tests

## Debugging

### Backend Debugging

**View logs:**
```bash
./scripts/logs.sh --backend
tail -f logs/backend-latest.log
```

**Add logging:**
```python
self.logger.info(f"[MY_FEATURE] Description: {variable}")
self.logger.error(f"[MY_FEATURE] Error: {error}")
```

**Run with debugger:**
```bash
cd backend
poetry run python -m debugpy --listen 5678 --wait-for-client -m uvicorn chat_demo.main:app --reload
```

**Check database:**
```bash
psql -U postgres -d prism_chat
\dt  # List tables
SELECT * FROM prism_objects LIMIT 10;
```

### Frontend Debugging

**View browser console:**
- Open DevTools (F12)
- Check Console tab for `[MOUNT]`, `[LOGIN]`, `[CREATE_ROOM]` logs

**Vue DevTools:**
- Install Vue DevTools browser extension
- Inspect component state and props

**Network debugging:**
- DevTools → Network tab
- Filter: WS (WebSocket)
- View WebSocket messages

**Add logging:**
```typescript
console.log('[MY_FEATURE] Description:', variable);
console.error('[MY_FEATURE] Error:', error);
```

### E2E Debugging

```bash
# Run in UI mode (visual debugging)
./scripts/run-e2e.sh --ui

# Run in headed mode (see browser)
cd e2e
npm run test:headed

# Debug specific test
cd e2e
npm run test:debug -- tests/multi-user-chat.spec.ts
```

## Code Quality

### Linting

**Backend:**
```bash
cd backend
poetry run ruff check prism chat_demo
poetry run ruff format prism chat_demo
poetry run mypy prism chat_demo
```

**Frontend:**
```bash
cd frontend
pnpm run lint
pnpm run format
```

### Type Checking

**Backend** - Strict mypy enabled:
```bash
cd backend
poetry run mypy prism chat_demo
```

**Frontend** - Strict TypeScript enabled:
```bash
cd frontend
pnpm run build  # Runs vue-tsc
```

### Testing Coverage

**Backend:**
```bash
./scripts/test-backend.sh
# View coverage: open backend/htmlcov/index.html
```

**Frontend:** (when tests are added)
```bash
./scripts/test-frontend.sh
```

## Best Practices

### Code Style

**Backend (Python):**
- Follow PEP 8
- Use type hints everywhere
- Async/await for I/O operations
- Pydantic models for data validation
- Descriptive docstrings

**Frontend (TypeScript):**
- Follow Vue 3 Composition API patterns
- Use TypeScript types, avoid `any`
- Reactive refs for state
- Composables for reusable logic
- Single File Components (SFC)

### Git Workflow

1. Create feature branch: `git checkout -b feature/my-feature`
2. Make changes
3. Run tests: `./scripts/test-all.sh`
4. Commit with descriptive message
5. Push and create PR

### Performance

- Backend: Use async/await, connection pooling
- Frontend: Use `usePrismObject` for reactive data (auto-subscribes)
- Database: Add indexes for frequently queried fields
- Minimize WebSocket message size (use delta updates)

### Security

- Validate all inputs (Pydantic models)
- Use filters to hide sensitive data
- Sanitize user-generated content
- Use parameterized SQL queries (SQLAlchemy)
- HTTPS/WSS in production

## Troubleshooting

See [CLAUDE.md](../CLAUDE.md#troubleshooting) for common issues and solutions.

## Additional Resources

- [CLAUDE.md](../CLAUDE.md) - Autonomous development workflow
- [testing.md](./testing.md) - Testing guide
- [protocol.md](./protocol.md) - Prism protocol specification
- [getting-started.md](./getting-started.md) - Tutorial
