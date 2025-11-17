#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🐍 Starting Backend Server"

# Check if already running
if [ -f "$BACKEND_PID_FILE" ]; then
    PID=$(cat "$BACKEND_PID_FILE")
    if is_running "$PID"; then
        log_warn "Backend is already running (PID $PID)"
        log_info "Use ./scripts/stop-backend.sh to stop it first"
        exit 1
    else
        log_warn "Removing stale PID file"
        rm -f "$BACKEND_PID_FILE"
    fi
fi

# Check if port is in use
if port_in_use "$BACKEND_PORT"; then
    PID=$(get_pid_by_port "$BACKEND_PORT")
    log_error "Port $BACKEND_PORT is already in use by PID $PID"
    log_info "Stop the process first: kill $PID"
    exit 1
fi

# Check PostgreSQL
log_info "Checking PostgreSQL..."
if ! database_exists; then
    log_error "Database '$DB_NAME' does not exist"
    log_info "Run: ./scripts/db-create.sh"
    exit 1
fi
log_success "Database is ready"

# Create log file
LOG_FILE=$(create_log_file "backend")
log_info "Logging to: $LOG_FILE"

# Start backend server
log_info "Starting uvicorn server on port $BACKEND_PORT..."

cd "$BACKEND_DIR"

# Start server in background, redirecting output to log file
poetry run uvicorn chat_demo.main:app \
    --host 0.0.0.0 \
    --port "$BACKEND_PORT" \
    --log-level info \
    > "$LOG_FILE" 2>&1 &

PID=$!
echo $PID > "$BACKEND_PID_FILE"

log_success "Backend started with PID $PID"

# Wait a moment for startup
sleep "$SERVER_STARTUP_WAIT"

# Show initial log output
log_info "Initial log output:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
tail -n 10 "$LOG_FILE" || true
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Health check
if wait_for_http "$BACKEND_HEALTH_URL" 10; then
    log_success "Backend is healthy and ready!"
    echo ""
    log_info "Backend URL: $BACKEND_URL"
    log_info "Health check: $BACKEND_HEALTH_URL"
    log_info "WebSocket: ws://localhost:$BACKEND_PORT/ws"
    echo ""
    log_info "View logs: ./scripts/logs.sh --backend"
    log_info "Stop server: ./scripts/stop-backend.sh"
else
    log_error "Backend health check failed"
    log_info "Check logs: tail -f $LOG_FILE"
    exit 1
fi

print_separator
