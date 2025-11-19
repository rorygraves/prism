# Prism

> **A versioned object synchronization protocol with implementations in Python, Scala, and TypeScript**

[![CI Status](https://github.com/rorygraves/prism/workflows/CI/badge.svg)](https://github.com/rorygraves/prism/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Prism is a protocol and library for **real-time synchronization of versioned objects** between clients and servers. It provides efficient delta updates, smart caching, filter-based security, and automatic reconnection handling.

## Why Prism?

- **Language Agnostic Protocol**: Works across Python, Scala, and TypeScript with identical wire format
- **Efficient**: Send only what changed using JSON Patch deltas
- **Smart Caching**: Three-tier cache system for maximum performance
- **Filter System**: Built-in security and data transformation
- **Production Ready**: Automatic reconnection, error handling, comprehensive tests

## Quick Start

### Python Backend

Install from PyPI:

```bash
pip install prism
```

Create a simple server:

```python
from prism import ObjectManager, PrismWebSocketHandler, MemoryStorage

# Setup storage and manager
storage = MemoryStorage()
manager = ObjectManager(storage)

# Use with FastAPI
from fastapi import FastAPI, WebSocket

app = FastAPI()
handler = PrismWebSocketHandler(manager)

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await handler.handle_connection(websocket)
```

[Full Python Guide →](implementations/python/README.md)

### TypeScript Client

Install from npm:

```bash
npm install @prism/client
```

Connect and subscribe:

```typescript
import { PrismClient } from '@prism/client'

const client = new PrismClient('ws://localhost:8000/ws')
await client.connect()

// Subscribe to an object
await client.subscribe('user:123', (user) => {
  console.log('User updated:', user)
})
```

[Full TypeScript Guide →](implementations/typescript/README.md)

### Vue Integration

Install the Vue composables:

```bash
npm install @prism/vue
```

Use in your components:

```vue
<script setup>
import { usePrismObject } from '@prism/vue'

const room = usePrismObject('room:123')
</script>

<template>
  <div v-if="room">
    <h1>{{ room.name }}</h1>
    <p>{{ room.description }}</p>
  </div>
</template>
```

[Full Vue Guide →](implementations/typescript/packages/vue/README.md)

### Scala Backend

Add to your `build.sbt`:

```scala
libraryDependencies += "com.prism" %% "prism-core" % "0.1.0"

// For http4s integration:
libraryDependencies += "com.prism" %% "prism-http4s" % "0.1.0"

// For Play Framework integration:
libraryDependencies += "com.prism" %% "prism-play" % "0.1.0"
```

[Full Scala Guide →](implementations/scala/README.md)

## Features

### Core Protocol Features

- **Versioned Objects**: Every object has an immutable version number
- **Delta Updates**: Efficient JSON Patch deltas sent over WebSocket
- **Reference Hydration**: Automatic resolution of object references
- **Filter System**: Security and data transformation at the protocol level
- **Reconnection Sync**: Automatic state reconciliation after disconnects

### Performance Features

- **Three-Tier Caching**:
  - Version cache: Full object by version
  - Delta cache: Pre-computed deltas
  - Filter cache: Filtered results

- **Efficiency Checks**: Automatic fallback to full object when delta is larger

### Implementation Features

| Feature | Python | TypeScript | Scala |
|---------|--------|------------|-------|
| Core Protocol | ✅ | ✅ | ✅ |
| Delta Computation | ✅ | ✅ | ✅ |
| Filter System | ✅ | ✅ | ✅ |
| Caching | ✅ | ✅ | ✅ |
| PostgreSQL Storage | ✅ | - | 📋 Planned |
| Memory Storage | ✅ | - | ✅ |
| FastAPI Integration | ✅ | - | - |
| http4s Integration | - | - | ✅ |
| Play Integration | - | - | ✅ |
| Vue Composables | - | ✅ | - |
| React Hooks | - | 📋 Planned | - |

## Architecture

```
┌─────────────────┐         ┌──────────────────────┐
│   Clients       │         │   Server             │
│                 │         │                      │
│  subscribe()    ├────────>│  ObjectManager       │
│                 │         │    ├─ Version Cache  │
│  <fullObject>   │<────────┤    ├─ Delta Cache    │
│                 │         │    ├─ Filter Cache   │
│  <delta>        │<────────┤    └─ Storage        │
│                 │         │                      │
│  request()      ├────────>│  RequestRouter       │
│  <response>     │<────────┤    └─ Hydration      │
│                 │         │                      │
│  sync()         ├────────>│  Sync Handler        │
│  <delta>        │<────────┤    └─ Delta Compute  │
└─────────────────┘         └──────────────────────┘
```

[Read more about architecture →](docs/protocol/architecture.md)

## Protocol Specification

The Prism protocol is language-agnostic and well-documented. See the [Protocol Specification](spec/protocol.md) for details on:

- Message types and formats
- Delta computation algorithm
- Filter application
- Reconnection sync process
- Error handling

## Documentation

- **Getting Started**
  - [Installation Guide](docs/getting-started/installation.md)
  - [Python Quickstart](docs/getting-started/quickstart-python.md)
  - [TypeScript Quickstart](docs/getting-started/quickstart-typescript.md)
  - [Scala Quickstart](docs/getting-started/quickstart-scala.md)

- **Guides**
  - [Core Concepts](docs/guides/concepts.md)
  - [Subscriptions](docs/guides/subscriptions.md)
  - [Filters](docs/guides/filters.md)
  - [Storage Adapters](docs/guides/storage-adapters.md)
  - [Error Handling](docs/guides/error-handling.md)
  - [Performance Tuning](docs/guides/performance-tuning.md)

- **API Reference**
  - [Python API](docs/api/python/)
  - [TypeScript API](docs/api/typescript/)
  - [Scala API](docs/api/scala/)

- **Protocol**
  - [Protocol Specification](spec/protocol.md)
  - [Message Types](docs/protocol/message-types.md)
  - [Delta Computation](docs/protocol/delta-computation.md)
  - [Architecture](docs/protocol/architecture.md)

- **Examples**
  - [Chat Demo](examples/chat-demo/) - Full-stack real-time chat application
  - [Cookbook](docs/examples/cookbook.md) - Common patterns and recipes

## Examples

### Full-Stack Chat Demo

The [chat demo](examples/chat-demo/) demonstrates a complete real-time chat application:

- **Backend**: Python (FastAPI) or Scala (http4s/Play)
- **Frontend**: Vue 3 + @prism/vue
- **Features**: User management, room creation, real-time messaging

```bash
# Run the demo
cd examples/chat-demo
docker-compose up
```

Visit http://localhost:3000 to try it out!

## Contributing

We welcome contributions! Whether you're:

- Fixing a bug
- Improving documentation
- Adding a new feature
- Implementing support for a new language

Please see our [Contributing Guide](CONTRIBUTING.md) to get started.

### Adding a New Language Implementation

Interested in implementing Prism in Rust, Go, Java, or another language? See our guide on [Adding a New Language Implementation](docs/contributing/adding-language.md).

## Testing

Prism has comprehensive test coverage:

- **Unit Tests**: 260+ tests across all implementations
- **Integration Tests**: Client vs server compatibility tests
- **End-to-End Tests**: Full application scenarios with Playwright

```bash
# Run all tests
./tools/scripts/test/test-all.sh

# Run Python tests
cd implementations/python && poetry run pytest

# Run TypeScript tests
cd implementations/typescript && pnpm test

# Run Scala tests
cd implementations/scala && sbt test

# Run E2E tests
cd tests/e2e && npm test
```

## Performance

Prism is designed for production use with excellent performance characteristics:

- **Delta Efficiency**: 90%+ reduction in payload size for typical updates
- **Caching**: Sub-millisecond cache hits for versioned objects
- **Throughput**: Handles 1000+ concurrent WebSocket connections per server
- **Latency**: < 10ms end-to-end update propagation on local network

See [Performance Tuning Guide](docs/guides/performance-tuning.md) for optimization tips.

## Project Status

Prism is currently in **beta** (v0.1.0). The protocol is stable, but we're still refining the APIs based on feedback.

- ✅ Protocol: Stable
- ✅ Python Backend: Production-ready
- ✅ TypeScript Client: Production-ready
- ✅ Scala Backend: Production-ready
- ✅ Vue Integration: Production-ready
- 📋 React Integration: Planned
- 📋 PostgreSQL for Scala: Planned
- 📋 Redis Cache: Planned

See [ROADMAP.md](ROADMAP.md) for future plans.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Community

- **GitHub Issues**: [Bug reports and feature requests](https://github.com/rorygraves/prism/issues)
- **Discussions**: [Questions and ideas](https://github.com/rorygraves/prism/discussions)

## Acknowledgments

Prism builds on the shoulders of giants:

- [JSON Patch (RFC 6902)](https://tools.ietf.org/html/rfc6902) for delta computation
- [FastAPI](https://fastapi.tiangolo.com/) for Python WebSocket support
- [http4s](https://http4s.org/) and [Play Framework](https://www.playframework.com/) for Scala backends
- [Vue 3](https://vuejs.org/) for reactive frontend integration

---

**Ready to get started?** Choose your language and follow the quickstart guide:
- [Python Quickstart](docs/getting-started/quickstart-python.md)
- [TypeScript Quickstart](docs/getting-started/quickstart-typescript.md)
- [Scala Quickstart](docs/getting-started/quickstart-scala.md)
