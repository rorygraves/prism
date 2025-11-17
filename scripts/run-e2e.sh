#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

# Parse arguments
STOP_AFTER=true
UI_MODE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --keep-alive)
            STOP_AFTER=false
            shift
            ;;
        --ui)
            UI_MODE=true
            STOP_AFTER=false
            shift
            ;;
        *)
            log_error "Unknown option: $1"
            echo "Usage: $0 [--keep-alive] [--ui]"
            exit 1
            ;;
    esac
done

print_header "🎭 Running End-to-End Tests"

# Track if we started servers (so we know whether to stop them)
STARTED_BACKEND=false
STARTED_FRONTEND=false

# Cleanup function
cleanup() {
    if [ "$STOP_AFTER" = true ]; then
        echo ""
        log_info "Cleaning up..."

        if [ "$STARTED_FRONTEND" = true ]; then
            log_info "Stopping frontend..."
            "$SCRIPT_DIR/stop-frontend.sh" >/dev/null 2>&1 || true
        fi

        if [ "$STARTED_BACKEND" = true ]; then
            log_info "Stopping backend..."
            "$SCRIPT_DIR/stop-backend.sh" >/dev/null 2>&1 || true
        fi
    fi
}

# Set trap for cleanup
trap cleanup EXIT

# Check if backend is running
if ! port_in_use "$BACKEND_PORT"; then
    log_info "Backend not running, starting it..."
    "$SCRIPT_DIR/start-backend.sh"
    STARTED_BACKEND=true
    echo ""
else
    log_info "Backend already running on port $BACKEND_PORT"
fi

# Check if frontend is running
if ! port_in_use "$FRONTEND_PORT"; then
    log_info "Frontend not running, starting it..."
    "$SCRIPT_DIR/start-frontend.sh"
    STARTED_FRONTEND=true
    echo ""
else
    log_info "Frontend already running on port $FRONTEND_PORT"
fi

print_separator

# Run E2E tests
log_info "Running Playwright tests..."
echo ""

cd "$E2E_DIR"

if [ "$UI_MODE" = true ]; then
    log_info "Starting Playwright UI mode..."
    npm run test:ui
else
    if npm test; then
        echo ""
        log_success "All E2E tests passed! ✅"
        echo ""
        log_info "Test report: $E2E_DIR/playwright-report/index.html"
        print_separator
        exit 0
    else
        echo ""
        log_error "E2E tests failed ❌"
        echo ""
        log_info "Test report: $E2E_DIR/playwright-report/index.html"
        log_info "Debug with: ./scripts/run-e2e.sh --ui"
        print_separator
        exit 1
    fi
fi
