#!/usr/bin/env bash
#
# Claude Code on the Web - Start Session Hook
# This script runs automatically when a new Claude Code session starts.
# It sets up the environment for testing and development.
#

set -e

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🚀 Prism Project - Session Startup"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Get project root (parent of .claude directory)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

# PostgreSQL Configuration for Cloud Environment
# ================================================
echo "📦 Configuring PostgreSQL..."

# Check if PostgreSQL is running
if ! pg_ctlcluster 16 main status >/dev/null 2>&1; then
    echo "  ├─ Starting PostgreSQL 16..."
    pg_ctlcluster 16 main start || {
        echo "  └─ ⚠️  Failed to start PostgreSQL"
        exit 1
    }
else
    echo "  ├─ PostgreSQL 16 already running"
fi

# Ensure trust authentication is configured
if ! grep -q "^local.*all.*all.*trust" /etc/postgresql/16/main/pg_hba.conf 2>/dev/null; then
    echo "  ├─ Configuring trust authentication..."
    sudo sed -i 's/^local.*all.*all.*/local   all             all                                     trust/' /etc/postgresql/16/main/pg_hba.conf
    pg_ctlcluster 16 main reload
fi

# Create database if it doesn't exist
if ! psql -U postgres -lqt | cut -d \| -f 1 | grep -qw prism_chat 2>/dev/null; then
    echo "  ├─ Creating prism_chat database..."
    psql -U postgres -c "CREATE DATABASE prism_chat;" || {
        echo "  └─ ⚠️  Failed to create database"
        exit 1
    }
else
    echo "  ├─ Database prism_chat already exists"
fi

echo "  └─ ✅ PostgreSQL ready"

# Python/Poetry Dependencies
# ===========================
echo "📦 Checking Python dependencies..."

if command -v poetry >/dev/null 2>&1; then
    echo "  ├─ Poetry found: $(poetry --version)"
    if [ ! -d "$PROJECT_ROOT/backend/.venv" ]; then
        echo "  ├─ Installing Python dependencies..."
        cd "$PROJECT_ROOT/backend"
        poetry install --no-interaction || {
            echo "  └─ ⚠️  Failed to install Python dependencies"
            exit 1
        }
        cd "$PROJECT_ROOT"
    else
        echo "  ├─ Python virtual environment already exists"
    fi
    echo "  └─ ✅ Python dependencies ready"
else
    echo "  └─ ⚠️  Poetry not found (skipping Python setup)"
fi

# Node/pnpm Dependencies
# =======================
echo "📦 Checking Node dependencies..."

if command -v pnpm >/dev/null 2>&1; then
    echo "  ├─ pnpm found: $(pnpm --version)"
    if [ ! -d "$PROJECT_ROOT/frontend/node_modules" ]; then
        echo "  ├─ Installing frontend dependencies..."
        cd "$PROJECT_ROOT/frontend"
        pnpm install --frozen-lockfile || pnpm install || {
            echo "  └─ ⚠️  Failed to install frontend dependencies"
            exit 1
        }
        cd "$PROJECT_ROOT"
    else
        echo "  ├─ Frontend node_modules already exists"
    fi

    # Build prism packages
    echo "  ├─ Building prism packages..."
    cd "$PROJECT_ROOT/frontend"
    pnpm run build:packages 2>/dev/null || {
        # Fallback: build packages individually
        cd "$PROJECT_ROOT/frontend/packages/prism-client"
        pnpm run build
        cd "$PROJECT_ROOT/frontend/packages/prism-vue"
        pnpm run build
    }
    cd "$PROJECT_ROOT"
    echo "  └─ ✅ Frontend dependencies ready"
else
    echo "  └─ ⚠️  pnpm not found (skipping frontend setup)"
fi

# Playwright Browsers
# ====================
echo "📦 Checking Playwright browsers..."

if [ -d "$PROJECT_ROOT/e2e" ]; then
    cd "$PROJECT_ROOT/e2e"
    if ! npx playwright --version >/dev/null 2>&1; then
        echo "  ├─ Installing e2e dependencies..."
        npm install || {
            echo "  └─ ⚠️  Failed to install e2e dependencies"
            exit 1
        }
    fi

    # Check if Chromium is installed
    if [ ! -d "$HOME/.cache/ms-playwright/chromium-"* ]; then
        echo "  ├─ Installing Playwright Chromium..."
        npx playwright install chromium --with-deps || {
            echo "  └─ ⚠️  Failed to install Chromium"
            exit 1
        }
    else
        echo "  ├─ Playwright Chromium already installed"
    fi
    cd "$PROJECT_ROOT"
    echo "  └─ ✅ Playwright ready"
else
    echo "  └─ ⚠️  e2e directory not found (skipping Playwright setup)"
fi

# Summary
# ========
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Session Setup Complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "📖 Quick Start:"
echo "  • Start all servers:  ./scripts/start-all.sh"
echo "  • Run e2e tests:      ./scripts/run-e2e.sh"
echo "  • View logs:          ./scripts/logs.sh"
echo "  • Stop all servers:   ./scripts/stop-all.sh"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
