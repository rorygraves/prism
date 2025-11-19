#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🗑️  Dropping Database"

# Check if database exists
if ! database_exists; then
    log_warn "Database '$DB_NAME' does not exist"
    exit 0
fi

# Confirm deletion
log_warn "This will permanently delete the database '$DB_NAME' and all its data!"
read -p "Are you sure? (yes/no): " -r
echo

if [[ ! $REPLY =~ ^[Yy][Ee][Ss]$ ]]; then
    log_info "Cancelled"
    exit 0
fi

# Stop backend if running (can't drop database while connected)
if port_in_use "$BACKEND_PORT"; then
    log_info "Stopping backend server..."
    "$SCRIPT_DIR/stop-backend.sh" >/dev/null 2>&1 || true
    sleep 2
fi

# Drop database
log_info "Dropping database '$DB_NAME'..."

# Terminate existing connections first
psql -U postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME';" >/dev/null 2>&1 || true

if psql -U postgres -c "DROP DATABASE $DB_NAME;" 2>/dev/null; then
    log_success "Database '$DB_NAME' dropped successfully"
else
    log_error "Failed to drop database"
    exit 1
fi

print_separator
