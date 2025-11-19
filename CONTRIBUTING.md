# Contributing to Prism

Thank you for your interest in contributing to Prism! We welcome contributions of all kinds, from bug fixes to new features to documentation improvements.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Project Structure](#project-structure)
- [Running Tests](#running-tests)
- [Adding a New Language Implementation](#adding-a-new-language-implementation)
- [Submitting Changes](#submitting-changes)
- [Release Process](#release-process)

## Code of Conduct

This project adheres to a Code of Conduct that all contributors are expected to follow. Please be respectful and constructive in all interactions.

## Getting Started

### Prerequisites

To contribute to Prism, you'll need:

- **For Python**: Python 3.11+, Poetry
- **For TypeScript**: Node.js 18+, pnpm
- **For Scala**: JDK 11+, sbt
- **For Testing**: PostgreSQL 14+, Playwright

### One-Time Setup

The project includes comprehensive setup scripts:

```bash
# Clone the repository
git clone https://github.com/rorygraves/prism.git
cd prism

# Run the setup script (installs all dependencies)
./tools/scripts/setup.sh
```

This script will:
- Check for required tools (Node.js, Python, PostgreSQL)
- Install package managers (pnpm, Poetry)
- Install dependencies for all implementations
- Build all packages
- Create the PostgreSQL database
- Install Playwright browsers

## Development Workflow

### Starting Development Servers

```bash
# Start both backend and frontend
./tools/scripts/dev/start-all.sh

# Or start them individually
./tools/scripts/dev/start-backend.sh
./tools/scripts/dev/start-frontend.sh
```

### Viewing Logs

```bash
# View logs from all servers
./tools/scripts/logs.sh

# View only backend logs
./tools/scripts/logs.sh --backend

# View only frontend logs
./tools/scripts/logs.sh --frontend

# Filter logs
./tools/scripts/logs.sh --filter "ERROR"
```

### Making Changes

The project uses hot-reloading for development:

- **Python**: Auto-reloads on code changes (uvicorn --reload)
- **TypeScript/Vue**: Auto-reloads with Vite HMR
- **Scala**: Restart required after changes

After editing `@prism/client` or `@prism/vue` packages:

```bash
cd implementations/typescript/packages/client
pnpm run build

# Restart servers to pick up changes
./tools/scripts/restart-all.sh
```

### Running Tests

```bash
# Run all tests (unit + integration + E2E)
./tools/scripts/test/test-all.sh

# Run specific test suites
./tools/scripts/test/test-backend.sh      # Python unit tests
./tools/scripts/test/test-scala.sh        # Scala unit tests
./tools/scripts/test/test-frontend.sh     # TypeScript unit tests
./tools/scripts/test/run-e2e.sh           # E2E tests

# Run E2E tests in UI mode (for debugging)
./tools/scripts/test/run-e2e.sh --ui

# Run E2E tests and keep servers running
./tools/scripts/test/run-e2e.sh --keep-alive
```

### Code Quality

All implementations have strict quality checks:

#### Python

```bash
cd implementations/python

# Type checking
poetry run mypy prism

# Linting
poetry run ruff check .

# Auto-fix
poetry run ruff check . --fix

# Format
poetry run ruff format .
```

#### TypeScript

```bash
cd implementations/typescript

# Type checking
pnpm run type-check

# Linting
pnpm run lint

# Format
pnpm run format
```

#### Scala

```bash
cd implementations/scala

# Compile
sbt compile

# Format
sbt scalafmt

# Format check
sbt scalafmtCheck
```

## Project Structure

```
prism/
├── spec/                    # Protocol specification (the source of truth)
├── implementations/         # Language implementations
│   ├── python/             # Python backend
│   ├── typescript/         # TypeScript client
│   └── scala/              # Scala backend
├── examples/               # Demo applications
│   └── chat-demo/         # Full-stack chat demo
├── tests/                  # Cross-implementation tests
│   ├── integration/       # Integration tests
│   └── e2e/               # End-to-end tests
├── docs/                   # Documentation
│   ├── getting-started/   # User guides
│   ├── guides/            # Concept guides
│   ├── api/               # API reference (generated)
│   ├── protocol/          # Protocol deep-dive
│   ├── contributing/      # Contributor guides
│   └── examples/          # Code examples
└── tools/                  # Development tools
    ├── scripts/           # Dev/build/test scripts
    ├── ci/                # CI configuration
    └── docker/            # Docker configs
```

### Key Principles

1. **Protocol First**: The spec/ directory contains the authoritative protocol definition
2. **Independent Implementations**: Each language implementation is self-contained and publishable
3. **Demos Separate**: Example apps are separate from core libraries
4. **Comprehensive Testing**: Unit, integration, and E2E tests for all implementations

## Adding a New Language Implementation

Interested in implementing Prism in Rust, Go, Java, or another language? Here's how:

### 1. Create Implementation Directory

```bash
mkdir -p implementations/[language]
cd implementations/[language]
```

### 2. Implement Core Components

Based on the [Protocol Specification](spec/protocol.md), implement:

- **Types**: Message types, versioned objects
- **Delta Computer**: JSON Patch delta computation
- **Object Manager**: Subscription management, caching
- **Request Router**: Request handling with hydration
- **Filter System**: Filter application
- **Storage Adapter**: Interface for data persistence

### 3. Follow Language Conventions

- Use idiomatic code style for the language
- Follow language-specific naming conventions
- Use appropriate type systems (static where available)
- Include comprehensive tests

### 4. Create README

Document:
- Installation instructions
- Quick start example
- API overview
- Integration examples

See [implementations/python/README.md](implementations/python/README.md) for a template.

### 5. Add Integration Tests

Create tests that verify compatibility with existing clients/servers:

```bash
mkdir -p tests/integration/[language]-backend
```

### 6. Submit Pull Request

Open a PR with:
- Implementation code
- Tests (unit + integration)
- Documentation
- Example usage

See our [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md).

## Submitting Changes

### 1. Create a Branch

```bash
git checkout -b feature/your-feature-name
```

Branch naming conventions:
- `feature/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation updates
- `refactor/` - Code refactoring
- `test/` - Test improvements

### 2. Make Your Changes

- Write clear, concise commit messages
- Follow the existing code style
- Add tests for new functionality
- Update documentation as needed

### 3. Run Tests

Before submitting, ensure all tests pass:

```bash
./tools/scripts/test/test-all.sh
```

### 4. Submit Pull Request

1. Push your branch to GitHub
2. Open a Pull Request against `main`
3. Fill out the PR template
4. Wait for review

### Pull Request Guidelines

- **Title**: Clear, descriptive title (e.g., "Add Redis cache support for Python backend")
- **Description**: Explain what and why, not just how
- **Tests**: All new code must have tests
- **Documentation**: Update docs for user-facing changes
- **Breaking Changes**: Clearly mark any breaking changes

## Release Process

### Versioning

Prism follows [Semantic Versioning](https://semver.org/):

- **MAJOR**: Breaking changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes (backward compatible)

### Publishing Workflow

Releases are coordinated across all implementations:

#### 1. Prepare Release

```bash
# Update version numbers
vim implementations/python/pyproject.toml        # Update version
vim implementations/typescript/packages/*/package.json
vim implementations/scala/build.sbt

# Update CHANGELOG
vim CHANGELOG.md
```

#### 2. Create Release PR

```bash
git checkout -b release/v0.2.0
git add .
git commit -m "Prepare v0.2.0 release"
git push origin release/v0.2.0
```

#### 3. Tag Release

After merging the release PR:

```bash
git tag -a v0.2.0 -m "Release v0.2.0"
git push origin v0.2.0
```

#### 4. Publish Packages

GitHub Actions will automatically publish to:
- PyPI (Python)
- npm (TypeScript)
- Maven Central (Scala)

See `.github/workflows/` for CI/CD configuration.

### Package-Specific Publishing

For manual publishing:

**Python:**
```bash
cd implementations/python
poetry build
poetry publish
```

**TypeScript:**
```bash
cd implementations/typescript/packages/client
npm publish

cd ../vue
npm publish
```

**Scala:**
```bash
cd implementations/scala
sbt +publishSigned
sbt sonatypeBundleRelease
```

## Development Tips

### 1. Use Health Checks

```bash
# Check if everything is running
./tools/scripts/status.sh

# Check system health
./tools/scripts/health-check.sh
```

### 2. Monitor Logs

Keep a terminal open with:

```bash
./tools/scripts/logs.sh
```

### 3. Clean Restarts

When in doubt:

```bash
./tools/scripts/restart-all.sh
```

### 4. Fresh Start

To completely reset:

```bash
./tools/scripts/stop-all.sh
./tools/scripts/clean.sh
./tools/scripts/db-reset.sh
./tools/scripts/setup.sh
```

## Getting Help

- **Questions**: Open a [GitHub Discussion](https://github.com/rorygraves/prism/discussions)
- **Bug Reports**: Open a [GitHub Issue](https://github.com/rorygraves/prism/issues)
- **Feature Requests**: Open a [GitHub Issue](https://github.com/rorygraves/prism/issues) with the "enhancement" label

## Recognition

Contributors are recognized in:
- CHANGELOG.md
- Release notes
- GitHub contributors page

Thank you for contributing to Prism! 🎉
