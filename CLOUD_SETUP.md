# Cloud Environment Setup Guide

This document describes the setup process for running the Prism chat demo in a cloud/container environment (specifically Claude Code on the Web).

## Automated Setup

The project includes a **startSession hook** that automatically configures the environment when a new Claude Code session starts:

```
.claude/hooks/startSession.sh
```

This hook will:
1. Start and configure PostgreSQL 16
2. Create the `prism_chat` database
3. Install Python dependencies (Poetry)
4. Install Node dependencies (pnpm)
5. Build frontend packages
6. Install Playwright and Chromium browser

## Manual Setup Steps

If you need to manually set up the environment, follow these steps:

### 1. PostgreSQL Configuration

The cloud environment includes PostgreSQL 16, but it needs configuration:

```bash
# Start PostgreSQL
pg_ctlcluster 16 main start

# Configure trust authentication (for local development)
sudo sed -i 's/^local.*all.*all.*/local   all             all                                     trust/' /etc/postgresql/16/main/pg_hba.conf
pg_ctlcluster 16 main reload

# Create database
psql -U postgres -c "CREATE DATABASE prism_chat;"
```

**Why trust authentication?**
In the cloud environment, we use `trust` authentication for local connections to simplify development. This is safe because:
- Only local (Unix socket) connections use trust auth
- The container is isolated
- Production deployments should use stronger authentication

### 2. Disable SSL (Cloud Environment Only)

The cloud PostgreSQL setup may have SSL certificate permission issues. To disable SSL:

```bash
# Edit postgresql.conf
sudo sed -i 's/^ssl = on/ssl = off/' /etc/postgresql/16/main/postgresql.conf

# Restart PostgreSQL
pg_ctlcluster 16 main restart
```

**Note:** This is only needed in the containerized cloud environment. Local installations can keep SSL enabled.

### 3. Install Dependencies

```bash
# Backend (Python/Poetry)
cd backend
poetry install

# Frontend (Node/pnpm)
cd ../frontend
pnpm install

# Build prism packages
pnpm run build:packages
```

### 4. Install Playwright

```bash
cd e2e
npm install
npx playwright install chromium
```

## Running the Application

### Start All Servers

```bash
./scripts/start-all.sh
```

This starts:
- Backend on http://localhost:8000
- Frontend on http://localhost:3000

### Run E2E Tests

```bash
./scripts/run-e2e.sh
```

### View Logs

```bash
./scripts/logs.sh
```

### Stop All Servers

```bash
./scripts/stop-all.sh
```

## Known Issues

### E2E Tests - Chromium Crashes (Cloud Environment)

**Status:** Known issue under investigation

**Symptoms:**
- E2E tests fail with "Page crashed" errors in headless Chromium
- Error: `page.goto: Page crashed`
- Tests that create multiple browser contexts fail

**Root Cause:**
Chromium headless mode in containerized environments can crash due to:
- Resource limitations (CPU, memory)
- Missing system libraries or configurations
- GPU/rendering incompatibilities
- Process isolation issues

**Attempted Fixes:**
- ✅ Added Chromium launch args for cloud environments
- ✅ Disabled GPU acceleration
- ✅ Installed system dependencies with `playwright install-deps`
- ❌ Still experiencing crashes

**Current Configuration:** (`e2e/playwright.config.ts`)

```typescript
launchOptions: {
  args: [
    '--disable-gpu',
    '--disable-dev-shm-usage',
    '--disable-setuid-sandbox',
    '--no-sandbox',
    '--disable-accelerated-2d-canvas',
    '--disable-software-rasterizer',
  ],
}
```

**Potential Solutions:**

1. **Use Firefox instead of Chromium:**
   ```bash
   npx playwright install firefox
   # Update playwright.config.ts to use firefox project
   ```

2. **Run tests on local machine:**
   - Clone the repository locally
   - Run `./scripts/setup.sh`
   - Run `./scripts/run-e2e.sh`
   - E2E tests work reliably on local machines

3. **Increase resource limits:**
   - May require container configuration changes
   - Not always possible in managed cloud environments

4. **Use full Chromium instead of headless_shell:**
   ```typescript
   use: {
     channel: 'chrome',
     headless: true,
   }
   ```

5. **Reduce concurrent browser contexts:**
   - Modify tests to use fewer simultaneous browser instances
   - Run tests in isolation

**Workaround:**
The application itself works correctly (verified with manual testing via curl and browser console logs). The issue is specific to automated E2E testing in this environment.

## Environment Differences

### Cloud vs Local

| Feature | Cloud Environment | Local Environment |
|---------|------------------|-------------------|
| PostgreSQL | Pre-installed (v16) | Needs installation |
| PostgreSQL Auth | Trust (configured) | Peer/password |
| PostgreSQL SSL | Disabled | Enabled |
| Chromium E2E | ⚠️ Crashes | ✅ Works |
| Node/pnpm | Pre-installed | Needs installation |
| Poetry | Pre-installed | Needs installation |

### Recommendations

- **Development:** Use cloud environment for quick testing and iteration
- **E2E Testing:** Run on local machine for reliable test execution
- **CI/CD:** May need Firefox or different Chromium configuration

## Files Modified for Cloud Support

### PostgreSQL Configuration
- `/etc/postgresql/16/main/postgresql.conf` - SSL disabled
- `/etc/postgresql/16/main/pg_hba.conf` - Trust authentication

### Test Configuration
- `e2e/playwright.config.ts` - Added cloud-friendly Chromium launch args

### Automation
- `.claude/hooks/startSession.sh` - Automatic environment setup

## Troubleshooting

### PostgreSQL Connection Issues

```bash
# Check if PostgreSQL is running
pg_ctlcluster 16 main status

# View PostgreSQL logs
tail -f /var/log/postgresql/postgresql-16-main.log

# Test connection
psql -U postgres -d prism_chat -c "SELECT 1;"
```

### Dependency Installation Issues

```bash
# Python dependencies
cd backend
poetry install --no-interaction

# Frontend dependencies
cd frontend
pnpm install --no-frozen-lockfile

# E2E dependencies
cd e2e
npm install
```

### Port Conflicts

```bash
# Check what's using port 8000
lsof -i:8000

# Check what's using port 3000
lsof -i:3000

# Kill processes if needed
./scripts/stop-all.sh
```

## Support

For issues specific to the cloud environment:
1. Check this document for known issues
2. Review `/home/user/prism/CURRENT_STATE.md` for project status
3. Check logs with `./scripts/logs.sh`
4. Report issues to the project repository

---

**Last Updated:** 2025-11-17
**Environment:** Claude Code on the Web / Ubuntu 24.04 (Noble)
**PostgreSQL Version:** 16
**Node Version:** Latest LTS
**Python Version:** 3.12+
