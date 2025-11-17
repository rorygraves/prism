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

### Backend (Python)

```bash
cd backend
poetry install
poetry run uvicorn chat_demo.main:app --reload
```

### Frontend (TypeScript/Vue)

```bash
cd frontend/chat-demo
npm install
npm run dev
```

### End-to-End Tests

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
