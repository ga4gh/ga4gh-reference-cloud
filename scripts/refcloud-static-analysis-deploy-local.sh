#!/usr/bin/env bash

# Static analysis for the local deployment context (deploy/local) and the app Dockerfiles (apps/**/Dockerfile).
# Every tool runs from a pinned docker image (version + digest), so the only requirement is Docker, and
# local runs match CI (.github/workflows/static-analysis-deploy-local.yml), which calls this script.
#
#   ./scripts/refcloud-static-analysis-deploy-local.sh                run all blocking checks
#   ./scripts/refcloud-static-analysis-deploy-local.sh <check>...     run specific checks:
#       compose       validate deploy/local/docker-compose.yml (docker compose config)
#       caddy         validate and format-check deploy/local/Caddyfile (caddy validate, caddy fmt)
#       hadolint      lint the Dockerfiles (best practices, shellcheck of RUN lines); config: .hadolint.yaml
#       trivy-config  scan the Dockerfiles for security misconfigurations (HIGH/CRITICAL); ignores: .trivyignore
#       images        non-blocking: scan the images in docker-compose.yml and the Dockerfiles' final base images
#                     for CVEs. set SARIF_DIR to write one SARIF report per image instead of a table, and
#                     SCAN_IMAGES (space-separated) to scan only those images
#   ./scripts/refcloud-static-analysis-deploy-local.sh --list-images  print the images the 'images' check scans
#
# Written for bash 3.2 (macOS) as well as CI, so it avoids zsh and bash 4 features. Run from the repo root.

set -uo pipefail

# ##############################
# PINNED TOOL IMAGES (bump version and digest together)
# ##############################

TRIVY_IMAGE="aquasec/trivy:0.76.2@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb"
HADOLINT_IMAGE="hadolint/hadolint:v2.15.1-alpine@sha256:a1d49ae1a4e83c1dbad26b8c1ad7588c8bd1e04f4866b34ad3cac50335198552"
CADDY_IMAGE="caddy:2.11.9-alpine@sha256:0bbb055bda6793c999bde78b1b5ce3e89f829fb031af2cf50056ea1246559fee"

COMPOSE_FILE="deploy/local/docker-compose.yml"
CADDYFILE_DIR="deploy/local"
# trivy's vulnerability db and checks bundle, reused between runs
TRIVY_CACHE_DIR="${TRIVY_CACHE_DIR:-${HOME}/.cache/trivy}"

# ##############################
# FUNCTIONS
# ##############################

log_info() {
  echo "[INFO] $1"
}

log_error() {
  echo "[ERROR] $1" >&2
}

# app Dockerfiles, relative to the repo root (dependency directories excluded)
list_dockerfiles() {
  find apps -name Dockerfile -not -path '*/node_modules/*' -not -path '*/.venv/*' | sort
}

# trivy, with the repo mounted read-only at /repo and a persistent cache
trivy() {
  mkdir -p "${TRIVY_CACHE_DIR}"
  docker run --rm -v "${PWD}:/repo:ro" -v "${TRIVY_CACHE_DIR}:/root/.cache/trivy" "${TRIVY_IMAGE}" "$@"
}

check_compose() {
  log_info "validating ${COMPOSE_FILE}"
  docker compose -f "${COMPOSE_FILE}" config --quiet
}

check_caddy() {
  log_info "validating ${CADDYFILE_DIR}/Caddyfile"
  docker run --rm -v "${PWD}/${CADDYFILE_DIR}:/etc/caddy:ro" "${CADDY_IMAGE}" \
    caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile || return 1

  log_info "checking ${CADDYFILE_DIR}/Caddyfile formatting (fix with: caddy fmt --overwrite ${CADDYFILE_DIR}/Caddyfile)"
  # caddy fmt --diff always prints the file; it exits 1 when the formatting differs
  docker run --rm -v "${PWD}/${CADDYFILE_DIR}:/etc/caddy:ro" "${CADDY_IMAGE}" \
    caddy fmt --diff /etc/caddy/Caddyfile >/dev/null 2>&1 && return 0
  docker run --rm -v "${PWD}/${CADDYFILE_DIR}:/etc/caddy:ro" "${CADDY_IMAGE}" caddy fmt --diff /etc/caddy/Caddyfile
  return 1
}

