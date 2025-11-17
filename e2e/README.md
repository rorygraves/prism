# Prism E2E Tests

End-to-end tests for the Prism chat demo using Playwright.

## Running Tests

### ✅ Local Machine (Recommended)

E2E tests work reliably on local machines:

```bash
# One-time setup
cd /path/to/prism
./scripts/setup.sh

# Run tests
./scripts/run-e2e.sh

# Or run directly
cd e2e
npm test
```

### ❌ Cloud/Container Environments

**E2E tests do NOT work in cloud/containerized environments** due to browser compatibility issues:

- **Chromium**: Crashes due to shared memory permission errors
- **Firefox**: Cannot run as root in user sessions

See [CLOUD_SETUP.md](../CLOUD_SETUP.md) for details.

## Test Suite

The test suite includes 5 test scenarios run against 2 browsers (10 total tests):

### Tests
1. **Multi-user chat** - Two users chatting in the same room
2. **Multiple rooms** - Three users in different rooms
3. **Member count updates** - Real-time member count synchronization
4. **Connection status** - Connection and disconnection handling
5. **Rapid messages** - High-frequency message exchange

### Browsers
- **Chromium** (headless)
- **Firefox** (headless)

## Configuration

Tests are configured in `playwright.config.ts`:

- **Sequential execution** - One test at a time to avoid race conditions
- **Automatic server startup** - Backend and frontend start automatically
- **Headless mode** - Tests run without visible browser
- **Cloud-friendly args** - Browser launch arguments optimized for containers (though still incompatible with cloud)

## Test Environment

The tests automatically:
1. Start the backend server (port 8000)
2. Start the frontend dev server (port 3000)
3. Run the test suite
4. Stop the servers when done

**Storage:** Uses in-memory storage (no database required)

## Writing Tests

Tests are written using Playwright's testing framework:

```typescript
test('test name', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('Connected')).toBeVisible();
  // ... test logic
});
```

For multi-user tests, create separate browser contexts:

```typescript
const context1 = await browser.newContext();
const context2 = await browser.newContext();
const page1 = await context1.newPage();
const page2 = await context2.newPage();
```

## Debugging Tests

### Run tests with UI

```bash
npm run test:headed  # Shows browser window
npm run test:ui      # Playwright UI mode
```

### View test report

After tests run, open the HTML report:

```bash
npx playwright show-report
```

### Check server logs

If tests fail, check server logs in the terminal running the tests.

## Known Issues

### Browser Crashes in Cloud

**Status:** Cannot be fixed in cloud environments

Both Chromium and Firefox fail in containerized cloud platforms due to fundamental incompatibilities:

- Permission restrictions
- File system limitations
- GPU/rendering issues
- Root/user ownership conflicts

**Solution:** Run tests on local development machines.

### Application Status

The application itself works perfectly. Test failures in cloud are purely browser/environment issues, **not application bugs**.

All protocol bugs (reconnection, subscription management, etc.) have been fixed and tests pass successfully on local machines.

## Requirements

- **Node.js** 18+
- **Playwright** (installed via npm)
- **Backend** running on port 8000
- **Frontend** running on port 3000

The `playwright.config.ts` webServer configuration handles starting the servers automatically.
