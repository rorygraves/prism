#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🛑 Stopping Frontend Server"

# Try to stop by PID file
if kill_by_pid_file "$FRONTEND_PID_FILE" "Frontend"; then
    print_separator
    exit 0
fi

# Try to find by port
if port_in_use "$FRONTEND_PORT"; then
    PID=$(get_pid_by_port "$FRONTEND_PORT")
    log_info "Found process on port $FRONTEND_PORT (PID $PID)"
    log_info "Stopping..."
    kill "$PID" 2>/dev/null || true

    # Wait for process to stop
    sleep 2

    if port_in_use "$FRONTEND_PORT"; then
        log_warn "Process didn't stop gracefully, forcing..."
        kill -9 "$PID" 2>/dev/null || true
    fi

    log_success "Frontend stopped"
else
    log_info "Frontend is not running"
fi

print_separator
