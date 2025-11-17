# CLAUDE.md - Development Workflow Guide

This document describes the development tooling and workflows for the Prism Chat Demo project, specifically designed to enable autonomous development, testing, and experimentation.

## Quick Start

```bash
# One-time setup (installs dependencies, builds packages, creates database)
./scripts/setup.sh

# Start both servers with logging
./scripts/start-all.sh

# Run all tests
./scripts/test-all.sh

# Check server status
./scripts/status.sh

# View logs in real-time
./scripts/logs.sh

# Stop servers
./scripts/stop-all.sh
```

## Project Structure

```
prism/
├── backend/              # Python FastAPI backend
│   ├── prism/           # Core Prism protocol library
│   └── chat_demo/       # Chat demo implementation
├── frontend/            # TypeScript/Vue frontend (pnpm monorepo)
│   ├── packages/        # Shared packages
│   │   ├── prism-client/    # Core client library
│   │   └── prism-vue/       # Vue 3 composables
│   └── chat-demo/       # Chat demo Vue app
├── e2e/                 # Playwright end-to-end tests
├── scripts/             # Development tooling scripts
├── logs/                # Server logs (gitignored)
└── docs/                # Documentation
```

## Development Scripts

All scripts are located in `scripts/` and are self-contained with proper error handling.

### Setup & Environment

**`./scripts/setup.sh`** - Complete environment setup
- Checks prerequisites (Node.js, Python, PostgreSQL, pnpm, Poetry)
- Installs dependencies for frontend and backend
- Builds frontend packages
- Creates PostgreSQL database
- Installs Playwright browsers

**`./scripts/clean.sh`** - Clean all build artifacts
- Removes `node_modules/`, `dist/`, `__pycache__/`
- Clears logs and PID files
- Removes test artifacts

### Server Management

**`./scripts/start-backend.sh`** - Start backend server
- Creates timestamped log file at `logs/backend-TIMESTAMP.log`
- Symlinks to `logs/backend-latest.log`
- Starts uvicorn on port 8000
- Performs health check at `/health`
- Shows initial log output

**`./scripts/start-frontend.sh`** - Start frontend server
- Ensures backend packages are built
- Creates timestamped log file at `logs/frontend-TIMESTAMP.log`
- Starts Vite dev server on port 3000
- Performs health check
- Shows initial log output

**`./scripts/start-all.sh`** - Start both servers
- Starts backend first
- Waits for backend health check
- Starts frontend
- Displays URLs and helpful commands

**`./scripts/stop-backend.sh`** - Stop backend server
- Stops process by PID file or port
- Cleans up PID file
- Graceful shutdown with fallback to force kill

**`./scripts/stop-frontend.sh`** - Stop frontend server
- Similar to stop-backend.sh

**`./scripts/stop-all.sh`** - Stop both servers

**`./scripts/restart-all.sh`** - Restart both servers
- Stops all servers
- Waits 2 seconds
- Starts all servers

### Logging

All logs are written to the `logs/` directory with timestamps. Symlinks to `*-latest.log` always point to the most recent log file.

**`./scripts/logs.sh`** - View logs in real-time
```bash
# View both servers (default)
./scripts/logs.sh

# View only backend
./scripts/logs.sh --backend

# View only frontend
./scripts/logs.sh --frontend

# Filter logs
./scripts/logs.sh --filter "ERROR"
```

**Log Files:**
- `logs/backend-latest.log` → latest backend log
- `logs/frontend-latest.log` → latest frontend log
- `logs/backend-YYYYMMDD-HHMMSS.log` → archived logs
- `logs/frontend-YYYYMMDD-HHMMSS.log` → archived logs

### Testing

**`./scripts/test-backend.sh`** - Run backend unit tests
- Runs pytest with coverage
- Generates HTML coverage report at `backend/htmlcov/index.html`
- 24 unit tests covering delta computation, filters, and caching

**`./scripts/test-frontend.sh`** - Run frontend unit tests
- Runs Vitest
- Currently shows warning (no tests yet)
- Infrastructure is ready for adding tests

**`./scripts/run-e2e.sh`** - Run end-to-end tests
- Auto-starts servers if not running
- Runs Playwright tests (5 scenarios)
- Stops servers after tests (unless `--keep-alive`)
- Options:
  - `--keep-alive`: Don't stop servers after tests
  - `--ui`: Run Playwright in UI mode for debugging

**`./scripts/test-all.sh`** - Run all test suites
- Runs backend, frontend, and E2E tests
- Displays summary of results
- Exits with failure if any tests fail

### Database Management

**`./scripts/db-create.sh`** - Create PostgreSQL database
- Creates `prism_chat` database
- Checks if database already exists

