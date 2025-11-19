# Prism Project Restructuring Plan

## Vision

Transform Prism from a demo-oriented project into a **professional, multi-language synchronization library** that is:
- **Protocol-first**: The specification is the star
- **Developer-friendly**: Easy to install, use, and understand
- **Contributor-friendly**: Clear structure for adding implementations
- **Production-ready**: Publishable to npm, PyPI, Maven Central

## Target Audience

### Library Users (Primary)
- Backend developers integrating Prism into Python/Scala servers
- Frontend developers using TypeScript/Vue client
- Want: Quick installation, clear API, good examples

### Protocol Implementers (Secondary)
- Developers adding new language implementations (Rust, Go, Java, etc.)
- Want: Clear protocol spec, reference implementations, test suite

### Contributors (Tertiary)
- Developers improving existing implementations
- Want: Clear build/test/publish workflows

---

## New Directory Structure

```
prism/
├── 📋 spec/                           # PROTOCOL SPECIFICATION (The Core Truth)
│   ├── protocol.md                    # Human-readable spec
│   ├── messages.schema.json           # JSON Schema for messages
│   ├── examples/                      # Message examples
│   │   ├── subscribe.json
│   │   ├── delta.json
│   │   └── sync.json
│   └── test-suite/                    # Reference test cases
│       ├── delta-computation/
│       └── filter-application/
│
├── 📦 implementations/                # LANGUAGE IMPLEMENTATIONS
│   │
│   ├── python/                        # Python Backend
│   │   ├── pyproject.toml            # name="prism" (publishable)
│   │   ├── README.md                 # Python-specific docs
│   │   ├── prism/                    # Core library
│   │   │   ├── __init__.py
│   │   │   ├── core/                # Types, protocol, delta
│   │   │   ├── server/              # ObjectManager, RequestRouter
│   │   │   ├── storage/             # Storage adapters
│   │   │   ├── filters/             # Filter system
│   │   │   └── cache/               # Caching
│   │   ├── tests/                   # Unit tests
│   │   └── docs/                    # API reference (Sphinx)
│   │
│   ├── typescript/                   # TypeScript Client
│   │   ├── package.json             # Workspace root
│   │   ├── README.md                # TypeScript-specific docs
│   │   ├── packages/
│   │   │   ├── client/              # @prism/client (publishable)
│   │   │   │   ├── package.json
│   │   │   │   ├── src/
│   │   │   │   ├── tests/
│   │   │   │   └── README.md
│   │   │   ├── vue/                 # @prism/vue (publishable)
│   │   │   │   └── ...
│   │   │   └── react/               # @prism/react (future)
│   │   │       └── ...
│   │   └── docs/                    # API reference (TypeDoc)
│   │
│   └── scala/                        # Scala Backend
│       ├── build.sbt                # Multi-project build
│       ├── README.md                # Scala-specific docs
│       ├── core/                    # prism-core (publishable)
│       │   ├── src/
│       │   └── README.md
│       ├── integrations/            # Framework integrations
│       │   ├── http4s/             # prism-http4s (publishable)
│       │   └── play/               # prism-play (publishable)
│       ├── tests/
│       └── docs/                   # API reference (ScalaDoc)
│
├── 🎯 examples/                      # DEMO APPLICATIONS
│   ├── chat-demo/
│   │   ├── README.md                # Demo-specific docs
│   │   ├── backend/                 # Uses prism from PyPI/Maven
│   │   │   ├── python/             # FastAPI + prism
│   │   │   └── scala/              # http4s/Play + prism
│   │   └── frontend/               # Uses @prism/* from npm
│   │       └── vue/                # Vue + @prism/vue
│   └── real-time-dashboard/        # Future: Another demo
│
├── 🧪 tests/                         # CROSS-IMPLEMENTATION TESTS
│   ├── integration/                 # Client vs Server tests
│   │   ├── python-backend/         # TS client → Python server
│   │   └── scala-backend/          # TS client → Scala server
│   └── e2e/                        # End-to-end scenarios
│       └── playwright/
│
├── 📚 docs/                          # DOCUMENTATION
│   ├── index.md                     # Documentation home
│   ├── getting-started/
│   │   ├── README.md
│   │   ├── installation.md         # npm/PyPI/Maven install
│   │   ├── quickstart-python.md
│   │   ├── quickstart-scala.md
│   │   └── quickstart-typescript.md
│   ├── guides/
│   │   ├── concepts.md             # Core concepts
│   │   ├── subscriptions.md
│   │   ├── filters.md
│   │   ├── storage-adapters.md
│   │   ├── error-handling.md
│   │   └── performance-tuning.md
│   ├── api/                        # Generated API docs (published here)
│   │   ├── python/
│   │   ├── typescript/
│   │   └── scala/
│   ├── protocol/                   # Protocol deep-dive
│   │   ├── specification.md        # Detailed spec
│   │   ├── message-types.md
│   │   ├── delta-computation.md
│   │   └── architecture.md
│   ├── contributing/
│   │   ├── README.md
│   │   ├── development.md
│   │   ├── testing.md
│   │   ├── adding-language.md      # How to add a new language
│   │   └── release-process.md
│   └── examples/
│       ├── cookbook.md             # Common patterns
│       ├── react-integration.md
│       └── fastapi-integration.md
│
├── 🛠️ tools/                         # DEVELOPMENT TOOLS
│   ├── scripts/                     # Development scripts
│   │   ├── setup.sh                # One-time setup
│   │   ├── dev/                    # Development helpers
│   │   ├── build/                  # Build scripts
│   │   ├── test/                   # Test runners
│   │   └── release/                # Publishing scripts
│   ├── ci/                         # CI configuration
│   └── docker/                     # Docker configs
│       ├── python.Dockerfile
│       └── scala.Dockerfile
│
├── .github/
│   ├── workflows/
│   │   ├── ci.yml                  # CI for all implementations
│   │   ├── publish-npm.yml         # Publish to npm
│   │   ├── publish-pypi.yml        # Publish to PyPI
│   │   ├── publish-maven.yml       # Publish to Maven Central
│   │   └── docs.yml                # Generate and publish docs
│   ├── ISSUE_TEMPLATE/
│   └── PULL_REQUEST_TEMPLATE.md
│
├── README.md                        # Project overview (library-focused)
├── CONTRIBUTING.md                  # Contributor guide
├── CHANGELOG.md                     # Version history
├── LICENSE                          # MIT License
├── CODE_OF_CONDUCT.md
└── .gitignore
```

