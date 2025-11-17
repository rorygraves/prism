# Getting Started with Prism

This guide will help you get the Prism chat demo up and running.

## Prerequisites

- Python 3.11 or higher
- Node.js 18 or higher
- PostgreSQL 14 or higher
- Poetry (Python package manager)
- npm or yarn

## Setup

### 1. Database Setup

Create a PostgreSQL database for the chat demo:

```bash
# Using psql
createdb prism_chat

# Or with custom credentials
psql -U postgres -c "CREATE DATABASE prism_chat;"
```

### 2. Backend Setup

```bash
cd backend

# Install dependencies
poetry install

# Update database URL if needed
# Edit chat_demo/main.py and update the connection string

# Run type checking
poetry run mypy prism chat_demo

# Run linting
poetry run ruff check prism chat_demo

# Run tests
poetry run pytest

# Start the server
poetry run python -m chat_demo.main
```

The backend will be available at `http://localhost:8000`

### 3. Frontend Setup

```bash
cd frontend

# Install dependencies (this will install all workspace packages)
npm install

# Build Prism client libraries
npm run build

# Start the chat demo
cd chat-demo
npm run dev
```

The frontend will be available at `http://localhost:3000`

## Using the Chat Demo

### Creating a User

1. Navigate to `http://localhost:3000`
2. Click on the "Register" tab
3. Enter a username and display name
4. Click "Create User"

### Creating a Room

1. After registering, switch to the "Rooms" tab
2. Enter a room name
3. Click "Create Room"

### Joining a Room

1. In the "Rooms" tab, you'll see available rooms
2. Click "Join" on any room
3. Start chatting!

### Testing Multi-User Chat

1. Open multiple browser windows (or use private/incognito mode)
2. Register different users in each window
3. Have them join the same room
4. Send messages and watch them sync in real-time!

## Running End-to-End Tests

```bash
cd e2e

# Install dependencies
npm install

# Run Playwright tests
npm test

# Run with UI
npm run test:ui

# Run in headed mode (see browsers)
npm run test:headed
```

## Architecture Overview

### Backend (Python + FastAPI)

- **prism/core**: Core protocol types, delta computation
- **prism/filters**: Filter system for object views
- **prism/server**: Object manager, request router, caching
- **prism/storage**: PostgreSQL storage adapter
- **chat_demo**: Chat application business logic

### Frontend (TypeScript + Vue)

- **@prism/client**: Core Prism client library
- **@prism/vue**: Vue 3 composables
- **chat-demo**: Vue/Vuetify chat application

### Key Features Demonstrated

1. **Real-time Synchronization**: Messages appear instantly across all connected clients
2. **Smart Delta Updates**: Only changed data is transmitted
3. **Object References**: Automatic resolution and hydration
4. **Reconnection Handling**: Automatic state sync after reconnects
5. **Filtered Views**: Server-side and client-side filtering
6. **Multi-room Support**: Users can be in different chat rooms

## Next Steps

- Read the [Protocol Specification](./protocol.md)
- Explore the [Python Backend Guide](../backend/README.md)
- Check out the [TypeScript Client Guide](../frontend/packages/prism-client/README.md)
- Review the [Scala Implementation Outline](../scala/README.md)

## Troubleshooting

### Backend won't start

- Check PostgreSQL is running: `pg_isready`
- Verify database exists: `psql -l | grep prism_chat`
- Check Python version: `python --version` (should be 3.11+)

### Frontend won't connect

- Verify backend is running at `http://localhost:8000`
- Check WebSocket endpoint: `http://localhost:8000/ws`
- Look for console errors in browser developer tools

### Tests failing

- Ensure both backend and frontend are running
- Check ports 3000 and 8000 are available
- Try running tests in headed mode: `npm run test:headed`

## Development Tips

### Backend Development

```bash
# Watch for changes and auto-reload
poetry run uvicorn chat_demo.main:app --reload

# Run with debug logging
LOG_LEVEL=debug poetry run python -m chat_demo.main

# Format code
poetry run ruff format prism chat_demo
```

### Frontend Development

```bash
# Hot reload is enabled by default with Vite
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview
```

## Contributing

When contributing:

1. Run all type checks and linters
2. Write tests for new features
3. Update documentation
4. Follow the existing code style

## Support

For issues or questions:

- Check the [main README](../README.md)
- Review the [Protocol Documentation](./protocol.md)
- Open an issue on GitHub
