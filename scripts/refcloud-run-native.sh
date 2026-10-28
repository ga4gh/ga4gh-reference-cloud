#!/bin/zsh

RESEARCHER_UI_PID=0
APPS_DOCS_PID=0
CADDY_PID=0

TRAPINT() {
    print "\nexit request received, shutting down services"
    kill $CADDY_PID
    kill $APPS_DOCS_PID
    kill $RESEARCHER_UI_PID
    print "all services stopped, exiting"
    exit
}

log_info() {
  echo "[INFO] $1"
}

print "starting refcloud dev services in native mode"

cd apps/researcher-ui && npm run dev -- --port 3000 > /dev/null 2>&1 &
RESEARCHER_UI_PID=$!
cd ../..

cd apps/docs && npm run start -- --port 3001 --no-open > /dev/null 2>&1 &
APPS_DOCS_PID=$!
cd ../..
cd deploy/local && caddy run --config Caddyfile > /dev/null 2>&1 &
CADDY_PID=$!

print "refcloud dev services are now running:"
print " * researcher ui service: https://refcloud.ga4gh.local"
print " * docs service: https://docs.refcloud.ga4gh.local"
print "press Ctrl+C to stop all services and exit"

while true; do
    sleep 1
done