---

## Key Design Principles

### 1. Protocol-First Architecture

**Current State**: Protocol spec is in `docs/protocol.md`, not prominent
**New State**: Dedicated `spec/` directory at root level

```
spec/
├── protocol.md              # Human-readable specification
├── messages.schema.json     # Machine-readable schema
├── examples/                # Example messages (JSON)
└── test-suite/              # Reference test cases
```

**Benefits**:
- Protocol is the "source of truth" for all implementations
- Easy for new language implementers to find
- Versioned independently (spec v1.0, impl v0.5)
- Can auto-generate types from schema

### 2. Clear Implementation Boundaries

**Current State**: Mixed structure (backend/, frontend/, backend-scala/)
**New State**: All implementations under `implementations/`

Each implementation:
- Is **independently publishable** (own package.json/pyproject.toml/build.sbt)
- Has **own README** with language-specific docs
- Has **own tests** (but shares integration tests)
- Follows **language conventions** (e.g., Python snake_case, Scala camelCase)

### 3. Demos Separate from Core

**Current State**: chat_demo/ inside backend/
**New State**: All demos in `examples/`

```
examples/chat-demo/
├── backend/
│   ├── python/
│   │   ├── pyproject.toml    # depends on "prism" from PyPI
│   │   └── main.py
│   └── scala/
│       ├── build.sbt         # depends on "com.prism:prism-core"
│       └── Main.scala
└── frontend/
    └── vue/
        ├── package.json      # depends on "@prism/vue" from npm
        └── src/
```

**Benefits**:
- Core library has zero demo code
- Demos use published packages (dogfooding)
- Examples serve as integration tests
- Users see real-world usage

### 4. Documentation for Multiple Audiences

**Current State**: Mixed docs for developers and users
**New State**: Clear separation

