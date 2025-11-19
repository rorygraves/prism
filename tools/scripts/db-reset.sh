#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🔄 Resetting Database"

log_warn "This will drop and recreate the database '$DB_NAME'"
log_warn "All data will be lost!"
echo ""

# Drop database (with confirmation)
"$SCRIPT_DIR/db-drop.sh"
echo ""

# Create database
"$SCRIPT_DIR/db-create.sh"

log_success "Database reset complete"
log_info "You can now start the backend: ./scripts/start-backend.sh"

print_separator
