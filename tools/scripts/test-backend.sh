#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🧪 Running Backend Unit Tests"

cd "$BACKEND_DIR"

log_info "Running pytest with coverage..."
echo ""

# Run tests
if poetry run pytest -v --cov=prism --cov=chat_demo --cov-report=term-missing --cov-report=html; then
    echo ""
    log_success "All backend tests passed! ✅"
    echo ""
    log_info "Coverage report generated at: $BACKEND_DIR/htmlcov/index.html"
    print_separator
    exit 0
else
    echo ""
    log_error "Backend tests failed ❌"
    print_separator
    exit 1
fi
