#!/usr/bin/env bash

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🧪 Running All Tests"

# Track results
BACKEND_RESULT=0
FRONTEND_RESULT=0
E2E_RESULT=0

# Run backend tests
log_info "1/3: Backend Unit Tests"
print_separator
if "$SCRIPT_DIR/test-backend.sh"; then
    BACKEND_RESULT=0
else
    BACKEND_RESULT=1
fi
echo ""

# Run frontend tests
log_info "2/3: Frontend Unit Tests"
print_separator
if "$SCRIPT_DIR/test-frontend.sh"; then
    FRONTEND_RESULT=0
else
    FRONTEND_RESULT=1
fi
echo ""

# Run E2E tests
log_info "3/3: End-to-End Tests"
print_separator
if "$SCRIPT_DIR/run-e2e.sh"; then
    E2E_RESULT=0
else
    E2E_RESULT=1
fi
echo ""

# Summary
print_header "📊 Test Results Summary"

echo ""
if [ $BACKEND_RESULT -eq 0 ]; then
    log_success "Backend Unit Tests: PASSED"
else
    log_error "Backend Unit Tests: FAILED"
fi

if [ $FRONTEND_RESULT -eq 0 ]; then
    log_success "Frontend Unit Tests: PASSED"
else
    log_error "Frontend Unit Tests: FAILED"
fi

if [ $E2E_RESULT -eq 0 ]; then
    log_success "E2E Tests: PASSED"
else
    log_error "E2E Tests: FAILED"
fi

echo ""
print_separator

# Exit with failure if any tests failed
if [ $BACKEND_RESULT -ne 0 ] || [ $FRONTEND_RESULT -ne 0 ] || [ $E2E_RESULT -ne 0 ]; then
    log_error "Some tests failed"
    exit 1
else
    log_success "All tests passed! 🎉"
    exit 0
fi