**`./scripts/db-drop.sh`** - Drop database (with confirmation)
- Stops backend if running
- Terminates all connections
- Drops database

**`./scripts/db-reset.sh`** - Reset database
- Drops and recreates database
- Useful for starting fresh

### Utility Scripts

**`./scripts/status.sh`** - Check server status
- Shows if backend/frontend are running (PID, uptime)
- Shows last 3 log lines from each server
- Shows database status and size

**`./scripts/health-check.sh`** - Health check all systems
- Checks backend health endpoint
- Checks frontend accessibility
- Checks database connection
- Checks WebSocket port
- Returns 0 if all healthy, 1 otherwise

## Common Workflows

### Starting Development

```bash
# First time setup
./scripts/setup.sh

# Start servers
./scripts/start-all.sh

# In another terminal, watch logs
./scripts/logs.sh

# Check everything is healthy
./scripts/health-check.sh
```

### Making Changes

```bash
# Backend changes auto-reload with uvicorn --reload
# Frontend changes auto-reload with Vite HMR

# Run backend tests
./scripts/test-backend.sh

# Run E2E tests while developing
./scripts/run-e2e.sh --keep-alive --ui
```

### Debugging

```bash
# Check what's running
./scripts/status.sh

# View real-time logs
./scripts/logs.sh

# View only errors
./scripts/logs.sh --filter "ERROR"

# Check system health
./scripts/health-check.sh

# Restart everything
./scripts/restart-all.sh
```

### Testing Workflow

```bash
# Run all tests
./scripts/test-all.sh

# Run only backend tests
./scripts/test-backend.sh

# Run E2E tests with UI for debugging
./scripts/run-e2e.sh --ui

# Run E2E tests without stopping servers
./scripts/run-e2e.sh --keep-alive
```

### Clean Start

```bash
# Stop everything
./scripts/stop-all.sh

# Clean all artifacts
./scripts/clean.sh

# Reset database
./scripts/db-reset.sh

# Reinstall and rebuild
./scripts/setup.sh

# Start fresh
./scripts/start-all.sh
```

## Using MCP Browser Tools (Puppeteer)

The project is set up for autonomous testing using MCP Puppeteer tools. Here's how to use them:

### Prerequisites

1. Servers must be running: `./scripts/start-all.sh`
2. MCP Puppeteer server must be configured in your MCP settings

### Basic Workflow

```bash
# 1. Start servers
./scripts/start-all.sh

# 2. Verify they're healthy
./scripts/health-check.sh

# 3. Use MCP Puppeteer tools:
```

**Navigate to the app:**
```typescript
mcp__puppeteer__puppeteer_navigate({
  url: "http://localhost:3000"
})
```

**Take a screenshot:**
```typescript
mcp__puppeteer__puppeteer_screenshot({
  name: "homepage",
  width: 1280,
  height: 720
})
```

**Fill login form:**
```typescript
mcp__puppeteer__puppeteer_fill({
  selector: 'input[type="text"]',
  value: "testuser"
})

mcp__puppeteer__puppeteer_click({
  selector: 'button[type="submit"]'
})
```

**Test room creation:**
```typescript
// Fill room name
mcp__puppeteer__puppeteer_fill({
  selector: 'input[label="Create a new room"]',
  value: "Test Room"
})

// Click create button
mcp__puppeteer__puppeteer_click({
  selector: 'button:has-text("Create")'
})

// Wait for room to appear
mcp__puppeteer__puppeteer_screenshot({
  name: "room-created"
})
```

**Execute JavaScript to inspect state:**
```typescript
mcp__puppeteer__puppeteer_evaluate({
  script: `
    // Get Vue app instance
    const app = document.querySelector('#app').__vueParentComponent;

    // Inspect current state
    return {
      currentUser: localStorage.getItem('userId'),
      rooms: document.querySelectorAll('.room-item').length
    };
  `
})
```

### Multi-Window Testing

To test real-time synchronization across multiple browser windows:

1. Use MCP Puppeteer to open first window
2. Create a room
3. Open second browser window (new context)
4. Verify room appears in second window without refresh

### Common Testing Scenarios

**Test Login Flow:**
```bash
# 1. Navigate to app
# 2. Screenshot initial state
# 3. Fill username
# 4. Click login
# 5. Screenshot logged-in state
# 6. Verify rooms list is visible
```

**Test Room Creation:**
```bash
# 1. Ensure logged in
# 2. Fill room name
# 3. Click create
# 4. Screenshot with new room
# 5. Verify room appears in list
```

**Test Real-time Updates:**
```bash
# 1. Open two browser contexts
# 2. Log in as different users in each
# 3. Create room in window 1
# 4. Screenshot window 2 to verify room appeared
```

