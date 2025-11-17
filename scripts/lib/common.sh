#!/usr/bin/env bash
# Common utility functions for all scripts

# Load configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$SCRIPT_DIR/config.sh"

# Logging functions
log_info() {
    echo -e "${COLOR_BLUE}${STATUS_INFO}  $1${COLOR_RESET}"
}

log_success() {
    echo -e "${COLOR_GREEN}${STATUS_OK}  $1${COLOR_RESET}"
}

log_warn() {
    echo -e "${COLOR_YELLOW}${STATUS_WARN}  $1${COLOR_RESET}"
}

log_error() {
    echo -e "${COLOR_RED}${STATUS_ERROR}  $1${COLOR_RESET}" >&2
}

log_running() {
    echo -e "${COLOR_GREEN}${STATUS_RUNNING}  $1${COLOR_RESET}"
}

log_stopped() {
    echo -e "${COLOR_RED}${STATUS_STOPPED}  $1${COLOR_RESET}"
}

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check if port is in use
port_in_use() {
    lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null 2>&1
}

# Get PID using port
get_pid_by_port() {
    lsof -Pi :$1 -sTCP:LISTEN -t 2>/dev/null
}

# Check if process is running
is_running() {
    local pid=$1
    kill -0 "$pid" 2>/dev/null
}

# Wait for HTTP endpoint to be available
wait_for_http() {
    local url=$1
    local timeout=${2:-$HEALTH_CHECK_TIMEOUT}
    local interval=${3:-$HEALTH_CHECK_INTERVAL}
    local elapsed=0

    log_info "Waiting for $url to be ready..."

    while [ $elapsed -lt $timeout ]; do
        if curl -s -f "$url" >/dev/null 2>&1; then
            log_success "$url is ready!"
            return 0
        fi
        sleep $interval
        elapsed=$((elapsed + interval))
    done

    log_error "Timeout waiting for $url after ${timeout}s"
    return 1
}

# Create timestamped log file and symlink
create_log_file() {
    local service=$1
    local timestamp=$(date +%Y%m%d-%H%M%S)
    local log_file="$LOGS_DIR/${service}-${timestamp}.log"
    local latest_link="$LOGS_DIR/${service}-latest.log"

    touch "$log_file"
    ln -sf "$(basename "$log_file")" "$latest_link"

    echo "$log_file"
}

# Kill process by PID file
kill_by_pid_file() {
    local pid_file=$1
    local service=$2

    if [ ! -f "$pid_file" ]; then
        log_warn "No PID file found for $service at $pid_file"
        return 1
    fi

    local pid=$(cat "$pid_file")

    if ! is_running "$pid"; then
        log_warn "$service (PID $pid) is not running"
        rm -f "$pid_file"
        return 1
    fi

    log_info "Stopping $service (PID $pid)..."
    kill "$pid" 2>/dev/null

    # Wait for process to stop (max 10 seconds)
    local count=0
    while is_running "$pid" && [ $count -lt 10 ]; do
        sleep 1
        count=$((count + 1))
    done

    if is_running "$pid"; then
        log_warn "$service didn't stop gracefully, forcing..."
        kill -9 "$pid" 2>/dev/null
    fi

    rm -f "$pid_file"
    log_success "$service stopped"
    return 0
}

# Check if we're in the project root
check_project_root() {
    if [ ! -d "backend" ] || [ ! -d "frontend" ]; then
        log_error "This script must be run from the project root directory"
        log_info "Current directory: $(pwd)"
        log_info "Expected to find 'backend' and 'frontend' directories"
        exit 1
    fi
}

# Check if database exists
database_exists() {
    psql -U "$DB_USER" -lqt 2>/dev/null | cut -d \| -f 1 | grep -qw "$DB_NAME"
}

# Display separator line
print_separator() {
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Display header
print_header() {
    print_separator
    echo -e "${COLOR_CYAN}$1${COLOR_RESET}"
    print_separator
}
