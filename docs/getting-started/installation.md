# Installation

Prism provides packages for Python, TypeScript, and Scala. Choose the installation method for your language.

## Python

### Requirements

- Python 3.11 or later
- pip or Poetry

### Using pip

```bash
pip install prism
```

### Using Poetry

```bash
poetry add prism
```

### Verify Installation

```python
python -c "import prism; print(prism.__version__)"
```

## TypeScript

### Requirements

- Node.js 18 or later
- npm, yarn, or pnpm

### Client Library

```bash
# Using npm
npm install @prism/client

# Using yarn
yarn add @prism/client

# Using pnpm
pnpm add @prism/client
```

### Vue Integration

```bash
# Using npm
npm install @prism/vue

# Using yarn
yarn add @prism/vue

# Using pnpm
pnpm add @prism/vue
```

Note: `@prism/vue` requires Vue 3.3 or later.

### React Integration

Coming soon! See [ROADMAP](../roadmap.md) for status.

### Verify Installation

```typescript
import { PrismClient } from '@prism/client'
console.log('Prism client loaded successfully!')
```

## Scala

### Requirements

- Scala 2.13 or 3.3
- sbt 1.9 or later

### Core Library

Add to your `build.sbt`:

```scala
libraryDependencies += "com.prism" %% "prism-core" % "0.1.0"
```

### http4s Integration

```scala
libraryDependencies += "com.prism" %% "prism-http4s" % "0.1.0"
```

### Play Framework Integration

```scala
libraryDependencies += "com.prism" %% "prism-play" % "0.1.0"
```

### Verify Installation

```scala
import prism.core.Types._
println("Prism loaded successfully!")
```

## Development Installation

If you want to contribute to Prism or run from source:

### Clone Repository

```bash
git clone https://github.com/rorygraves/prism.git
cd prism
```

### Setup

```bash
./tools/scripts/setup.sh
```

This will:
- Check for required tools (Node.js, Python, PostgreSQL)
- Install package managers (pnpm, Poetry)
- Install dependencies for all implementations
- Build all packages
- Create the PostgreSQL database
- Install Playwright browsers for E2E tests

See [Development Guide](../contributing/development.md) for more details.

## Next Steps

- [Python Quickstart](quickstart-python.md)
- [TypeScript Quickstart](quickstart-typescript.md)
- [Scala Quickstart](quickstart-scala.md)
- [Vue Quickstart](quickstart-vue.md)
