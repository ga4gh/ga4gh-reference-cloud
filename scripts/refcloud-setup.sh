#!/bin/zsh

typeset -A opts

zparseopts -D -A opts \
  r \
  -reset \
  -=opts

log_info() {
  echo "[INFO] $1"
}

log_error() {
  echo "[ERROR] $1"
}

init_db() {
  psql -d postgres -c "CREATE DATABASE $1;"
  psql -d postgres -c "CREATE ROLE $1 WITH ENCRYPTED PASSWORD 'secret';"
  psql -d postgres -c "ALTER DATABASE $1 OWNER TO $1;"
}

destroy_db() {
  psql -d postgres -c "DROP DATABASE $1;"
  psql -d postgres -c "DROP ROLE $1;"
}

# script start

log_info "running refcloud setup script"
log_info "first checking that required dependencies are installed"

# check for required dependencies

psql --version > /dev/null 2>&1 || { log_error "psql command not found, please install it and try again"; exit 1; }
node -v > /dev/null 2>&1 || { log_error "node command not found, please install it and try again"; exit 1; }
npm -v > /dev/null 2>&1 || { log_error "npm command not found, please install it and try again"; exit 1; }
npx -v > /dev/null 2>&1 || { log_error "npx command not found, please install it and try again"; exit 1; }

# run reset sequence if -r or --reset flag is detected

if (( ${+opts[-r]} || ${+opts[--reset]} )); then
    log_info "reset flag detected, running reset sequence prior to setup"
    # Place your reset logic here
else
    log_info "reset flag not detected, running script normally"
fi

# setup required databases and roles

log_info "setting up required databases and roles"

init_db "kratos"
init_db "hydra"
init_db "refcloudapi"

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