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

log_info() {
  echo "[INFO] $1"
}

log_error() {
  echo "[ERROR] $1"
}

# DOCKER COMPOSE

start_docker_compose() {
  log_info "starting docker compose"
  docker compose -f ./deploy/local/docker-compose.yml up -d --wait > /dev/null 2>&1 || { log_error "failed to start docker compose"; exit 1; }
}

stop_docker_compose() {
  log_info "stopping docker compose"
  docker compose -f ./deploy/local/docker-compose.yml down > /dev/null 2>&1 || { log_error "failed to stop docker compose"; exit 1; }
}

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

cd apps/docs && npm run start -- --port 3001 --no-open > /dev/null 2>&1 &
APPS_DOCS_PID=$!
cd ../..

cd deploy/local && caddy run --config Caddyfile > /dev/null 2>&1 &
CADDY_PID=$!
cd ../..

log_info ""
log_info "Refcloud dev services are now running:"
log_info " * Researcher UI service: https://refcloud.ga4gh.localhost"
log_info " * Docs Service: https://docs.refcloud.ga4gh.localhost"
log_info " * Core API Services:"
log_info "   * Data Connect: https://refcloud.ga4gh.localhost/ga4gh/data-connect/v1"
log_info "   * DRS: https://refcloud.ga4gh.localhost/ga4gh/drs/v1"
log_info "   * RefGet: https://refcloud.ga4gh.localhost/ga4gh/refget/v2"
log_info ""
log_info "To begin, visit the Researcher UI service and register a new researcher account."
log_info "press Ctrl+C to stop all services and exit"

while true; do
    sleep 1
done
