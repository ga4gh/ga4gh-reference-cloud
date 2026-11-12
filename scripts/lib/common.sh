# Shared constants and functions for the refcloud scripts.
# Source from a script run at the repo root: source ./scripts/lib/common.sh

# ##############################
# CONSTANTS
# ##############################

REFCLOUD_BASE_URL="refcloud.ga4gh.localhost"

PSQL_JDBC_URL="jdbc:postgresql://localhost:5432/refcloudapi"
PSQL_USERNAME="refcloudapi"
PSQL_PASSWORD="secret"
PSQL_DATABASE="refcloudapi"

ORY_HYDRA_RESEARCHER_CLIENT_ID="ga4gh-reference-cloud-researcher-client"
ORY_HYDRA_ENDPOINT="http://localhost:4445"

# compute (native mode). the connections file is shared by core-api and the admin CLI; its storage root must be the
# same directory as REFCLOUD_COMPUTE_STORAGE_ROOT, which docker-compose.yml mounts into Funnel at the same absolute path
COMPUTE_CONNECTIONS_FILE="./deploy/local/compute/connections.yml"
export REFCLOUD_COMPUTE_STORAGE_ROOT="$PWD/compute/nextflow-funnel-docker"
# the platform setup registers, on the connection below, with the bundled demo workflow on its allowlist
COMPUTE_CONNECTION_KEY="nextflow-funnel-docker-local"
DEFAULT_COMPUTE_PLATFORM_ID="nextflow-demo"
# the demo's input: a 1000 Genomes BAM index (compute/workflows/nextflow/drs-checksum/README.md)
DEMO_DRS_URI="drs://$REFCLOUD_BASE_URL/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai"
DEMO_DATASET_VISA="ga4gh_visa:aws_opendata_registry_1000_genomes"

# kubernetes (helm mode). all kubectl/helm calls must go through kubectl_local/helm_local,
# which pass the context and namespace explicitly so they can never touch a non-local cluster
# and always agree on where secrets and the release live.
KUBE_CONTEXT="minikube"
K8S_NAMESPACE="default"
HELM_RELEASE="refcloud"
TLS_CERT_DIR="./deploy/helm/.certs"
# secrets the helm chart expects, deleted on --reset
K8S_SECRETS=(
  common-db-info
  kratos-db-credentials
  hydra-db-credentials
  refcloud-api-db-credentials
  refcloud-api-data-connect-db-credentials
  kratos-secrets
  hydra-secrets
  refcloud-ui-hydra-researcher-client
  refcloud-api-kratos-webhook
  refcloud-tls
)
# refcloud app images built for helm mode, as "<repository>:<docker build context>"
REFCLOUD_IMAGES=(
  "refcloud-core-api:apps/core-api"
  "refcloud-core-api-liquibase:apps/core-api/liquibase"
  "refcloud-researcher-ui:apps/researcher-ui"
  "refcloud-docs:apps/docs"
  "refcloud-admin-cli:apps/admin-cli"
)
# deployments running refcloud app images (restarted after each build)
REFCLOUD_APP_DEPLOYMENTS=(refcloud-api refcloud-ui refcloud-docs refcloud-admin-cli)
# all deployments in the release (waited on after each deploy)
REFCLOUD_ALL_DEPLOYMENTS=(kratos hydra "${REFCLOUD_APP_DEPLOYMENTS[@]}")

# ##############################
# LOGGING
# ##############################

log_info() {
  echo "[INFO] $1"
}

log_warn() {
  echo "[WARN] $1"
}

log_error() {
  echo "[ERROR] $1"
}

# pass "compute" to also list the compute platforms (native mode only; helm mode has compute off)
print_service_urls() {
  log_info ""
  log_info "Refcloud dev services are now running:"
  log_info " * Researcher UI service: https://$REFCLOUD_BASE_URL"
  log_info " * Docs Service: https://docs.$REFCLOUD_BASE_URL"
  log_info " * Core API Services:"
  log_info "   * Data Connect: https://$REFCLOUD_BASE_URL/ga4gh/data-connect/v1"
  log_info "   * DRS: https://$REFCLOUD_BASE_URL/ga4gh/drs/v1"
  log_info "   * RefGet: https://$REFCLOUD_BASE_URL/ga4gh/refget/v2"
  log_info "   * Service Registry: https://$REFCLOUD_BASE_URL/ga4gh/service-registry/v1"
  if [[ "$1" == "compute" ]]; then
    print_compute_urls
  fi
  log_info ""
  log_info "To begin, visit the Researcher UI service and register a new researcher account."
}

