# Testing Guide

This guide covers testing strategies, writing tests, and running tests for the Prism Chat Demo project.

## Table of Contents

- [Overview](#overview)
- [Test Pyramid](#test-pyramid)
- [Backend Unit Tests](#backend-unit-tests)
- [Frontend Unit Tests](#frontend-unit-tests)
- [End-to-End Tests](#end-to-end-tests)
- [Running Tests](#running-tests)
- [Writing Tests](#writing-tests)
- [Continuous Integration](#continuous-integration)

## Overview

The project uses a comprehensive testing strategy with three levels:

1. **Unit Tests** - Test individual components in isolation
2. **Integration Tests** - Test component interactions
3. **End-to-End Tests** - Test complete user workflows

### Test Stack

- **Backend**: pytest, pytest-asyncio, pytest-cov
- **Frontend**: Vitest (infrastructure ready, tests to be added)
- **E2E**: Playwright with TypeScript

## Test Pyramid

```
           /\
          /  \
         / E2E \          5 scenarios (Playwright)
        /------\
       /        \
      /Integration\       (Future)
     /------------\
    /              \
   / Unit Tests     \     24 tests (Backend)
  /------------------\    0 tests (Frontend - TBD)
```

## Backend Unit Tests

### Current Coverage

24 unit tests covering:
- Delta computation (JSON Patch generation)
- Filter system (default filter)
- LRU cache implementation

### Running Backend Tests

```bash
# Run all backend tests with coverage
./scripts/test-backend.sh

# Or manually
cd backend
poetry run pytest

# With coverage report
poetry run pytest --cov=prism --cov=chat_demo --cov-report=term-missing

# Run specific test file
poetry run pytest tests/test_delta.py

# Run specific test
poetry run pytest tests/test_delta.py::test_compute_delta_add_field

# View coverage report
open htmlcov/index.html
```

### Test Structure

```
backend/tests/
├── __init__.py
├── test_delta.py      # JSON Patch delta computation
├── test_filters.py    # Filter system
└── test_cache.py      # LRU cache
```

### Writing Backend Tests

**Example test:**

```python
# backend/tests/test_handler.py
import pytest
from chat_demo.handler import ChatBusinessHandler
from chat_demo.models import CreateUserRequest

@pytest.mark.asyncio
async def test_create_user(storage, object_manager):
    """Test user creation."""
    handler = ChatBusinessHandler(storage, object_manager)

    # Create user
    request = CreateUserRequest(
        username="testuser",
        display_name="Test User",
        avatar_url=None
    )
    result = await handler.create_user(request)

    # Verify result
    assert "user" in result
    assert result["user"]["id"].startswith("user-")

    # Verify stored in database
    user_obj = await storage.get_current(result["user"]["id"])
    assert user_obj is not None
    assert user_obj.data["username"] == "testuser"
```

**Fixtures:**

```python
# backend/tests/conftest.py
import pytest
from prism.storage.postgres import PostgresStorageAdapter
from prism.server.object_manager import PrismObjectManager

@pytest.fixture
async def storage():
    """Create test database storage."""
    storage = await PostgresStorageAdapter.create(
        "postgresql+asyncpg://postgres:postgres@localhost/prism_test"
    )
    yield storage
    await storage.close()

@pytest.fixture
def object_manager(storage):
    """Create object manager."""
    from prism.filters.common import create_default_registry
    filters = create_default_registry()
    return PrismObjectManager(storage, filters)
```

### Test Database

For tests that require a database:

```bash
# Create test database
psql -U postgres -c "CREATE DATABASE prism_test;"

# Drop test database
psql -U postgres -c "DROP DATABASE prism_test;"
```

## Frontend Unit Tests

### Status

Frontend unit test infrastructure is ready (Vitest configured) but no tests have been written yet.

### Running Frontend Tests

```bash
# Check for tests (currently shows warning)
./scripts/test-frontend.sh

# Or manually
cd frontend
pnpm test run
```

### Writing Frontend Tests

**Example component test:**

```typescript
// frontend/packages/prism-client/src/client.test.ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { PrismClient } from './client';

describe('PrismClient', () => {
  let client: PrismClient;
  let mockWebSocket: any;

  beforeEach(() => {
    // Mock WebSocket
    mockWebSocket = {
      send: vi.fn(),
      close: vi.fn(),
      readyState: WebSocket.OPEN
    };
    global.WebSocket = vi.fn(() => mockWebSocket) as any;

    client = new PrismClient('ws://localhost:8000/ws');
  });

  afterEach(() => {
    client.disconnect();
  });

  it('should connect to WebSocket', async () => {
    await client.connect();
    expect(client.isConnected()).toBe(true);
  });

  it('should subscribe to object', async () => {
    await client.connect();
    await client.subscribe('obj-123', 'default');

    expect(mockWebSocket.send).toHaveBeenCalledWith(
      expect.stringContaining('"type":"subscribe"')
    );
  });

  it('should handle object updates', async () => {
    await client.connect();

    const callback = vi.fn();
    client.watch('obj-123', callback);

    // Simulate receiving update
    const message = {
      type: 'object_update',
      object_id: 'obj-123',
      version: 2,
      data: { name: 'Updated' }
    };
    mockWebSocket.onmessage({ data: JSON.stringify(message) });

    expect(callback).toHaveBeenCalledWith({ name: 'Updated' });
  });
});
```

**Example composable test:**

```typescript
// frontend/packages/prism-vue/src/composables.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { usePrismObject, PrismClientKey } from './composables';
import { PrismClient } from '@prism/client';

describe('usePrismObject', () => {
  let mockClient: PrismClient;

  beforeEach(() => {
    mockClient = new PrismClient('ws://localhost:8000/ws');
  });

  it('should subscribe to object on mount', async () => {
    const wrapper = mount({
      setup() {
        const { data, loading } = usePrismObject('obj-123');
        return { data, loading };
      },
      template: '<div></div>'
    }, {
      global: {
        provide: {
          [PrismClientKey as symbol]: mockClient
        }
      }
    });

    expect(wrapper.vm.loading).toBe(true);
    // ... more assertions
  });
});
```

### Test Utilities

```typescript
// frontend/packages/prism-client/src/test-utils.ts
export function createMockPrismClient(): PrismClient {
  const client = new PrismClient('ws://test');
  // Mock methods
  client.connect = vi.fn().mockResolvedValue(undefined);
  client.subscribe = vi.fn().mockResolvedValue(undefined);
  return client;
}

export function createMockWebSocket(): MockWebSocket {
  return {
    send: vi.fn(),
    close: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    readyState: WebSocket.OPEN
  };
}
```

## End-to-End Tests

### Current Tests

5 comprehensive scenarios in `e2e/tests/multi-user-chat.spec.ts`:

1. **Two-user chat** - Basic chat functionality
2. **Three-user multi-room** - Multiple users across rooms
3. **Real-time member updates** - Subscription to room changes
4. **Connection status** - Handling disconnections
5. **Rapid message exchange** - Performance and reliability

### Running E2E Tests

```bash
# Run all E2E tests (auto-starts/stops servers)
./scripts/run-e2e.sh

# Keep servers running after tests
./scripts/run-e2e.sh --keep-alive

# Run in UI mode (visual debugging)
./scripts/run-e2e.sh --ui

# Run specific test
cd e2e
npm test -- tests/multi-user-chat.spec.ts

# Run in headed mode (see browser)
cd e2e
npm run test:headed

# Debug mode (step through)
cd e2e
npm run test:debug
```

### Test Structure

```
e2e/
├── tests/
│   └── multi-user-chat.spec.ts
├── playwright.config.ts
├── package.json
└── README.md
```

### Writing E2E Tests

**Example test:**

```typescript
// e2e/tests/room-creation.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Room Creation', () => {
  test('user can create and see room', async ({ page }) => {
    // Navigate to app
    await page.goto('http://localhost:3000');

    // Login
    await page.fill('input[type="text"]', 'testuser');
    await page.click('button:has-text("Start Chatting")');

    // Wait for rooms list
    await expect(page.locator('text=Chat Rooms')).toBeVisible();

    // Create room
    await page.fill('input[label="Create a new room"]', 'Test Room');
    await page.click('button:has-text("Create")');

    // Verify room appears
    await expect(page.locator('text=Test Room')).toBeVisible();
    await expect(page.locator('text=1 members')).toBeVisible();
  });

  test('room appears in other windows', async ({ browser }) => {
    // Create two contexts (independent sessions)
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();

    const page1 = await context1.newPage();
    const page2 = await context2.newPage();

    // User 1: Login and create room
    await page1.goto('http://localhost:3000');
    await page1.fill('input[type="text"]', 'user1');
    await page1.click('button:has-text("Start Chatting")');
    await page1.fill('input[label="Create a new room"]', 'Shared Room');
    await page1.click('button:has-text("Create")');

    // User 2: Login
    await page2.goto('http://localhost:3000');
    await page2.fill('input[type="text"]', 'user2');
    await page2.click('button:has-text("Start Chatting")');

    // Verify room appears in page2 (real-time sync)
    await expect(page2.locator('text=Shared Room')).toBeVisible();

    await context1.close();
    await context2.close();
  });
});
```

**Helper functions:**

```typescript
// e2e/tests/helpers.ts
export async function loginUser(page, username: string) {
  await page.goto('http://localhost:3000');
  await page.fill('input[type="text"]', username);
  await page.click('button:has-text("Start Chatting")');
  await page.waitForSelector('text=Chat Rooms');
}

export async function createRoom(page, roomName: string) {
  await page.fill('input[label="Create a new room"]', roomName);
  await page.click('button:has-text("Create")');
  await page.waitForSelector(`text=${roomName}`);
}

export async function sendMessage(page, message: string) {
  await page.fill('textarea[placeholder="Type a message..."]', message);
  await page.click('button:has-text("Send")');
}
```

### Page Object Model

```typescript
// e2e/tests/pages/HomePage.ts
export class HomePage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('http://localhost:3000');
  }

  async login(username: string) {
    await this.page.fill('input[type="text"]', username);
    await this.page.click('button:has-text("Start Chatting")');
  }

  async createRoom(roomName: string) {
    await this.page.fill('input[label="Create a new room"]', roomName);
    await this.page.click('button:has-text("Create")');
  }

  async getRoomCount() {
    return await this.page.locator('.room-item').count();
  }
}

// Usage
const homePage = new HomePage(page);
await homePage.goto();
await homePage.login('testuser');
await homePage.createRoom('Test Room');
expect(await homePage.getRoomCount()).toBe(1);
```

## Running All Tests

```bash
# Run complete test suite
./scripts/test-all.sh

# This runs:
# 1. Backend unit tests (pytest)
# 2. Frontend unit tests (vitest)
# 3. E2E tests (playwright)

# View summary of all results
```

## Test Data Management

### Backend

Use fixtures for test data:

```python
@pytest.fixture
def sample_user():
    return User(
        id="user-test123",
        username="testuser",
        display_name="Test User",
        avatar_url=None
    )

@pytest.fixture
def sample_room(sample_user):
    return ChatRoom(
        id="room-test123",
        name="Test Room",
        description="Test room description",
        member_ids=[sample_user.id],
        created_by=sample_user.id
    )
```

### Frontend

Use factory functions:

```typescript
export function createMockUser(overrides = {}) {
  return {
    id: 'user-123',
    username: 'testuser',
    display_name: 'Test User',
    avatar_url: null,
    ...overrides
  };
}

export function createMockRoom(overrides = {}) {
  return {
    id: 'room-123',
    name: 'Test Room',
    description: 'Test Description',
    member_ids: ['user-123'],
    created_by: 'user-123',
    ...overrides
  };
}
```

### E2E

Use setup/teardown:

```typescript
test.beforeEach(async ({ page }) => {
  // Reset database (if needed)
  // await resetDatabase();

  // Navigate to clean state
  await page.goto('http://localhost:3000');
});

test.afterEach(async ({ page }) => {
  // Cleanup (if needed)
});
```

## Continuous Integration

### GitHub Actions (Future)

Example workflow:

```yaml
# .github/workflows/test.yml
name: Tests

on: [push, pull_request]

jobs:
  backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:14
        env:
          POSTGRES_PASSWORD: postgres
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-python@v4
        with:
          python-version: '3.11'
      - name: Install dependencies
        run: |
          cd backend
          pip install poetry
          poetry install
      - name: Run tests
        run: |
          cd backend
          poetry run pytest --cov

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v3
        with:
          node-version: 18
          cache: 'pnpm'
      - name: Install dependencies
        run: |
          cd frontend
          pnpm install
      - name: Build packages
        run: |
          cd frontend
          cd packages/prism-client && pnpm run build
          cd ../prism-vue && pnpm run build
      - name: Run tests
        run: |
          cd frontend
          pnpm test run

  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - name: Setup
        run: ./scripts/setup.sh
      - name: Run E2E tests
        run: ./scripts/run-e2e.sh
      - uses: actions/upload-artifact@v3
        if: always()
        with:
          name: playwright-report
          path: e2e/playwright-report/
```

## Best Practices

### General

- **Run tests frequently** - After every change
- **Write tests first** - TDD when possible
- **Keep tests fast** - Unit tests < 1s, E2E < 30s
- **Test one thing** - Each test should verify one behavior
- **Use descriptive names** - Test names should explain what they test
- **Clean up after tests** - Don't leave test data behind
- **Mock external dependencies** - Database, APIs, WebSocket

### Backend

- Use `pytest` fixtures for setup/teardown
- Test both happy path and error cases
- Use `pytest.mark.asyncio` for async tests
- Test database interactions with transactions
- Use coverage to find untested code

### Frontend

- Test component behavior, not implementation
- Mock Prism client in component tests
- Test user interactions (clicks, typing)
- Test reactive updates
- Use Vue Test Utils for component testing

### E2E

- Test complete user workflows
- Use page objects for reusability
- Take screenshots on failure
- Test in realistic scenarios
- Don't test implementation details

## Debugging Tests

### Backend

```bash
# Run with verbose output
cd backend
poetry run pytest -v

# Run with print statements
poetry run pytest -s

# Run specific test with debugging
poetry run pytest tests/test_delta.py::test_compute_delta_add_field -v -s

# Use pdb debugger
# Add: import pdb; pdb.set_trace()
poetry run pytest --pdb
```

### Frontend

```bash
# Run in watch mode
cd frontend
pnpm test watch

# Run specific test
pnpm test run client.test.ts

# Debug in browser
pnpm test --ui
```

### E2E

```bash
# Run in UI mode (visual debugging)
./scripts/run-e2e.sh --ui

# Run in headed mode (see browser)
cd e2e
npm run test:headed

# Debug specific test
npm run test:debug -- tests/multi-user-chat.spec.ts

# Take screenshot on failure (automatic)
# Screenshots saved in e2e/test-results/
```

## Coverage Goals

- **Backend**: 80%+ line coverage
- **Frontend**: 70%+ line coverage (when tests added)
- **E2E**: Cover all critical user workflows

## Additional Resources

- [pytest documentation](https://docs.pytest.org/)
- [Vitest documentation](https://vitest.dev/)
- [Playwright documentation](https://playwright.dev/)
- [Vue Test Utils](https://test-utils.vuejs.org/)
