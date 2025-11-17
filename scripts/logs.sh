#!/usr/bin/env bash

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

# Parse arguments
SHOW_BACKEND=false
SHOW_FRONTEND=false
FILTER=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --backend|-b)
            SHOW_BACKEND=true
            shift
            ;;
        --frontend|-f)
            SHOW_FRONTEND=true
            shift
            ;;
        --both)
            SHOW_BACKEND=true
            SHOW_FRONTEND=true
            shift
            ;;
        --filter)
            FILTER="$2"
            shift 2
            ;;
        *)
            log_error "Unknown option: $1"
            echo "Usage: $0 [--backend|-b] [--frontend|-f] [--both] [--filter PATTERN]"
            exit 1
            ;;
    esac
done

# Default to showing both if neither specified
if [ "$SHOW_BACKEND" = false ] && [ "$SHOW_FRONTEND" = false ]; then
    SHOW_BACKEND=true
    SHOW_FRONTEND=true
fi

# Build file list
LOG_FILES=()

if [ "$SHOW_BACKEND" = true ]; then
    if [ -f "$BACKEND_LOG" ]; then
        LOG_FILES+=("$BACKEND_LOG")
    else
        log_warn "Backend log not found at $BACKEND_LOG"
    fi
fi

if [ "$SHOW_FRONTEND" = true ]; then
    if [ -f "$FRONTEND_LOG" ]; then
        LOG_FILES+=("$FRONTEND_LOG")
    else
        log_warn "Frontend log not found at $FRONTEND_LOG"
    fi
fi

if [ ${#LOG_FILES[@]} -eq 0 ]; then
    log_error "No log files found"
    log_info "Start servers first: ./scripts/start-all.sh"
    exit 1
fi

# Display header
print_header "📜 Viewing Logs"

for log in "${LOG_FILES[@]}"; do
    log_info "Monitoring: $log"
done

if [ -n "$FILTER" ]; then
    log_info "Filter: $FILTER"
fi

print_separator
echo ""

# Tail logs
if [ -n "$FILTER" ]; then
    tail -f "${LOG_FILES[@]}" | grep --color=auto "$FILTER"
else
    tail -f "${LOG_FILES[@]}"
fi