# each enabled compute platform's WES and TES, and how to run the demo
print_compute_urls() {
  local platforms=$(psql -d "$PSQL_DATABASE" -tAc "SELECT id FROM compute_platform WHERE enabled ORDER BY id;" 2>/dev/null)
  log_info " * Compute (WES and TES, one of each per compute platform):"
  if [[ -z "$platforms" ]]; then
    log_info "   * no compute platforms are enabled. setup registers '$DEFAULT_COMPUTE_PLATFORM_ID' when Nextflow is installed (see deploy/local/README.md)"
    return 0
  fi
  local platform
  for platform in ${(f)platforms}; do
    log_info "   * $platform: https://$REFCLOUD_BASE_URL/ga4gh/wes/$platform/v1 and https://$REFCLOUD_BASE_URL/ga4gh/tes/$platform/v1"
  done
  log_info "   * demo: run the bundled drs-checksum workflow (refcloud:bundled/drs-checksum) on $DEMO_DRS_URI"
  log_info "     it needs approved visas for the platform (ga4gh_visa:compute_platform:<platform>) and the 1000 Genomes dataset ($DEMO_DATASET_VISA)"
  log_info "     see compute/workflows/nextflow/drs-checksum/README.md"
}

# ##############################
# VERSIONING
# ##############################

# lockstep image tag: git describe, plus "-uncommitted" when the working tree has any changes
# (including untracked files, which 'git describe --dirty' ignores but docker builds include)
refcloud_image_tag() {
  local tag=$(git describe --tags --match 'v[0-9]*' --always)
  if [[ -n "$(git status --porcelain)" ]]; then
    tag="${tag}-uncommitted"
  fi
  echo "$tag"
}

# ##############################
# DOCKER COMPOSE
# ##############################

start_docker_compose() {
  log_info "starting docker compose"
  mkdir -p "$REFCLOUD_COMPUTE_STORAGE_ROOT"
  docker compose -f ./deploy/local/docker-compose.yml up -d --wait > /dev/null 2>&1 || { log_error "failed to start docker compose"; exit 1; }
}

stop_docker_compose() {
  log_info "stopping docker compose"
  docker compose -f ./deploy/local/docker-compose.yml down > /dev/null 2>&1 || { log_error "failed to stop docker compose"; exit 1; }
}

# ##############################
# DATABASE
# ##############################

