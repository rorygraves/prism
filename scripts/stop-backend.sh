#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🛑 Stopping Backend Server"

# Try to stop by PID file
if kill_by_pid_file "$BACKEND_PID_FILE" "Backend"; then
    print_separator
    exit 0
fi

# Try to find by port
if port_in_use "$BACKEND_PORT"; then
    PID=$(get_pid_by_port "$BACKEND_PORT")
    log_info "Found process on port $BACKEND_PORT (PID $PID)"
    log_info "Stopping..."
    kill "$PID" 2>/dev/null || true

    # Wait for process to stop
    sleep 2

    if port_in_use "$BACKEND_PORT"; then
        log_warn "Process didn't stop gracefully, forcing..."
        kill -9 "$PID" 2>/dev/null || true
    fi

    log_success "Backend stopped"
else
    log_info "Backend is not running"
fi

print_separator
