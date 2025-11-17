#!/usr/bin/env bash

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "📊 Server Status"

# Check backend
echo ""
log_info "Backend Server (port $BACKEND_PORT):"

if [ -f "$BACKEND_PID_FILE" ]; then
    PID=$(cat "$BACKEND_PID_FILE")
    if is_running "$PID"; then
        log_running "Running (PID $PID)"

        # Get uptime
        if command_exists ps; then
            START_TIME=$(ps -p $PID -o lstart= 2>/dev/null || echo "unknown")
            echo "  Started: $START_TIME"
        fi

        # Show last 3 log lines
        if [ -f "$BACKEND_LOG" ]; then
            echo "  Last 3 log lines:"
            tail -n 3 "$BACKEND_LOG" | sed 's/^/    /'
        fi
    else
        log_stopped "Not running (stale PID file)"
        rm -f "$BACKEND_PID_FILE"
    fi
elif port_in_use "$BACKEND_PORT"; then
    PID=$(get_pid_by_port "$BACKEND_PORT")
    log_running "Running (PID $PID, no PID file)"
else
    log_stopped "Not running"
fi

# Check frontend
echo ""
log_info "Frontend Server (port $FRONTEND_PORT):"

if [ -f "$FRONTEND_PID_FILE" ]; then
    PID=$(cat "$FRONTEND_PID_FILE")
    if is_running "$PID"; then
        log_running "Running (PID $PID)"

        # Get uptime
        if command_exists ps; then
            START_TIME=$(ps -p $PID -o lstart= 2>/dev/null || echo "unknown")
            echo "  Started: $START_TIME"
        fi

        # Show last 3 log lines
        if [ -f "$FRONTEND_LOG" ]; then
            echo "  Last 3 log lines:"
            tail -n 3 "$FRONTEND_LOG" | sed 's/^/    /'
        fi
    else
        log_stopped "Not running (stale PID file)"
        rm -f "$FRONTEND_PID_FILE"
    fi
elif port_in_use "$FRONTEND_PORT"; then
    PID=$(get_pid_by_port "$FRONTEND_PORT")
    log_running "Running (PID $PID, no PID file)"
else
    log_stopped "Not running"
fi

# Check database
echo ""
log_info "Database:"

if database_exists; then
    log_success "Database '$DB_NAME' exists"

    # Get database size
    DB_SIZE=$(psql -U postgres -d "$DB_NAME" -t -c "SELECT pg_size_pretty(pg_database_size('$DB_NAME'));" 2>/dev/null | xargs || echo "unknown")
    echo "  Size: $DB_SIZE"
else
    log_error "Database '$DB_NAME' does not exist"
    echo "  Create with: ./scripts/db-create.sh"
fi

echo ""
print_separator