| Audience | Location | Content |
|----------|----------|---------|
| **Library Users** | `docs/getting-started/` | Installation, quickstart, guides |
| **API Reference** | `docs/api/` | Auto-generated API docs |
| **Protocol Implementers** | `spec/` + `docs/protocol/` | Spec, architecture, examples |
| **Contributors** | `docs/contributing/` | Development, testing, releasing |

### 5. Monorepo with Independent Publishing

**Structure**:
- Root `package.json` for tooling (Turborepo, changesets)
- Each implementation has own publish config
- Coordinated releases via changesets/Lerna

**Publishing Targets**:
- Python: PyPI (`prism`)
- TypeScript: npm (`@prism/client`, `@prism/vue`, `@prism/react`)
- Scala: Maven Central (`com.prism:prism-core`, `com.prism:prism-http4s`, `com.prism:prism-play`)

---

## Migration Strategy

### Phase 1: Non-Breaking Moves (Safe)

1. **Create new structure** (mkdir)
2. **Copy files** to new locations (keep originals)
3. **Update imports** in copied files
4. **Run tests** on new structure
5. **Delete old structure** only after verification

### Phase 2: Update Configurations

1. **Split Python package** (prism-backend → prism)
2. **Add publish configs** (npm, PyPI, Maven)
3. **Update CI/CD** workflows
4. **Generate API docs**

### Phase 3: Documentation Overhaul

