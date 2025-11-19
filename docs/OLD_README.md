# Prism: A Versioned Object Synchronization Protocol

[![CI](https://github.com/rorygraves/prism/actions/workflows/ci.yml/badge.svg)](https://github.com/rorygraves/prism/actions/workflows/ci.yml)

Prism is a lightweight, language-agnostic library for real-time object synchronization between distributed clients and servers. It provides automatic delta-based updates, intelligent caching, filtered object views, seamless reconnection handling, and smart request/response hydration.

## Project Structure

```
prism/
├── backend/              # Python implementation (FastAPI + PostgreSQL)
├── backend-scala/        # Scala implementations (http4s + Play Framework)
│   ├── prism-core/      # Core protocol library
│   ├── prism-http4s-demo/  # http4s demo server
│   └── prism-play-demo/    # Play Framework demo server
├── frontend/             # TypeScript client and Vue/Vuetify demo
├── e2e/                  # Playwright end-to-end tests
└── docs/                 # Documentation
```

## Quick Start

> **📖 For AI Assistants:** See [CLAUDE.md](CLAUDE.md) for a comprehensive quick reference guide specifically designed for autonomous development and testing.
>
> **☁️ For Cloud Environments:** See [CLOUD_SETUP.md](CLOUD_SETUP.md) for setup instructions specific to containerized/cloud environments (Claude Code on the Web, Docker, etc.)

### Automated Setup (Recommended)

The `setup.sh` script handles **complete environment setup** including:
- Checking system prerequisites (Node.js, Python, PostgreSQL)
- Auto-installing pnpm and Poetry
- Installing all dependencies (frontend + backend)
- Building packages
- Creating database
- Installing Playwright browsers

```bash
# One-time setup - installs EVERYTHING needed for development
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

**See [CLAUDE.md](CLAUDE.md) for detailed development workflows, testing strategies, and MCP browser integration.**

### Manual Setup

**Backend - Python (FastAPI)**

```bash
cd backend
poetry install
poetry run uvicorn chat_demo.main:app --reload
```

**Backend - Scala (http4s or Play Framework)**

```bash
cd backend-scala

# Run http4s demo (port 8000)
sbt "prismHttp4sDemo/run"

# Or run Play Framework demo (port 9000)
sbt "prismPlayDemo/run"
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
- **Backend Implementations:**
  - [Python Backend Guide (FastAPI)](./backend/README.md)
  - [Scala Backend Guide (http4s + Play)](./backend-scala/README.md)
- [TypeScript Client Guide](./frontend/packages/prism-client/README.md)
- [Chat Demo Tutorial](./docs/chat-demo.md)

## Implementation Status

| Component | Python | Scala | TypeScript |
|-----------|--------|-------|------------|
| Core Protocol | ✅ Complete | ✅ Complete | ✅ Complete |
| WebSocket Server | ✅ FastAPI | ✅ http4s + Play | - |
| Client Library | - | - | ✅ Complete |
| Vue Integration | - | - | ✅ Complete |
| Unit Tests | 24/24 ✅ | 197/197 ✅ | - |
| Integration Tests | 17/17 ✅ | 17/17 ✅ | ✅ Complete |
| Chat Demo | ✅ Complete | ✅ Complete | ✅ Complete |

## License

MIT
