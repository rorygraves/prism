# Prism Integration Tests

Integration tests that test the TypeScript client against a **real backend server**.

These tests verify the full protocol implementation without requiring browser automation (unlike E2E tests).

## Running Tests

### Option 1: Manual Backend Start (Recommended)

```bash
# Terminal 1: Start backend
cd backend
poetry run python -m chat_demo.main

# Terminal 2: Run integration tests
cd frontend/integration-tests
pnpm test
```

### Option 2: Auto-start Backend

```bash
cd frontend/integration-tests
AUTO_START_BACKEND=true pnpm test
```

## What's Tested

- **Connection**: WebSocket connection and reconnection
- **Subscribe/Unsubscribe**: Object subscription lifecycle
- **Request/Response**: Business logic requests with smart hydration
- **UpdateFilter**: Dynamic filter changes on subscriptions
- **Delta Updates**: Incremental object updates
- **Multiple Clients**: Cross-client synchronization
- **Reference Hydration**: Auto-resolution of object references
- **Error Handling**: Protocol error scenarios

## Test Structure

- `setup.ts` - Test utilities and backend management
- `protocol.integration.test.ts` - Full protocol integration tests

## Environment Variables

- `BACKEND_URL` - HTTP URL for backend (default: http://localhost:8000)
- `WS_URL` - WebSocket URL (default: ws://localhost:8000/ws)
- `AUTO_START_BACKEND` - Auto-start backend if not running (default: false)

## Differences from E2E Tests

| Feature | Integration Tests | E2E Tests |
|---------|------------------|-----------|
| Browser | ❌ No | ✅ Yes (Playwright) |
| UI Testing | ❌ No | ✅ Yes |
| Protocol Testing | ✅ Yes | ✅ Yes |
| Speed | ⚡ Fast | 🐢 Slower |
| Setup | Simple | Complex |

Integration tests are perfect for:
- Protocol validation
- Client library testing
- CI/CD pipelines
- Quick feedback loops

E2E tests are better for:
- UI/UX validation
- User workflows
- Visual regression testing
