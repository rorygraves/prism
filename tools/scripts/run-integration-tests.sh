#!/bin/bash
#
# Run integration tests for Prism
#
# This script ensures the backend is running and then runs integration tests.
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Prism Integration Tests ===${NC}"
echo

# Check if backend is running
echo "Checking if backend is running..."
if curl -s "http://localhost:8000/health" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Backend is running${NC}"
    BACKEND_WAS_RUNNING=true
else
    echo -e "${YELLOW}⚠ Backend not running. Starting...${NC}"
    BACKEND_WAS_RUNNING=false

    # Start backend in background
    cd "$PROJECT_ROOT/backend"
    poetry run python -m chat_demo.main > /tmp/prism-backend-integration.log 2>&1 &
    BACKEND_PID=$!

    # Wait for backend to be ready
    echo "Waiting for backend to start..."
    for i in {1..30}; do
        if curl -s "http://localhost:8000/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Backend started (PID: $BACKEND_PID)${NC}"
            break
        fi
        sleep 1
        if [ $i -eq 30 ]; then
            echo -e "${RED}✗ Backend failed to start${NC}"
            echo "Check logs at: /tmp/prism-backend-integration.log"
            exit 1
        fi
    done
fi

echo

# Run integration tests
echo -e "${GREEN}Running integration tests...${NC}"
cd "$PROJECT_ROOT/frontend/integration-tests"
pnpm test "$@"
TEST_EXIT_CODE=$?

echo

# Stop backend if we started it
if [ "$BACKEND_WAS_RUNNING" = false ]; then
    echo "Stopping backend (PID: $BACKEND_PID)..."
    kill $BACKEND_PID 2>/dev/null || true
    echo -e "${GREEN}✓ Backend stopped${NC}"
fi

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✓ All integration tests passed!${NC}"
else
    echo -e "${RED}✗ Integration tests failed${NC}"
fi

exit $TEST_EXIT_CODE
