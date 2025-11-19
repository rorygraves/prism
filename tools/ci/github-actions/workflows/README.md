# GitHub Actions CI/CD

This directory contains GitHub Actions workflows for continuous integration and testing.

## Workflows

### CI Workflow (`ci.yml`)

Runs on every push and pull request to `main` and `develop` branches.

**Jobs:**

1. **Backend Tests**
   - Python 3.11
   - Poetry dependency management
   - Runs pytest with coverage
   - Uploads coverage to Codecov

2. **E2E Tests**
   - Python 3.11 + Node.js 20
   - Installs backend (Poetry) and frontend (pnpm) dependencies
   - Builds frontend packages
   - Installs Playwright with Chromium
   - Runs E2E tests (Chromium only)
   - Uploads test reports and results as artifacts

3. **Lint & Type Check**
   - Python type checking with mypy
   - Python linting with ruff
   - TypeScript compilation checks

## Browser Support in CI

**GitHub Actions**: ✅ **Chromium headless works perfectly**

GitHub Actions runs on standard Ubuntu Linux with proper permissions, so Playwright's Chromium works reliably.

**Firefox**: Not included in CI due to occasional permission issues. Chromium provides sufficient coverage.

## Test Results

- **Backend Tests**: Unit tests with pytest
- **E2E Tests**: 5 test scenarios with Chromium
  - Multi-user chat
  - Multiple rooms
  - Member count updates
  - Connection status
  - Rapid messages

## Artifacts

When tests run, the following artifacts are uploaded:

- **playwright-report**: HTML test report (retained for 30 days)
- **test-results**: Test screenshots and traces (retained for 30 days)

View artifacts in the Actions tab of failed workflow runs.

## Local Testing

To run the same tests locally:

```bash
# Backend tests
cd backend
poetry run pytest -v

# E2E tests
./scripts/run-e2e.sh
```

## Environment Comparison

| Environment | Chromium | Firefox | Notes |
|-------------|----------|---------|-------|
| **GitHub Actions** | ✅ Works | ⚠️ Skip | Standard Linux, full permissions |
| **Local Machine** | ✅ Works | ✅ Works | Recommended for development |
| **Claude Code Cloud** | ❌ Fails | ❌ Fails | Container restrictions |

## Caching

The workflow uses caching for:
- Poetry virtual environments
- pnpm dependencies
- Playwright browsers

This speeds up subsequent runs significantly.

## Adding New Tests

1. Add tests to `e2e/tests/` directory
2. Tests will automatically run in CI on next push
3. Check the Actions tab for results

## Troubleshooting CI Failures

### E2E Tests Failing

Check the uploaded artifacts:
1. Go to Actions tab → Select the failed run
2. Scroll to artifacts section
3. Download `playwright-report` for detailed HTML report
4. Download `test-results` for screenshots/traces

### Dependency Issues

If Poetry or pnpm installation fails:
- Check lock files are committed
- Verify `poetry.lock` and `pnpm-lock.yaml` are up to date
- CI uses `--frozen-lockfile` to ensure reproducibility

### Timeout Issues

E2E tests have a 120-second timeout for server startup. If servers don't start in time:
- Check backend startup logs
- Verify in-memory storage initializes correctly
- Ensure frontend builds successfully
