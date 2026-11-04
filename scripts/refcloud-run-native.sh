#!/bin/zsh

CORE_API_PID=0
RESEARCHER_UI_PID=0
APPS_DOCS_PID=0
CADDY_PID=0

# ##############################
# FUNCTIONS
# ##############################

TRAPINT() {
    log_info "\nexit request received, shutting down services"
    kill $CADDY_PID
    kill $APPS_DOCS_PID
    kill $RESEARCHER_UI_PID
    kill $CORE_API_PID
    stop_docker_compose
    log_info "all services stopped, exiting"
    exit
}

source ./scripts/lib/common.sh

# ##############################
# MAIN SCRIPT
# ##############################

log_info "starting refcloud dev services in native mode"

log_info "starting docker compose"
start_docker_compose

cd apps/core-api && ./gradlew bootRun > /dev/null 2>&1 &
CORE_API_PID=$!
cd ../..

cd apps/researcher-ui && npm run dev -- --port 3000 > /dev/null 2>&1 &
RESEARCHER_UI_PID=$!
cd ../..

cd apps/docs && npm run start -- --host 127.0.0.1 --port 3001 --no-open > /dev/null 2>&1 &
APPS_DOCS_PID=$!
cd ../..

cd deploy/local && caddy run --config Caddyfile > /dev/null 2>&1 &
CADDY_PID=$!
cd ../..

print_service_urls
log_info "press Ctrl+C to stop all services and exit"

while true; do
    sleep 1
done
