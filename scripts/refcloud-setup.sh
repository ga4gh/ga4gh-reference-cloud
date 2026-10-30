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
# CONSTANTS
# ##############################

PSQL_JDBC_URL="jdbc:postgresql://localhost:5432/refcloudapi"
PSQL_USERNAME="refcloudapi"
PSQL_PASSWORD="secret"
PSQL_DATABASE="refcloudapi"

ORY_HYDRA_RESEARCHER_CLIENT_ID="ga4gh-reference-cloud-researcher-client"
ORY_HYDRA_ENDPOINT="http://localhost:4445"



# ##############################
# FUNCTIONS
# ##############################

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

# DATABASE

init_db() {
  psql -d postgres -c "CREATE DATABASE $1;"
  psql -d postgres -c "CREATE ROLE $1 WITH LOGIN ENCRYPTED PASSWORD 'secret';"
  psql -d postgres -c "ALTER DATABASE $1 OWNER TO $1;"
}

destroy_db() {
  psql -d postgres -c "DROP DATABASE $1;"
  psql -d postgres -c "DROP ROLE $1;"
}

apply_liquibase_migrations() {
  local changelog_file=$1
  local jdbc_url=$2
  local username=$3
  local password=$4

  log_info "applying Liquibase migrations using changelog file '$changelog_file'"

  liquibase \
    --changelog-file=${changelog_file} \
    --url="${jdbc_url}" \
    --username=${username} \
    --password=${password} \
    update \
  > /dev/null 2>&1 || { log_error "failed to apply Liquibase migrations"; exit 1; }
}

create_data_connect_reader_role() {
  log_info "creating data connect reader role in database"
  psql -d postgres -c "ALTER ROLE data_connect_reader WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD 'secret';"
  psql -d postgres -c "ALTER ROLE data_connect_reader SET default_transaction_read_only = on;"
  psql -d postgres -c "ALTER ROLE data_connect_reader SET statement_timeout = '30s';"
}

destroy_data_connect_reader_role() {
  log_info "destroying data connect reader role in database"
  psql -d postgres -c "DROP ROLE IF EXISTS data_connect_reader;"
}

# ORY HYDRA

find_hydra_client() {
  local client_id=$1
  local hydra_endpoint=$2
  log_info "checking if ORY Hydra client '$client_id' exists at endpoint '$hydra_endpoint'"
  hydra get oauth2-client "$client_id" -e $hydra_endpoint > /dev/null 2>&1
  if [[ $? -eq 0 ]]; then
    log_info "ORY Hydra client '$client_id' exists"
    return 0
  else
    log_info "ORY Hydra client '$client_id' does not exist"
    return 1
  fi
}

create_hydra_client() {
  local client_id=$1
  local client_secret=$2
  local redirect_uris=$3
  local grant_types=$4
  local response_types=$5
  local scope=$6
  local hydra_endpoint=$7

  log_info "creating ORY Hydra client '$client_id' with redirect URIs '$redirect_uris', grant types '$grant_types', response types '$response_types', and scope '$scope'"

  hydra create oauth2-client \
    --id "$client_id" \
    --secret "$client_secret" \
    --grant-type "$grant_types" \
    --response-type "$response_types" \
    --scope "$scope" \
    --redirect-uri "$redirect_uris" \
    --token-endpoint-auth-method client_secret_post \
    -e "$hydra_endpoint" \
  > /dev/null 2>&1 || { log_error "failed to create ORY Hydra client '$client_id'"; exit 1; }
}

delete_hydra_client() {
  local client_id=$1
  local hydra_endpoint=$2

  log_info "deleting ORY Hydra client '$client_id' at endpoint '$hydra_endpoint'"

  hydra delete oauth2-client "$client_id" -e $hydra_endpoint > /dev/null 2>&1 || { log_error "failed to delete ORY Hydra client '$client_id'"; exit 1; }
}

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

# RESET SEQUENCE IF RESET FLAG DETECTED

if (( ${+opts[-r]} || ${+opts[--reset]} )); then
    log_info "reset flag detected, running reset sequence prior to setup"
    destroy_data_connect_reader_role
    destroy_db "kratos"
    destroy_db "hydra"
    destroy_db "refcloudapi"
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
    create_hydra_client "$ORY_HYDRA_RESEARCHER_CLIENT_ID" "secret" "https://refcloud.ga4gh.local/passport,https://refcloud.ga4gh.local/drs" "authorization_code,refresh_token" "code" "openid,offline_access,profile" "$ORY_HYDRA_ENDPOINT"
fi
stop_docker_compose

# apps/researcher-ui

log_info "installing all required dependencies for apps/docs"
log_info "installing node_modules for apps/docs"

cd apps/researcher-ui && npm install > /dev/null 2>&1 || { log_error "failed to install dependencies for apps/researcher-ui"; exit 1; }
cd ../..

# apps/docs

log_info "installing all required dependencies for apps/docs"
log_info "installing node_modules for apps/docs"

cd apps/docs && npm install > /dev/null 2>&1 || { log_error "failed to install dependencies for apps/docs"; exit 1; }
cd ../..

log_info "refcloud setup complete, you may now proceed to run the reference cloud locally"