#!/usr/bin/env bash
# Shared configuration for all scripts

# Project directories
export PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export BACKEND_DIR="$PROJECT_ROOT/backend"
export FRONTEND_DIR="$PROJECT_ROOT/frontend"
export E2E_DIR="$PROJECT_ROOT/e2e"
export SCRIPTS_DIR="$PROJECT_ROOT/scripts"
export LOGS_DIR="$PROJECT_ROOT/logs"

# Server ports
export BACKEND_PORT=8000
export FRONTEND_PORT=3000

# Server URLs
export BACKEND_URL="http://localhost:$BACKEND_PORT"
export FRONTEND_URL="http://localhost:$FRONTEND_PORT"
export BACKEND_HEALTH_URL="$BACKEND_URL/health"

# Database configuration
export DB_NAME="prism_chat"
export DB_USER="postgres"
export DB_PASSWORD="postgres"
export DB_HOST="localhost"
export DB_PORT="5432"
export DATABASE_URL="postgresql+asyncpg://$DB_USER:$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_NAME"

# PID files
export BACKEND_PID_FILE="$LOGS_DIR/backend.pid"
export FRONTEND_PID_FILE="$LOGS_DIR/frontend.pid"

# Log files (will use timestamped versions, these are symlinks)
export BACKEND_LOG="$LOGS_DIR/backend-latest.log"
export FRONTEND_LOG="$LOGS_DIR/frontend-latest.log"

# Timeouts (in seconds)
export HEALTH_CHECK_TIMEOUT=30
export HEALTH_CHECK_INTERVAL=1
export SERVER_STARTUP_WAIT=3

# Colors for output
export COLOR_RED='\033[0;31m'
export COLOR_GREEN='\033[0;32m'
export COLOR_YELLOW='\033[1;33m'
export COLOR_BLUE='\033[0;34m'
export COLOR_MAGENTA='\033[0;35m'
export COLOR_CYAN='\033[0;36m'
export COLOR_RESET='\033[0m'

# Emoji for status
export STATUS_OK="✅"
export STATUS_WARN="⚠️"
export STATUS_ERROR="❌"
export STATUS_INFO="ℹ️"
export STATUS_RUNNING="🟢"
export STATUS_STOPPED="🔴"
