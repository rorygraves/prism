# Prism: A Versioned Object Synchronization Protocol

Prism is a lightweight, language-agnostic library for real-time object synchronization between distributed clients and servers. It provides automatic delta-based updates, intelligent caching, filtered object views, seamless reconnection handling, and smart request/response hydration.

## Project Structure

```
prism/
├── backend/              # Python implementation (FastAPI + PostgreSQL)
├── frontend/             # TypeScript client and Vue/Vuetify demo
├── scala/                # Scala implementation outline
├── e2e/                  # Playwright end-to-end tests
└── docs/                 # Documentation
```

## Quick Start

**Automated Setup (Recommended)**

```bash
# One-time setup (installs dependencies, builds packages, creates database)
./scripts/setup.sh

# Start both backend and frontend servers
./scripts/start-all.sh

# Run all tests
./scripts/test-all.sh

# Check server status
./scripts/status.sh

# View logs
./scripts/logs.sh

# Stop servers
./scripts/stop-all.sh
```

**See [CLAUDE.md](CLAUDE.md) for detailed development workflows and MCP browser integration.**

### Manual Setup

**Backend (Python)**

```bash
cd backend
poetry install
poetry run uvicorn chat_demo.main:app --reload
```

**Frontend (TypeScript/Vue)**

```bash
cd frontend
pnpm install
cd packages/prism-client && pnpm run build
cd ../prism-vue && pnpm run build
cd ../../chat-demo && pnpm run dev
```

**End-to-End Tests**

```bash
cd e2e
npm install
npm test
```

## Key Features

- **Immutable versioned objects** with automatic delta computation
- **Smart request/response routing** with automatic object reference resolution
- **Filtered views** for security and optimization
- **Protocol-agnostic** transport (WebSocket, SSE, polling)
- **Automatic reconnection** with state reconciliation
- **Intelligent object hydration** based on client state

## Documentation

See the [docs](./docs/) directory for detailed documentation:
- [Protocol Specification](./docs/protocol.md)
- [Python Backend Guide](./backend/README.md)
- [TypeScript Client Guide](./frontend/packages/prism-client/README.md)
- [Chat Demo Tutorial](./docs/chat-demo.md)

## License

MIT