1. **Restructure docs/** for library users
2. **Create getting-started** guides
3. **Write cookbook** examples
4. **Setup docs site** (ReadTheDocs/GitHub Pages)

### Phase 4: Cleanup

1. **Archive `/scala/`** (incomplete outline)
2. **Remove duplicate files**
3. **Update all READMEs**
4. **Create CHANGELOG**

---

## File Mappings (Old → New)

### Python Backend

| Old | New |
|-----|-----|
| `backend/prism/` | `implementations/python/prism/` |
| `backend/tests/` | `implementations/python/tests/` |
| `backend/pyproject.toml` | `implementations/python/pyproject.toml` |
| `backend/chat_demo/` | `examples/chat-demo/backend/python/` |
| `backend/README.md` | `implementations/python/README.md` |

### TypeScript Client

| Old | New |
|-----|-----|
| `frontend/packages/prism-client/` | `implementations/typescript/packages/client/` |
| `frontend/packages/prism-vue/` | `implementations/typescript/packages/vue/` |
| `frontend/chat-demo/` | `examples/chat-demo/frontend/vue/` |
| `frontend/integration-tests/` | `tests/integration/python-backend/` |

### Scala Backend

| Old | New |
|-----|-----|
| `backend-scala/prism-core/` | `implementations/scala/core/` |
| `backend-scala/prism-http4s-demo/` | `implementations/scala/integrations/http4s/` + `examples/chat-demo/backend/scala/http4s/` |
| `backend-scala/prism-play-demo/` | `implementations/scala/integrations/play/` + `examples/chat-demo/backend/scala/play/` |
| `backend-scala/build.sbt` | `implementations/scala/build.sbt` |

### Documentation

| Old | New |
|-----|-----|
| `docs/protocol.md` | `spec/protocol.md` + `docs/protocol/specification.md` |
| `docs/architecture.md` | `docs/protocol/architecture.md` |
| `docs/getting-started.md` | `docs/getting-started/README.md` |
| `docs/development.md` | `docs/contributing/development.md` |
| `docs/testing.md` | `docs/contributing/testing.md` |
| `docs/implementation-guide.md` | `docs/contributing/adding-language.md` |

### Scripts & Tools

| Old | New |
|-----|-----|
| `scripts/` | `tools/scripts/` |
| `e2e/` | `tests/e2e/` |
| `.github/workflows/` | `.github/workflows/` (updated) |

### Archive

| Old | New |
|-----|-----|
| `scala/` | `_archive/scala-outline/` |

---

## Package Renaming

### Python

**Old** (bundled):
```toml
[tool.poetry]
name = "prism-backend"
packages = [
    { include = "prism" },
    { include = "chat_demo" }  # ❌
]
```

**New** (core only):
```toml
[tool.poetry]
name = "prism"
version = "0.1.0"
description = "Versioned object synchronization protocol for Python"
packages = [{ include = "prism" }]

[project.urls]
Homepage = "https://github.com/rorygraves/prism"
Documentation = "https://prism.readthedocs.io"
Repository = "https://github.com/rorygraves/prism"
```

### TypeScript

**Update workspace references**:
```json
{
  "dependencies": {
    "@prism/client": "workspace:*"  // ❌ Only for dev
  }
}
```

**To versioned dependencies** (for publishing):
```json
{
  "dependencies": {
    "@prism/client": "^0.1.0"  // ✅ Real version
  },
  "publishConfig": {
    "access": "public"
  }
}
```

### Scala

**Add Maven Central metadata**:
```scala
// implementations/scala/build.sbt

ThisBuild / organization := "com.prism"
ThisBuild / version := "0.1.0"

// Maven Central publishing
publishTo := sonatypePublishToBundle.value
publishMavenStyle := true

licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
homepage := Some(url("https://github.com/rorygraves/prism"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/rorygraves/prism"),
    "scm:git@github.com:rorygraves/prism.git"
  )
)
developers := List(
  Developer("rorygraves", "Rory Graves", "email@example.com", url("https://github.com/rorygraves"))
)
```

---

## New README Structure

### Root README.md (Library-Focused)

```markdown
# Prism

> A versioned object synchronization protocol with implementations in Python, Scala, and TypeScript

## What is Prism?

Prism is a protocol and library for **real-time synchronization of versioned objects** between clients and servers. It provides:

- **Delta updates** - Send only what changed (JSON Patch)
- **Smart caching** - Three-tier cache for performance
- **Filter system** - Security and data transformation
- **Reference hydration** - Automatic object resolution
- **Reconnection sync** - Never miss an update

## Quick Start

### Python Backend

```bash
pip install prism
```

```python
from prism import ObjectManager, PrismWebSocketHandler

manager = ObjectManager(storage)
handler = PrismWebSocketHandler(manager)
```

[Full Python Guide →](implementations/python/README.md)

### TypeScript Client

```bash
npm install @prism/client
```

```typescript
import { PrismClient } from '@prism/client'

const client = new PrismClient('ws://localhost:8000')
await client.connect()
```

[Full TypeScript Guide →](implementations/typescript/README.md)

### Vue Integration

```bash
npm install @prism/vue
```

```vue
<script setup>
import { usePrismObject } from '@prism/vue'

const room = usePrismObject('room:123')
</script>
```

[Full Vue Guide →](implementations/typescript/packages/vue/README.md)

### Scala Backend

```scala
libraryDependencies += "com.prism" %% "prism-core" % "0.1.0"
```

[Full Scala Guide →](implementations/scala/README.md)

## Documentation

- **[Getting Started](docs/getting-started/)** - Installation and tutorials
- **[Protocol Spec](spec/protocol.md)** - Wire protocol specification
- **[API Reference](docs/api/)** - Generated API documentation
- **[Guides](docs/guides/)** - Concepts, patterns, best practices
- **[Examples](examples/)** - Full demo applications

## Features

- ✅ **Multi-language** - Python, Scala, TypeScript
- ✅ **Production-ready** - Caching, error handling, reconnection
- ✅ **Type-safe** - Full TypeScript/Scala types, Python type hints
- ✅ **Framework-agnostic** - Works with FastAPI, http4s, Play, Vue, React
- ✅ **Tested** - 260+ unit tests, integration tests, E2E tests

## Architecture

```
┌─────────────┐         ┌──────────────┐
│   Clients   │         │   Server     │
│             │         │              │
│  subscribe  ├────────>│ ObjectMgr    │
│             │         │   ├─ Cache   │
│  <delta>    │<────────┤   └─ Storage │
└─────────────┘         └──────────────┘
```

[Read more about architecture →](docs/protocol/architecture.md)

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for:

- Setting up your development environment
- Running tests
- Adding a new language implementation
- Release process

## License

MIT License - see [LICENSE](LICENSE)
```

---

## API Documentation Generation

### Python (Sphinx)

```bash
# implementations/python/docs/conf.py
extensions = ['sphinx.ext.autodoc', 'sphinx_rtd_theme']

# Generate
cd implementations/python
sphinx-build -b html docs/ docs/_build/html
```

**Output**: `implementations/python/docs/_build/html/` → `docs/api/python/`

### TypeScript (TypeDoc)

```bash
# implementations/typescript/packages/client/package.json
{
  "scripts": {
    "docs": "typedoc src/index.ts"
  }
}

# Generate
cd implementations/typescript
npm run docs
```

**Output**: `implementations/typescript/docs/` → `docs/api/typescript/`

### Scala (ScalaDoc)

```scala
// implementations/scala/build.sbt
Compile / doc / scalacOptions ++= Seq(
  "-doc-title", "Prism Scala API",
  "-doc-version", version.value
)

// Generate
sbt core/doc
```

**Output**: `implementations/scala/core/target/scala-2.13/api/` → `docs/api/scala/`

---

## Publishing Workflows

### GitHub Actions: Publish to npm

```yaml
# .github/workflows/publish-npm.yml
name: Publish to npm

on:
  release:
    types: [published]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
          registry-url: 'https://registry.npmjs.org'

      - name: Install and build
        run: |
          cd implementations/typescript
          npm install
          npm run build

      - name: Publish @prism/client
        run: |
          cd implementations/typescript/packages/client
          npm publish
        env:
          NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}

      - name: Publish @prism/vue
        run: |
          cd implementations/typescript/packages/vue
          npm publish
        env:
          NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}
```

### GitHub Actions: Publish to PyPI

```yaml
# .github/workflows/publish-pypi.yml
name: Publish to PyPI

on:
  release:
    types: [published]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-python@v4
        with:
          python-version: '3.11'

      - name: Install Poetry
        run: pip install poetry

      - name: Build and publish
        run: |
          cd implementations/python
          poetry build
          poetry publish
        env:
          POETRY_PYPI_TOKEN_PYPI: ${{ secrets.PYPI_TOKEN }}
```

### GitHub Actions: Publish to Maven Central

```yaml
# .github/workflows/publish-maven.yml
name: Publish to Maven Central

on:
  release:
    types: [published]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Publish
        run: |
          cd implementations/scala
          sbt +publishSigned sonatypeBundleRelease
        env:
          PGP_SECRET: ${{ secrets.PGP_SECRET }}
          SONATYPE_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
          SONATYPE_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
```

---

## Success Criteria

### For Library Users

✅ Can install from standard package managers:
- `pip install prism`
- `npm install @prism/client @prism/vue`
- `libraryDependencies += "com.prism" %% "prism-core" % "0.1.0"`

✅ Can find clear documentation:
- Installation guide in < 5 minutes
- Working example in < 10 minutes
- API reference available online

✅ Can integrate into existing projects:
- FastAPI example
- Spring Boot example
- React example

### For Protocol Implementers

✅ Can find protocol specification easily (root `spec/` directory)

✅ Can see reference implementations (Python, TypeScript, Scala)

✅ Can run conformance tests against new implementation

✅ Documented process for contributing new language

### For Contributors

✅ Can setup dev environment in one command (`./tools/scripts/setup.sh`)

✅ Can run all tests in one command (`./tools/scripts/test/test-all.sh`)

✅ Can build all packages in one command (`./tools/scripts/build/build-all.sh`)

✅ Clear contributing guide explains process

---

## Timeline

### Week 1: Structure + Core
- [ ] Create new directory structure
- [ ] Move Python implementation
- [ ] Move TypeScript implementation
- [ ] Move Scala implementation
- [ ] Update build configs

### Week 2: Documentation
- [ ] Restructure docs/
- [ ] Create getting-started guides
- [ ] Setup API doc generation
- [ ] Write cookbook examples

### Week 3: Publishing
- [ ] Split Python package
- [ ] Add npm publish configs
- [ ] Add Maven Central config
- [ ] Create GitHub Actions workflows

### Week 4: Examples + Polish
- [ ] Reorganize chat demo
- [ ] Create additional examples
- [ ] Update all READMEs
- [ ] Final testing and validation

---

## Next Steps

1. **Review this plan** - Get feedback from team
2. **Create implementation branch** - `restructure/monorepo`
3. **Execute Phase 1** - Non-breaking moves
4. **Test thoroughly** - Ensure nothing breaks
5. **Execute Phases 2-4** - Configuration, docs, cleanup
6. **Publish v1.0** - First official release! 🎉
