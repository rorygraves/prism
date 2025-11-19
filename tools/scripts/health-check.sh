#!/usr/bin/env bash

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🏥 Health Check"

ALL_HEALTHY=true

# Check backend
echo ""
log_info "Backend Health:"

if curl -s -f "$BACKEND_HEALTH_URL" >/dev/null 2>&1; then
    log_success "🟢 Backend is healthy"
    RESPONSE=$(curl -s "$BACKEND_HEALTH_URL")
    echo "  Response: $RESPONSE"
else
    log_error "🔴 Backend health check failed"
    echo "  URL: $BACKEND_HEALTH_URL"
    ALL_HEALTHY=false
fi

# Check frontend
echo ""
log_info "Frontend Health:"

if curl -s -f "$FRONTEND_URL" >/dev/null 2>&1; then
    log_success "🟢 Frontend is accessible"
    echo "  URL: $FRONTEND_URL"
else
    log_error "🔴 Frontend is not accessible"
    echo "  URL: $FRONTEND_URL"
    ALL_HEALTHY=false
fi

# Check database
echo ""
log_info "Database Connection:"

if psql -U postgres -d "$DB_NAME" -c "SELECT 1;" >/dev/null 2>&1; then
    log_success "🟢 Database is accessible"

    # Count tables
    TABLE_COUNT=$(psql -U postgres -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public';" 2>/dev/null | xargs || echo "0")
    echo "  Tables: $TABLE_COUNT"
else
    log_error "🔴 Database connection failed"
    ALL_HEALTHY=false
fi

# Check WebSocket
echo ""
log_info "WebSocket:"

if port_in_use "$BACKEND_PORT"; then
    log_success "🟢 WebSocket port is listening"
    echo "  URL: ws://localhost:$BACKEND_PORT/ws"
else
    log_error "🔴 WebSocket port is not listening"
    ALL_HEALTHY=false
fi

echo ""
print_separator

if [ "$ALL_HEALTHY" = true ]; then
    log_success "All systems healthy! 🎉"
    exit 0
else
    log_error "Some systems are unhealthy"
    echo ""
    log_info "Troubleshooting:"
    echo "  Check status: ./scripts/status.sh"
    echo "  View logs:    ./scripts/logs.sh"
    echo "  Restart:      ./scripts/restart-all.sh"
    exit 1
fi