**Test Messaging:**
```bash
# 1. Create/join room
# 2. Send message
# 3. Verify message appears
# 4. Check message in second window
```

## Log Monitoring During Development

All application-level activity is logged with prefixes:

**Backend Logs:**
- `[WEBSOCKET]` - WebSocket connection events
- `[PROCESS]` - Request processing
- `[CREATE_USER]` - User creation
- `[CREATE_ROOM]` - Room creation
- `[JOIN_ROOM]` - Room joining
- `[SEND_MESSAGE]` - Message sending
- `[UPDATE_ROOM_LIST]` - Room list updates
- `[LIST_ROOMS]`, `[GET_USER]`, `[GET_ROOM]` - Data retrieval

**Frontend Logs (Browser Console):**
- `[MOUNT]` - Component lifecycle
- `[LOGIN]` - Login/account creation
- `[CREATE_ROOM]` - Room creation
- `[ROOM_LIST]` - Room list subscription updates
- `[LOAD_ROOMS]` - Room loading

**To monitor logs:**
```bash
# Watch all logs
./scripts/logs.sh

# Watch only backend
./scripts/logs.sh --backend

# Watch for specific events
./scripts/logs.sh --filter "CREATE_ROOM"

# In separate terminals
tail -f logs/backend-latest.log | grep "CREATE_ROOM"
tail -f logs/frontend-latest.log | grep "CREATE_ROOM"
```

## Architecture Quick Reference

### Backend Stack
- **Language**: Python 3.11+
- **Framework**: FastAPI
- **WebSocket**: Native FastAPI WebSocket support
- **Database**: PostgreSQL 14+ with asyncpg
- **ORM**: SQLAlchemy (async)
- **Protocol**: Custom Prism protocol (versioned objects, delta updates)

### Frontend Stack
- **Language**: TypeScript
- **Framework**: Vue 3 (Composition API)
- **UI Library**: Vuetify 3
- **Build Tool**: Vite
- **State Management**: Vue refs + Prism subscriptions
- **Routing**: Vue Router

### Key Concepts

**Prism Protocol:**
- Versioned objects with delta updates
- WebSocket-based subscriptions
- Automatic notification on object changes
- Filter system for data transformation

**Real-time Sync:**
- Global room list object (`global-room-list`)
- Each client subscribes to room list
- Room list updated on room creation
- All subscribers notified automatically

## Troubleshooting

### Servers won't start

```bash
# Check what's using the ports
lsof -i :8000  # Backend
lsof -i :3000  # Frontend

# Kill processes if needed
kill <PID>

# Or use stop scripts
./scripts/stop-all.sh

# Check database
./scripts/status.sh

# Reset if needed
./scripts/db-reset.sh
```

### Tests failing

```bash
# Check servers are running
./scripts/status.sh

# Check health
./scripts/health-check.sh

# View logs for errors
./scripts/logs.sh

# Reset database
./scripts/db-reset.sh

# Restart servers
./scripts/restart-all.sh
```

### Build issues

```bash
# Clean everything
./scripts/clean.sh

# Reinstall and rebuild
./scripts/setup.sh
```

### Database issues

```bash
# Check PostgreSQL is running
psql -U postgres -l

# macOS: Start PostgreSQL
brew services start postgresql@14

# Reset database
./scripts/db-reset.sh
```

## Environment Variables

The scripts use these environment variables (defined in `scripts/config.sh`):

- `BACKEND_PORT=8000`
- `FRONTEND_PORT=3000`
- `DB_NAME=prism_chat`
- `DB_USER=postgres`
- `DB_PASSWORD=postgres`

To override, export before running scripts:
```bash
export BACKEND_PORT=8001
./scripts/start-backend.sh
```

## Development Tips

1. **Use health checks**: Run `./scripts/health-check.sh` frequently
2. **Monitor logs**: Keep `./scripts/logs.sh` running in a terminal
3. **Check status**: Use `./scripts/status.sh` to see what's running
4. **Clean starts**: When in doubt, `./scripts/restart-all.sh`
5. **Test often**: Run `./scripts/test-all.sh` before committing
6. **Use E2E UI mode**: `./scripts/run-e2e.sh --ui` for debugging

## Next Steps

- Add frontend unit tests (Vitest infrastructure ready)
- Add more E2E test scenarios
- Set up CI/CD pipeline
- Add performance testing
- Add database migrations system

## Resources

- Main README: `README.md`
- Project Summary: `PROJECT_SUMMARY.md`
- Backend README: `backend/README.md`
- Protocol Docs: `docs/protocol.md`
- Getting Started: `docs/getting-started.md`
