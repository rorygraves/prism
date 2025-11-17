#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "⚡ Starting Frontend Server"

# Check if already running
if [ -f "$FRONTEND_PID_FILE" ]; then
    PID=$(cat "$FRONTEND_PID_FILE")
    if is_running "$PID"; then
        log_warn "Frontend is already running (PID $PID)"
        log_info "Use ./scripts/stop-frontend.sh to stop it first"
        exit 1
    else
        log_warn "Removing stale PID file"
        rm -f "$FRONTEND_PID_FILE"
    fi
fi

# Check if port is in use
if port_in_use "$FRONTEND_PORT"; then
    PID=$(get_pid_by_port "$FRONTEND_PORT")
    log_error "Port $FRONTEND_PORT is already in use by PID $PID"
    log_info "Stop the process first: kill $PID"
    exit 1
fi

# Check if backend is running (warn if not)
if ! port_in_use "$BACKEND_PORT"; then
    log_warn "Backend is not running on port $BACKEND_PORT"
    log_info "Start backend first: ./scripts/start-backend.sh"
    log_info "Or use: ./scripts/start-all.sh"
fi

# Check if packages are built
log_info "Checking built packages..."
if [ ! -d "$FRONTEND_DIR/packages/prism-client/dist" ] || [ ! -d "$FRONTEND_DIR/packages/prism-vue/dist" ]; then
    log_warn "Packages not built, building now..."
    cd "$FRONTEND_DIR/packages/prism-client"
    pnpm run build
    cd "$FRONTEND_DIR/packages/prism-vue"
    pnpm run build
    log_success "Packages built"
fi

# Create log file
LOG_FILE=$(create_log_file "frontend")
log_info "Logging to: $LOG_FILE"

# Start frontend server
log_info "Starting Vite dev server on port $FRONTEND_PORT..."

cd "$FRONTEND_DIR/chat-demo"

# Start server in background, redirecting output to log file
pnpm run dev > "$LOG_FILE" 2>&1 &

PID=$!
echo $PID > "$FRONTEND_PID_FILE"

log_success "Frontend started with PID $PID"

# Wait a moment for startup
sleep "$SERVER_STARTUP_WAIT"

# Show initial log output
log_info "Initial log output:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
tail -n 15 "$LOG_FILE" || true
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Health check
if wait_for_http "$FRONTEND_URL" 15; then
    log_success "Frontend is healthy and ready!"
    echo ""
    log_info "Frontend URL: $FRONTEND_URL"
    log_info "Open in browser: open $FRONTEND_URL"
    echo ""
    log_info "View logs: ./scripts/logs.sh --frontend"
    log_info "Stop server: ./scripts/stop-frontend.sh"
else
    log_error "Frontend health check failed"
    log_info "Check logs: tail -f $LOG_FILE"
    exit 1
fi

print_separator
