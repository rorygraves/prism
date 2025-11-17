#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --type|-t)
            BACKEND_TYPE="$2"
            shift 2
            ;;
        python|play|http4s)
            BACKEND_TYPE="$1"
            shift
            ;;
        *)
            echo "Usage: $0 [--type|-t TYPE | python|play|http4s]"
            echo "  Backend types: python (default), play, http4s"
            echo "  Or set PRISM_BACKEND environment variable"
            exit 1
            ;;
    esac
done

# Backend-specific startup functions (defined before use)

start_python_backend() {
    log_info "Starting Python backend (uvicorn)..."

    # Check PostgreSQL
    log_info "Checking PostgreSQL..."
    if ! database_exists; then
        log_error "Database '$DB_NAME' does not exist"
        log_info "Run: ./scripts/db-create.sh"
        exit 1
    fi
    log_success "Database is ready"

    cd "$BACKEND_DIR"

    # Start server in background
    poetry run uvicorn chat_demo.main:app \
        --host 0.0.0.0 \
        --port "$BACKEND_PORT" \
        --log-level info \
        > "$LOG_FILE" 2>&1 &

    PID=$!
    echo $PID > "$BACKEND_PID_FILE"

    log_success "Python backend started with PID $PID"

    # Wait for startup
    sleep "$SERVER_STARTUP_WAIT"

    # Show initial log output
    log_info "Initial log output:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    tail -n 10 "$LOG_FILE" || true
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

start_play_backend() {
    log_info "Starting Play Framework backend (sbt)..."

    # Note: Play demo uses in-memory storage, no PostgreSQL needed
    log_info "Using in-memory storage (no database required)"

    cd "$BACKEND_SCALA_DIR"

    # Compile first to check for errors
    log_info "Compiling Play demo..."
    if ! sbt "++ 2.13.12; prismCore/compile; prismPlayDemo/compile" >> "$LOG_FILE" 2>&1; then
        log_error "Compilation failed"
        log_info "Check logs: tail -f $LOG_FILE"
        exit 1
    fi
    log_success "Compilation successful"

    # Start server in background
    # Use -Dhttp.port to override the port and ++ to set Scala version
    sbt -Dhttp.port="$BACKEND_PORT" "++ 2.13.12; prismPlayDemo/run" \
        > "$LOG_FILE" 2>&1 &

    PID=$!
    echo $PID > "$BACKEND_PID_FILE"

    log_success "Play backend started with PID $PID"

    # Wait longer for sbt to start
    log_info "Waiting for Play server to start (this may take 10-20 seconds)..."
    sleep 15

    # Show initial log output
    log_info "Initial log output:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    tail -n 10 "$LOG_FILE" || true
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

start_http4s_backend() {
    log_info "Starting http4s backend (sbt)..."

    # Note: http4s demo uses in-memory storage, no PostgreSQL needed
    log_info "Using in-memory storage (no database required)"

    cd "$BACKEND_SCALA_DIR"

    # Compile first to check for errors
    log_info "Compiling http4s demo..."
    if ! sbt "++ 3.3.1; prismCore/compile; prismHttp4sDemo/compile" >> "$LOG_FILE" 2>&1; then
        log_error "Compilation failed"
        log_info "Check logs: tail -f $LOG_FILE"
        exit 1
    fi
    log_success "Compilation successful"

    # Start server in background
    sbt "prismHttp4sDemo/run" \
        > "$LOG_FILE" 2>&1 &

    PID=$!
    echo $PID > "$BACKEND_PID_FILE"

    log_success "http4s backend started with PID $PID"

    # Wait longer for sbt to start
    log_info "Waiting for http4s server to start (this may take 10-20 seconds)..."
    sleep 15

    # Show initial log output
    log_info "Initial log output:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    tail -n 10 "$LOG_FILE" || true
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

print_header "🚀 Starting Backend Server ($BACKEND_TYPE)"

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

# Create log file
LOG_FILE=$(create_log_file "backend-$BACKEND_TYPE")
log_info "Logging to: $LOG_FILE"

# Start appropriate backend
case "$BACKEND_TYPE" in
    python)
        start_python_backend
        ;;
    play)
        start_play_backend
        ;;
    http4s)
        start_http4s_backend
        ;;
    *)
        log_error "Unknown backend type: $BACKEND_TYPE"
        log_info "Valid types: python, play, http4s"
        exit 1
        ;;
esac

# Health check
if wait_for_http "$BACKEND_HEALTH_URL" 30; then
    log_success "Backend ($BACKEND_TYPE) is healthy and ready!"
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
