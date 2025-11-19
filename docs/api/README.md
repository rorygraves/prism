# API Reference Documentation

This directory contains auto-generated API reference documentation for all Prism implementations.

## Structure

```
api/
├── python/       # Sphinx-generated Python API docs
├── typescript/   # TypeDoc-generated TypeScript API docs
└── scala/        # ScalaDoc-generated Scala API docs
```

## Generating Documentation

### Python (Sphinx)

```bash
cd implementations/python
poetry install --with docs
poetry run sphinx-build -b html docs ../../docs/api/python
```

The generated documentation will be in `docs/api/python/`.

### TypeScript (TypeDoc)

```bash
cd implementations/typescript
pnpm install
pnpm run build  # Build packages first
pnpm run docs
```

The generated documentation will be in `docs/api/typescript/`.

### Scala (ScalaDoc)

```bash
cd implementations/scala
sbt core/doc
mkdir -p ../../docs/api/scala
cp -r core/target/scala-3.3.1/api/* ../../docs/api/scala/
```

The generated documentation will be in `docs/api/scala/`.

### All at Once

Use the GitHub Actions workflow:

```bash
# Trigger the docs workflow
git push origin main
```

The workflow will:
1. Build all API documentation
2. Build the MkDocs site
3. Deploy to GitHub Pages

## Viewing Locally

### Python API

```bash
cd docs/api/python
python -m http.server 8000
# Visit http://localhost:8000
```

### TypeScript API

```bash
cd docs/api/typescript
python -m http.server 8001
# Visit http://localhost:8001
```

### Scala API

```bash
cd docs/api/scala
python -m http.server 8002
# Visit http://localhost:8002
```

### Full Site (MkDocs)

```bash
mkdocs serve
# Visit http://localhost:8000
```

## Notes

- API documentation is auto-generated from code comments
- **Do not manually edit** files in these directories
- To update docs, update the source code comments and regenerate
- Python uses docstrings (Google/NumPy style)
- TypeScript uses TSDoc comments
- Scala uses ScalaDoc comments

## Documentation Standards

### Python Docstrings

```python
def subscribe(self, object_id: str, filter_type: str | None = None) -> None:
    """Subscribe to real-time updates for an object.

    Args:
        object_id: The ID of the object to subscribe to
        filter_type: Optional filter to apply to the object

    Raises:
        ValueError: If object_id is empty
        ConnectionError: If not connected to server

    Example:
        >>> client.subscribe('user:123', filter_type='summary')
    """
```

### TypeScript TSDoc

```typescript
/**
 * Subscribe to real-time updates for an object.
 *
 * @param objectId - The ID of the object to subscribe to
 * @param filterType - Optional filter to apply
 * @throws {Error} If not connected to server
 *
 * @example
 * ```typescript
 * await client.subscribe('user:123', 'summary')
 * ```
 */
subscribe(objectId: string, filterType?: string): Promise<void>
```

### Scala ScalaDoc

```scala
/**
 * Subscribe to real-time updates for an object.
 *
 * @param objectId the ID of the object to subscribe to
 * @param filterType optional filter to apply
 * @throws ConnectionException if not connected to server
 * @example
 * {{{
 * client.subscribe("user:123", Some("summary"))
 * }}}
 */
def subscribe(objectId: String, filterType: Option[String] = None): Unit
```

## Troubleshooting

### Python docs not building

- Ensure you're in the `implementations/python` directory
- Run `poetry install --with docs` first
- Check for syntax errors in docstrings

### TypeScript docs not building

- Ensure packages are built first: `pnpm run build`
- Check for TSDoc syntax errors
- Make sure typedoc.json is correctly configured

### Scala docs not building

- Ensure sbt is installed
- Check for ScalaDoc syntax errors
- Verify you're using correct Scala version (2.13 or 3.3)

## Links

- [Sphinx Documentation](https://www.sphinx-doc.org/)
- [TypeDoc Documentation](https://typedoc.org/)
- [ScalaDoc Guide](https://docs.scala-lang.org/style/scaladoc.html)
