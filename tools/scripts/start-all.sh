#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🚀 Starting All Servers"

# Start backend
log_info "Starting backend server..."
"$SCRIPT_DIR/start-backend.sh"
echo ""

# Start frontend
log_info "Starting frontend server..."
"$SCRIPT_DIR/start-frontend.sh"
echo ""

print_separator
log_success "All servers started successfully! 🎉"
print_separator
echo ""
log_info "Access the application:"
echo "  Frontend: $FRONTEND_URL"
echo "  Backend:  $BACKEND_URL"
echo "  Health:   $BACKEND_HEALTH_URL"
echo ""
log_info "Useful commands:"
echo "  View logs:    ./scripts/logs.sh"
echo "  Check status: ./scripts/status.sh"
echo "  Stop servers: ./scripts/stop-all.sh"
echo "  Run tests:    ./scripts/test-all.sh"
echo ""
log_info "Monitor logs with:"
echo "  tail -f logs/backend-latest.log logs/frontend-latest.log"
echo ""
print_separator