db_role_exists() {
  [[ "$(psql -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$1';")" == "1" ]]
}

db_exists() {
  [[ "$(psql -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$1';")" == "1" ]]
}

# creates a database and a login role of the same name that owns it; skips what already exists
init_db() {
  if db_exists "$1"; then
    log_info "database '$1' already exists, skipping creation"
  else
    log_info "initializing $1 db"
    psql -d postgres -c "CREATE DATABASE $1;"
  fi

  if db_role_exists "$1"; then
    log_info "role '$1' already exists, skipping creation"
  else
    log_info "initializing $1 role"
    psql -d postgres -c "CREATE ROLE $1 WITH LOGIN ENCRYPTED PASSWORD 'secret';"
  fi

  psql -d postgres -c "ALTER DATABASE $1 OWNER TO $1;"
}

destroy_db() {
  log_info "deleting $1 db and role"
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
  if db_role_exists "data_connect_reader"; then
    log_info "role 'data_connect_reader' already exists, skipping creation"
  else
    psql -d postgres -c "CREATE ROLE data_connect_reader LOGIN;"
  fi
  psql -d postgres -c "ALTER ROLE data_connect_reader WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD 'secret';"
  psql -d postgres -c "ALTER ROLE data_connect_reader SET default_transaction_read_only = on;"
  psql -d postgres -c "ALTER ROLE data_connect_reader SET statement_timeout = '30s';"
}

destroy_data_connect_reader_role() {
  log_info "destroying data connect reader role in database"
  psql -d postgres -c "DROP ROLE IF EXISTS data_connect_reader;"
}

# ##############################
# DATASETS (ADMIN CLI)
# ##############################

dataset_exists() {
  [[ "$(psql -d "$PSQL_DATABASE" -tAc "SELECT 1 FROM dataset WHERE id = '$1';")" == "1" ]]
}

# runs 'dataset init' and 'dataset onboard' for a dataset that doesn't exist yet. onboarding is
# not idempotent, so existing datasets (including partially onboarded ones) are skipped.
# must be called from apps/admin-cli
init_and_onboard_dataset() {
  local dataset_id=$1

  if dataset_exists "$dataset_id"; then
    log_info "dataset '$dataset_id' already exists, skipping init and onboard. to re-onboard it, run setup with --reset or 'uv run refcloud dataset reset -d $dataset_id' then 'uv run refcloud dataset onboard -d $dataset_id' in apps/admin-cli"
    return 0
  fi

  log_info "initializing dataset '$dataset_id'"
  uv run refcloud dataset init -d "$dataset_id" > /dev/null 2>&1 || { log_error "failed to initialize dataset '$dataset_id'"; exit 1; }

  log_info "onboarding dataset '$dataset_id' (this may take a while)"
  uv run refcloud dataset onboard -d "$dataset_id" > /dev/null 2>&1 || { log_error "failed to onboard dataset '$dataset_id'"; exit 1; }
}

# ##############################
# COMPUTE (NATIVE MODE)
# ##############################

# the nextflow version pinned in the connections file. core-api runs exactly this version (NXF_VER), whatever version
# of the nextflow launcher is installed
pinned_nextflow_version() {
  awk '$1 == "nextflow-version:" { print $2; exit }' "$COMPUTE_CONNECTIONS_FILE"
}

# compute needs nextflow on the PATH, able to run the pinned version. the first check downloads that version into
# ~/.nextflow (internet access needed), as core-api's own health check would
nextflow_installed() {
  if ! command -v nextflow > /dev/null 2>&1; then
    log_warn "nextflow command not found, skipping compute setup. install Nextflow (see the requirements in README.md) to run workflows in native mode"
    return 1
  fi
  local version=$(pinned_nextflow_version)
  log_info "checking that nextflow can run version $version (pinned in $COMPUTE_CONNECTIONS_FILE; the first run downloads it)"
  if ! NXF_VER="$version" nextflow -version 2>&1 | grep -q "version $version "; then
    log_warn "nextflow could not run version $version, skipping compute setup. run 'NXF_VER=$version nextflow -version' to see why"
    return 1
  fi
  return 0
}

compute_platform_exists() {
  [[ "$(psql -d "$PSQL_DATABASE" -tAc "SELECT 1 FROM compute_platform WHERE id = '$1';")" == "1" ]]
}

# registers the default compute platform with small per-user limits and the bundled demo on its allowlist, and enables
# it. an existing platform is left as is, since an admin may have changed it. must be called from apps/admin-cli
init_default_compute_platform() {
  local platform_id=$DEFAULT_COMPUTE_PLATFORM_ID

  if compute_platform_exists "$platform_id"; then
    log_info "compute platform '$platform_id' already exists, skipping. to recreate it, run 'uv run refcloud compute-platform delete -p $platform_id' in apps/admin-cli, then setup"
    return 0
  fi

  log_info "registering compute platform '$platform_id' on connection '$COMPUTE_CONNECTION_KEY'"
  uv run refcloud compute-platform init -p "$platform_id" \
    --name "Nextflow Demo" \
    --description "Run allowlisted Nextflow workflows on this machine (Funnel + Docker), with refcloud DRS objects as inputs" \
    --connection "$COMPUTE_CONNECTION_KEY" \
    --max-concurrent-runs 2 \
    --max-task-cpu-cores 2 \
    --max-task-memory-gb 2 \
    --max-task-disk-gb 10 \
    --max-run-duration-seconds 1800 \
    --monthly-core-hours 10 \
  > /dev/null 2>&1 || { log_error "failed to register compute platform '$platform_id'"; exit 1; }

  log_info "adding the bundled drs-checksum workflow to the allowlist of '$platform_id'"
  uv run refcloud compute-platform allowlist add -p "$platform_id" --bundled drs-checksum \
    --name "DRS checksum" \
    --description "Report a DRS object's name, size, MD5 and SHA-256" \
  > /dev/null 2>&1 || { log_error "failed to allowlist drs-checksum on '$platform_id'"; exit 1; }

  log_info "enabling compute platform '$platform_id'"
  uv run refcloud compute-platform enable -p "$platform_id" > /dev/null 2>&1 \
    || { log_error "failed to enable compute platform '$platform_id'"; exit 1; }
}

# compute storage is runtime data (run directories, Funnel's database and work directory)
reset_compute_storage() {
  if [[ "$REFCLOUD_COMPUTE_STORAGE_ROOT" != */compute/nextflow-funnel-docker ]]; then
    log_error "refusing to delete unexpected compute storage root '$REFCLOUD_COMPUTE_STORAGE_ROOT'"
    exit 1
  fi
  log_info "deleting compute storage in '$REFCLOUD_COMPUTE_STORAGE_ROOT'"
  rm -rf "$REFCLOUD_COMPUTE_STORAGE_ROOT" || { log_error "failed to delete compute storage (files created by task containers may need sudo on Linux)"; exit 1; }
}

# ##############################
# ORY HYDRA
# ##############################

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
# KUBERNETES (MINIKUBE)
# ##############################

kubectl_local() {
  kubectl --context "$KUBE_CONTEXT" --namespace "$K8S_NAMESPACE" "$@"
}

helm_local() {
  helm --kube-context "$KUBE_CONTEXT" --namespace "$K8S_NAMESPACE" "$@"
}

kubernetes_dependencies_installed() {
  local cmd
  for cmd in minikube kubectl helm mkcert; do
    if ! command -v $cmd > /dev/null 2>&1; then
      log_warn "$cmd command not found, skipping kubernetes (helm mode) setup. install minikube, kubectl, helm and mkcert to run the refcloud via helm"
      return 1
    fi
  done
  return 0
}

minikube_running() {
  minikube status > /dev/null 2>&1
}

start_minikube() {
  if minikube_running; then
    log_info "minikube is already running"
  else
    log_info "starting minikube"
    minikube start --driver=docker > /dev/null 2>&1 || { log_error "failed to start minikube, check 'minikube logs'. if the cluster is broken, run 'minikube delete' and try again"; exit 1; }
  fi

  log_info "enabling minikube ingress addon"
  minikube addons enable ingress > /dev/null 2>&1 || { log_error "failed to enable minikube ingress addon"; exit 1; }
}

use_minikube_context() {
  local current_context=$(kubectl config current-context 2>/dev/null)
  if [[ "$current_context" != "$KUBE_CONTEXT" ]]; then
    log_info "switching kubectl context from '${current_context:-<none>}' to '$KUBE_CONTEXT'"
    kubectl config use-context "$KUBE_CONTEXT" > /dev/null 2>&1 || { log_error "failed to switch kubectl context to '$KUBE_CONTEXT'"; exit 1; }
  fi
}

create_tls_cert() {
  local cert_file="$TLS_CERT_DIR/tls.crt"
  local key_file="$TLS_CERT_DIR/tls.key"

  log_info "installing mkcert local CA into system trust store (may prompt for password)"
  mkcert -install > /dev/null 2>&1 || { log_error "failed to install mkcert local CA"; exit 1; }

  if [[ -f "$cert_file" && -f "$key_file" ]]; then
    log_info "TLS certificate already exists in '$TLS_CERT_DIR'"
    return 0
  fi

  log_info "generating TLS certificate for '$REFCLOUD_BASE_URL' in '$TLS_CERT_DIR'"
  mkdir -p "$TLS_CERT_DIR"
  mkcert -cert-file "$cert_file" -key-file "$key_file" \
    "$REFCLOUD_BASE_URL" "*.$REFCLOUD_BASE_URL" \
  > /dev/null 2>&1 || { log_error "failed to generate TLS certificate"; exit 1; }
}

# creates or updates a secret, e.g. apply_k8s_secret generic my-secret --from-literal=key=value
apply_k8s_secret() {
  local secret_type=$1
  local secret_name=$2
  shift 2

  log_info "applying kubernetes secret '$secret_name'"
  kubectl_local create secret "$secret_type" "$secret_name" "$@" --dry-run=client -o yaml \
    | kubectl_local apply -f - > /dev/null 2>&1 \
  || { log_error "failed to apply kubernetes secret '$secret_name'"; exit 1; }
}

# values match native mode (deploy/local, apps/core-api application.yml, the constants above)
apply_k8s_secrets() {
  log_info "applying kubernetes secrets required by the helm chart"

  # host.minikube.internal is provided by minikube and reaches host postgres; connections
  # arrive from 127.0.0.1, so no pg_hba.conf/listen_addresses changes are needed
  apply_k8s_secret generic common-db-info \
    --from-literal=host=host.minikube.internal \
    --from-literal=port=5432
  apply_k8s_secret generic kratos-db-credentials \
    --from-literal=username=kratos \
    --from-literal=password=secret
  apply_k8s_secret generic hydra-db-credentials \
    --from-literal=username=hydra \
    --from-literal=password=secret
  apply_k8s_secret generic refcloud-api-db-credentials \
    --from-literal=username="$PSQL_USERNAME" \
    --from-literal=password="$PSQL_PASSWORD"
  apply_k8s_secret generic refcloud-api-data-connect-db-credentials \
    --from-literal=username=data_connect_reader \
    --from-literal=password=secret
  # must match deploy/local/ory/kratos/kratos.yml (kratos db is shared with native mode)
  apply_k8s_secret generic kratos-secrets \
    --from-literal=cookie=PLEASE-CHANGE-ME-I-AM-VERY-INSECURE \
    --from-literal=cipher=32-LONG-SECRET-NOT-SECURE-AT-ALL
  # must match deploy/local/ory/hydra/hydra.yml: hydra encrypts stored client secrets with the
  # system secret, so a mismatch makes the researcher client created by setup unusable
  apply_k8s_secret generic hydra-secrets \
    --from-literal=system-secret=b957c641285730151983fcde377fe4cd28935d493173b15c3de06c8b46685397 \
    --from-literal=pairwise-salt=b957c641285730151983fcde377fe4cd28935d493173b15c3de06c8b46685397
  apply_k8s_secret generic refcloud-ui-hydra-researcher-client \
    --from-literal=id="$ORY_HYDRA_RESEARCHER_CLIENT_ID" \
    --from-literal=secret=secret
  apply_k8s_secret generic refcloud-api-kratos-webhook \
    --from-literal=username=kratos_webhook_user \
    --from-literal=password=secret
  apply_k8s_secret tls refcloud-tls \
    --cert="$TLS_CERT_DIR/tls.crt" \
    --key="$TLS_CERT_DIR/tls.key"
}

# removes the TLS certificate (recreated fresh by create_tls_cert), the helm release (it holds
# db connections) and refcloud secrets
reset_kubernetes() {
  log_info "deleting TLS certificate in '$TLS_CERT_DIR'"
  rm -rf "$TLS_CERT_DIR"

  # start minikube if stopped so the release and secrets are always cleaned up
  start_minikube

  if helm_local status "$HELM_RELEASE" > /dev/null 2>&1; then
    log_info "uninstalling helm release '$HELM_RELEASE'"
    helm_local uninstall "$HELM_RELEASE" --wait > /dev/null 2>&1 || { log_error "failed to uninstall helm release '$HELM_RELEASE'"; exit 1; }
  fi

  log_info "deleting refcloud kubernetes secrets"
  kubectl_local delete secret "${K8S_SECRETS[@]}" --ignore-not-found > /dev/null 2>&1 || { log_error "failed to delete kubernetes secrets"; exit 1; }
}