check_hadolint() {
  local dockerfiles
  dockerfiles=$(list_dockerfiles)
  log_info "linting Dockerfiles: $(echo "${dockerfiles}" | tr '\n' ' ')"
  # shellcheck disable=SC2086 # word-split the file list into arguments
  docker run --rm -v "${PWD}:/repo:ro" -w /repo "${HADOLINT_IMAGE}" \
    hadolint --config /repo/.hadolint.yaml ${dockerfiles}
}

check_trivy_config() {
  log_info "scanning Dockerfiles for HIGH/CRITICAL misconfigurations"
  # dockerfile checks only: helm and other config formats are covered by their own checks
  trivy config --quiet --exit-code 1 --severity HIGH,CRITICAL \
    --misconfig-scanners dockerfile --ignorefile /repo/.trivyignore \
    --skip-dirs '**/node_modules' --skip-dirs '**/.venv' --skip-dirs '**/build' \
    /repo/apps
}

# images to scan: those docker-compose.yml runs, plus the final (shipped) stage of each Dockerfile
list_images() {
  docker compose -f "${COMPOSE_FILE}" config --images
  local dockerfile
  for dockerfile in $(list_dockerfiles); do
    awk 'toupper($1) == "FROM" { image = $2 } END { print image }' "${dockerfile}"
  done
}

check_images() {
  local image name sarif_dir=""
  if [ -n "${SARIF_DIR:-}" ]; then
    mkdir -p "${SARIF_DIR}"
    sarif_dir=$(cd "${SARIF_DIR}" && pwd) # absolute, for the docker mount (SARIF_DIR may be relative)
  fi
  for image in ${SCAN_IMAGES:-$(list_images | sort -u)}; do
    if [ -n "${sarif_dir}" ]; then
      name=$(echo "${image}" | tr '/:@' '___')
      log_info "scanning ${image} -> ${SARIF_DIR}/${name}.sarif"
      # the sarif is written inside the container to a writable mount of SARIF_DIR
      docker run --rm -v "${TRIVY_CACHE_DIR}:/root/.cache/trivy" -v "${sarif_dir}:/out" "${TRIVY_IMAGE}" \
        image --quiet --ignore-unfixed --severity HIGH,CRITICAL --format sarif --output "/out/${name}.sarif" "${image}" \
        || log_error "could not scan ${image}"
    else
      log_info "scanning ${image}"
      trivy image --quiet --ignore-unfixed --severity HIGH,CRITICAL "${image}" || log_error "could not scan ${image}"
    fi
  done
  # report only: image CVEs come from upstream images, so they never fail the run
  return 0
}

run_check() {
  case "$1" in
    compose) check_compose ;;
    caddy) check_caddy ;;
    hadolint) check_hadolint ;;
    trivy-config) check_trivy_config ;;
    images) check_images ;;
    *)
      log_error "unknown check: $1 (expected compose, caddy, hadolint, trivy-config or images)"
      return 2
      ;;
  esac
}

# ##############################
# MAIN
# ##############################

if [ ! -f "${COMPOSE_FILE}" ]; then
  log_error "run this script from the repo root"
  exit 2
fi

if [ "${1:-}" = "--list-images" ]; then
  list_images | sort -u
  exit 0
fi

if [ $# -eq 0 ]; then
  set -- compose caddy hadolint trivy-config
fi

# every requested check runs even if an earlier one fails, so a single run reports every problem
failed=()
for check in "$@"; do
  if ! run_check "${check}"; then
    failed+=("${check}")
  fi
done

if [ ${#failed[@]} -gt 0 ]; then
  log_error "failed checks: ${failed[*]}"
  exit 1
fi
log_info "all checks passed: $*"
