#!/bin/zsh

# ##############################
# OPTIONS
# ##############################

typeset -A opts

zparseopts -D -A opts \
  r \
  -reset \
  -=opts

# ##############################
# SHARED CONSTANTS & FUNCTIONS
# ##############################

source ./scripts/lib/common.sh

# ##############################
# MAIN SCRIPT
# ##############################

log_info "running refcloud setup script"
log_info "first checking that required dependencies are installed"

# CHECK FOR REQUIRED DEPENDENCIES

psql --version > /dev/null 2>&1 || { log_error "psql command not found, please install it and try again"; exit 1; }
liquibase -v > /dev/null 2>&1 || { log_error "liquibase command not found, please install it and try again"; exit 1; }
hydra version > /dev/null 2>&1 || { log_error "hydra command not found, please install it and try again"; exit 1; }
node -v > /dev/null 2>&1 || { log_error "node command not found, please install it and try again"; exit 1; }
npm -v > /dev/null 2>&1 || { log_error "npm command not found, please install it and try again"; exit 1; }
npx -v > /dev/null 2>&1 || { log_error "npx command not found, please install it and try again"; exit 1; }

# kubernetes tools are optional: without them, only native mode is set up
KUBERNETES_ENABLED=false
if kubernetes_dependencies_installed; then
  KUBERNETES_ENABLED=true
fi

# RESET SEQUENCE IF RESET FLAG DETECTED

if (( ${+opts[-r]} || ${+opts[--reset]} )); then
    log_info "reset flag detected, running reset sequence prior to setup"

    # before dropping databases, as in-cluster services hold connections to them
    if [[ "$KUBERNETES_ENABLED" == true ]]; then
      reset_kubernetes
    fi

    log_info "deleting dependencies for admin-cli"
    rm -rf ./apps/admin-cli/.venv
    log_info "deleting dependencies for docs"
    rm -rf ./apps/docs/node_modules
    log_info "deleting dependencies for researcher-ui"
    rm -rf ./apps/researcher-ui/node_modules
    log_info "deleting dependencies for core-api"
    rm -rf ./apps/core-api/.gradle ./apps/core-api/build
    destroy_db "kratos"
    destroy_db "hydra"
    destroy_db "refcloudapi"
    destroy_data_connect_reader_role
else
    log_info "reset flag not detected, running script normally"
fi

# SETUP DATABASES AND ROLES

log_info "setting up required databases and roles"

init_db "kratos"
init_db "hydra"
init_db "refcloudapi"
create_data_connect_reader_role

# APPLY LIQUIBASE MIGRATIONS

apply_liquibase_migrations "./apps/core-api/liquibase/dbchangelog.xml" "$PSQL_JDBC_URL" "$PSQL_USERNAME" "$PSQL_PASSWORD"

# SETUP ORY HYDRA CLIENTS

log_info "setting up required ORY Hydra clients"

start_docker_compose
find_hydra_client "$ORY_HYDRA_RESEARCHER_CLIENT_ID" "$ORY_HYDRA_ENDPOINT"
if [[ $? -ne 0 ]]; then
    log_info "ORY Hydra client '$ORY_HYDRA_RESEARCHER_CLIENT_ID' does not exist, creating it"
    create_hydra_client "$ORY_HYDRA_RESEARCHER_CLIENT_ID" "secret" "https://$REFCLOUD_BASE_URL/passport/callback" "authorization_code,refresh_token" "code" "openid,offline_access,profile" "$ORY_HYDRA_ENDPOINT"
fi
stop_docker_compose

# KUBERNETES SETUP (HELM MODE)

if [[ "$KUBERNETES_ENABLED" == true ]]; then
  log_info "setting up kubernetes (minikube) for helm mode"
  start_minikube
  use_minikube_context
  create_tls_cert
  apply_k8s_secrets
fi

# APP SETUP

log_info "installing dependencies for all apps"

# apps/core-api

log_info "installing dependencies for apps/core-api"

cd apps/core-api && ./gradlew dependencies > /dev/null 2>&1 || { log_error "failed to install dependencies for apps/core-api"; exit 1; }
cd ../..

# apps/researcher-ui

log_info "installing dependencies for apps/researcher-ui"

cd apps/researcher-ui && npm install > /dev/null 2>&1 || { log_error "failed to install dependencies for apps/researcher-ui"; exit 1; }
cd ../..

# apps/docs

log_info "installing dependencies for apps/docs"

cd apps/docs && npm install > /dev/null 2>&1 || { log_error "failed to install dependencies for apps/docs"; exit 1; }
cd ../..

# apps/admin-cli

log_info "installing dependencies for apps/admin-cli"

cd apps/admin-cli && uv sync > /dev/null 2>&1 || { log_error "failed to install dependencies for apps/admin-cli"; exit 1; }
cd ../..

# DATASET ONBOARDING

log_info "onboarding datasets into the reference cloud"

cd apps/admin-cli

# seed upserts, so it is safe to re-run
log_info "seeding platform-wide data"
uv run refcloud seed > /dev/null 2>&1 || { log_error "failed to seed refcloud database"; exit 1; }

init_and_onboard_dataset "aws_opendata_registry_1000_genomes"
init_and_onboard_dataset "ga4gh_refget_sequences_v2_conformance"

cd ../..

log_info "refcloud setup complete, you may now proceed to run the reference cloud locally"
