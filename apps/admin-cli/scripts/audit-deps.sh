#!/usr/bin/env bash

# Dependency CVE audit of uv.lock (runtime and dev dependencies) with Trivy. Fails on HIGH and CRITICAL
# vulnerabilities, whether or not a fix is available, like `npm audit --audit-level=high` in the Node apps.
# Trivy runs from a docker image pinned by version and digest, so the only requirement is Docker, and local
# runs match CI (.github/workflows/static-analysis-admin-cli.yml), which calls this script.
#
#   ./scripts/audit-deps.sh      run from apps/admin-cli
#
# To fix a finding, upgrade the package in the lock: `uv lock --upgrade-package <name>`. A vulnerability that
# can't be fixed yet can be listed in .trivyignore (apps/admin-cli) with a reason; remove it once fixed.
#
# Written for bash 3.2 (macOS) as well as CI, so it avoids zsh and bash 4 features.

set -euo pipefail

# ##############################
# PINNED TOOL IMAGE (bump version and digest together; also pinned in scripts/refcloud-static-analysis-deploy-local.sh)
# ##############################

TRIVY_IMAGE="aquasec/trivy:0.76.2@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb"

# trivy's vulnerability db, reused between runs (outside the repo)
TRIVY_CACHE_DIR="${TRIVY_CACHE_DIR:-${HOME}/.cache/trivy}"
# trivy runs as the calling user (not root) with the cache mounted at /cache, so cached files stay owned by the
# user and CI can save and restore them between runs (actions/cache)

# ##############################
# MAIN
# ##############################

if [ ! -f uv.lock ] || [ ! -f .trivyignore ]; then
  echo "[ERROR] run this script from apps/admin-cli" >&2
  exit 2
fi

echo "[INFO] auditing uv.lock dependencies for HIGH/CRITICAL vulnerabilities (trivy fs)"
mkdir -p "${TRIVY_CACHE_DIR}"
# only uv.lock and .trivyignore are mounted (read-only), so nothing is written to the repo
docker run --rm \
  -v "${PWD}/uv.lock:/src/uv.lock:ro" -v "${PWD}/.trivyignore:/src/.trivyignore:ro" \
  --user "$(id -u):$(id -g)" -e HOME=/tmp -e TRIVY_CACHE_DIR=/cache -v "${TRIVY_CACHE_DIR}:/cache" \
  "${TRIVY_IMAGE}" fs --quiet --scanners vuln --include-dev-deps --exit-code 1 --severity HIGH,CRITICAL \
  --ignorefile /src/.trivyignore /src
echo "[INFO] no HIGH/CRITICAL vulnerabilities found"
