#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🛑 Stopping All Servers"

# Stop frontend
log_info "Stopping frontend..."
"$SCRIPT_DIR/stop-frontend.sh"
echo ""

# Stop backend
log_info "Stopping backend..."
"$SCRIPT_DIR/stop-backend.sh"
echo ""

print_separator
log_success "All servers stopped"
print_separator
