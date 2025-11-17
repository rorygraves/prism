#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🧹 Cleaning Build Artifacts"

# Stop servers first
if port_in_use "$BACKEND_PORT" || port_in_use "$FRONTEND_PORT"; then
    log_info "Stopping servers..."
    "$SCRIPT_DIR/stop-all.sh" >/dev/null 2>&1 || true
    echo ""
fi

# Clean frontend
log_info "Cleaning frontend..."
cd "$FRONTEND_DIR"

# Remove node_modules
if [ -d "node_modules" ]; then
    log_info "Removing frontend/node_modules..."
    rm -rf node_modules
fi

# Remove package node_modules and dist
for pkg in packages/*/; do
    if [ -d "${pkg}node_modules" ]; then
        log_info "Removing ${pkg}node_modules..."
        rm -rf "${pkg}node_modules"
    fi
    if [ -d "${pkg}dist" ]; then
        log_info "Removing ${pkg}dist..."
        rm -rf "${pkg}dist"
    fi
done

# Remove chat-demo build artifacts
if [ -d "chat-demo/node_modules" ]; then
    log_info "Removing chat-demo/node_modules..."
    rm -rf chat-demo/node_modules
fi

if [ -d "chat-demo/dist" ]; then
    log_info "Removing chat-demo/dist..."
    rm -rf chat-demo/dist
fi

log_success "Frontend cleaned"

# Clean backend
echo ""
log_info "Cleaning backend..."
cd "$BACKEND_DIR"

# Remove Python cache
find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true
find . -type d -name ".pytest_cache" -exec rm -rf {} + 2>/dev/null || true
find . -type f -name "*.pyc" -delete 2>/dev/null || true
find . -type d -name "*.egg-info" -exec rm -rf {} + 2>/dev/null || true

# Remove coverage files
rm -rf .coverage htmlcov .mypy_cache 2>/dev/null || true

log_success "Backend cleaned"

# Clean logs
echo ""
log_info "Cleaning logs..."
cd "$PROJECT_ROOT"

if [ -d "logs" ]; then
    rm -f logs/*.log logs/*.pid 2>/dev/null || true
    log_success "Logs cleaned"
fi

# Clean E2E artifacts
echo ""
log_info "Cleaning E2E test artifacts..."
cd "$E2E_DIR"

if [ -d "node_modules" ]; then
    rm -rf node_modules
fi

rm -rf playwright-report test-results .playwright 2>/dev/null || true

log_success "E2E artifacts cleaned"

echo ""
print_separator
log_success "All build artifacts cleaned! ✨"
echo ""
log_info "To reinstall dependencies, run: ./scripts/setup.sh"
print_separator
