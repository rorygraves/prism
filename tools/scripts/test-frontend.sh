#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🧪 Running Frontend Unit Tests"

cd "$FRONTEND_DIR"

log_info "Running Vitest..."
echo ""

# Check if test files exist
TEST_COUNT=$(find packages chat-demo -name "*.test.ts" -o -name "*.spec.ts" 2>/dev/null | wc -l | tr -d ' ')

if [ "$TEST_COUNT" -eq 0 ]; then
    log_warn "No test files found"
    log_info "Frontend unit tests not yet implemented"
    log_info "Test infrastructure is ready (Vitest configured)"
    echo ""
    log_info "To add tests, create files with .test.ts or .spec.ts extension"
    print_separator
    exit 0
fi

# Run tests
if pnpm test run; then
    echo ""
    log_success "All frontend tests passed! ✅"
    print_separator
    exit 0
else
    echo ""
    log_error "Frontend tests failed ❌"
    print_separator
    exit 1
fi
