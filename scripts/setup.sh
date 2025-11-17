#!/usr/bin/env bash
set -e

# Load common utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common.sh"

print_header "🚀 Prism Chat Demo - Environment Setup"

# Check project root
check_project_root

# Check prerequisites
log_info "Checking prerequisites..."

# Check Node.js
if ! command_exists node; then
    log_error "Node.js is not installed"
    log_info "Please install Node.js >= 18 from https://nodejs.org/"
    exit 1
fi

NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 18 ]; then
    log_error "Node.js version $NODE_VERSION is too old. Required: >= 18"
    exit 1
fi
log_success "Node.js $(node -v) found"

# Check Python
if ! command_exists python3; then
    log_error "Python 3 is not installed"
    log_info "Please install Python >= 3.11"
    exit 1
fi

PYTHON_VERSION=$(python3 --version | cut -d' ' -f2 | cut -d'.' -f1,2)
log_success "Python $(python3 --version) found"

# Check PostgreSQL
if ! command_exists psql; then
    log_error "PostgreSQL is not installed"
    log_info "Please install PostgreSQL >= 14"
    log_info "macOS: brew install postgresql@14"
    log_info "Linux: sudo apt-get install postgresql-14"
    exit 1
fi
log_success "PostgreSQL found"

# Check if PostgreSQL is running
if ! psql -U postgres -c '\l' >/dev/null 2>&1; then
    log_warn "PostgreSQL is not running or not accessible"
    log_info "macOS: brew services start postgresql@14"
    log_info "Linux: sudo systemctl start postgresql"
    exit 1
fi
log_success "PostgreSQL is running"

# Check/Install pnpm
if ! command_exists pnpm; then
    log_warn "pnpm not found, installing..."
    npm install -g pnpm
    log_success "pnpm installed"
else
    log_success "pnpm $(pnpm -v) found"
fi

# Check/Install Poetry
if ! command_exists poetry; then
    log_warn "Poetry not found, installing..."
    curl -sSL https://install.python-poetry.org | python3 -
    log_success "Poetry installed"
    log_info "You may need to add Poetry to your PATH"
    log_info "See: https://python-poetry.org/docs/#installation"
else
    log_success "Poetry $(poetry --version | cut -d' ' -f3) found"
fi

print_separator

# Install frontend dependencies
log_info "Installing frontend dependencies..."
cd "$FRONTEND_DIR"
pnpm install
log_success "Frontend dependencies installed"

# Build frontend packages
log_info "Building @prism/client package..."
cd "$FRONTEND_DIR/packages/prism-client"
pnpm run build
log_success "@prism/client built"

log_info "Building @prism/vue package..."
cd "$FRONTEND_DIR/packages/prism-vue"
pnpm run build
log_success "@prism/vue built"

print_separator

# Install backend dependencies
log_info "Installing backend dependencies..."
cd "$BACKEND_DIR"
poetry install
log_success "Backend dependencies installed"

print_separator

# Create database
log_info "Setting up database..."
if database_exists; then
    log_success "Database '$DB_NAME' already exists"
else
    log_info "Creating database '$DB_NAME'..."
    psql -U postgres -c "CREATE DATABASE $DB_NAME;" 2>/dev/null || log_warn "Could not create database (may need manual creation)"

    if database_exists; then
        log_success "Database '$DB_NAME' created"
    else
        log_error "Failed to create database"
        log_info "Please create it manually: psql -U postgres -c \"CREATE DATABASE $DB_NAME;\""
    fi
fi

print_separator

# Install E2E test dependencies
log_info "Installing E2E test dependencies..."
cd "$E2E_DIR"
if [ -f "package.json" ]; then
    npm install
    log_success "E2E dependencies installed"

    # Install Playwright browsers if not already installed
    if ! npx playwright --version >/dev/null 2>&1; then
        log_info "Installing Playwright browsers..."
        npx playwright install chromium
        log_success "Playwright browsers installed"
    else
        log_success "Playwright already installed"
    fi
else
    log_warn "No E2E package.json found"
fi

print_separator

# Verify setup
log_info "Verifying setup..."

cd "$PROJECT_ROOT"

# Check backend
if [ -d "$BACKEND_DIR/prism" ] && [ -d "$BACKEND_DIR/chat_demo" ]; then
    log_success "Backend source files found"
else
    log_error "Backend source files not found"
fi

# Check frontend
if [ -d "$FRONTEND_DIR/chat-demo/src" ]; then
    log_success "Frontend source files found"
else
    log_error "Frontend source files not found"
fi

# Check built packages
if [ -d "$FRONTEND_DIR/packages/prism-client/dist" ]; then
    log_success "@prism/client package built"
else
    log_error "@prism/client not built"
fi

if [ -d "$FRONTEND_DIR/packages/prism-vue/dist" ]; then
    log_success "@prism/vue package built"
else
    log_error "@prism/vue not built"
fi

print_separator

log_success "Setup complete! 🎉"
echo ""
log_info "Next steps:"
echo "  1. Start servers:    ./scripts/start-all.sh"
echo "  2. Run tests:        ./scripts/test-all.sh"
echo "  3. View logs:        ./scripts/logs.sh"
echo "  4. Check status:     ./scripts/status.sh"
echo ""
log_info "For development workflow, see CLAUDE.md"
print_separator
