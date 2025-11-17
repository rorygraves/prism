#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🔄 Restarting All Servers"

# Stop all servers
"$SCRIPT_DIR/stop-all.sh"

# Wait a moment
log_info "Waiting 2 seconds before restart..."
sleep 2
echo ""

# Start all servers
"$SCRIPT_DIR/start-all.sh"
