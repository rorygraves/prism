#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🗄️  Creating Database"

# Check if PostgreSQL is running
if ! psql -U postgres -c '\l' >/dev/null 2>&1; then
    log_error "PostgreSQL is not running or not accessible"
    log_info "macOS: brew services start postgresql@14"
    log_info "Linux: sudo systemctl start postgresql"
    exit 1
fi

# Check if database already exists
if database_exists; then
    log_warn "Database '$DB_NAME' already exists"
    log_info "Use ./scripts/db-drop.sh to remove it first"
    log_info "Or use ./scripts/db-reset.sh to drop and recreate"
    exit 0
fi

# Create database
log_info "Creating database '$DB_NAME'..."
if psql -U postgres -c "CREATE DATABASE $DB_NAME;" 2>/dev/null; then
    log_success "Database '$DB_NAME' created successfully"
else
    log_error "Failed to create database"
    log_info "You may need to run: sudo -u postgres psql -c \"CREATE DATABASE $DB_NAME;\""
    exit 1
fi

print_separator
