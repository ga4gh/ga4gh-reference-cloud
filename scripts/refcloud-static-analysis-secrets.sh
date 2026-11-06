#!/usr/bin/env bash

# Secret scanning with gitleaks. Runs from a docker image pinned by version and digest, so the only requirement
# is Docker, and local runs match CI (.github/workflows/static-analysis-secrets.yml), which calls this script.
# Complements GitHub push protection (repo setting), which blocks known provider token formats at push time.
#
#   ./scripts/refcloud-static-analysis-secrets.sh            scan the full git history (what CI runs)
#   ./scripts/refcloud-static-analysis-secrets.sh history    same as above
#   ./scripts/refcloud-static-analysis-secrets.sh staged     scan only staged changes: run before committing
#
# Config: .gitleaks.toml (default rules plus narrow allowlists) and .gitleaksignore (pinned historical findings).
# Findings are printed redacted, so secret values never appear in logs.
#
# Written for bash 3.2 (macOS) as well as CI, so it avoids zsh and bash 4 features. Run from the repo root.

set -uo pipefail

# ##############################
# PINNED TOOL IMAGE (bump version and digest together)
# ##############################

GITLEAKS_IMAGE="ghcr.io/gitleaks/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f"

# ##############################
# FUNCTIONS
# ##############################

log_info() {
  echo "[INFO] $1"
}

log_error() {
  echo "[ERROR] $1" >&2
}

# gitleaks over the repo (mounted read-only). the container user differs from the checkout's owner, so git's
# safe.directory check is satisfied through environment config instead of a global git config change
gitleaks() {
  docker run --rm -v "${PWD}:/repo:ro" -w /repo \
    -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0=/repo \
    "${GITLEAKS_IMAGE}" "$@" \
    --config .gitleaks.toml --gitleaks-ignore-path .gitleaksignore --redact --no-banner --verbose
}

check_history() {
  log_info "scanning the full git history for secrets (gitleaks)"
  # the whole history, not just new commits: a secret committed and then removed is still public
  gitleaks git .
}

check_staged() {
  log_info "scanning staged changes for secrets (gitleaks)"
  gitleaks git --staged .
}

run_check() {
  case "$1" in
    history) check_history ;;
    staged) check_staged ;;
    *)
      log_error "unknown check: $1 (expected history or staged)"
      return 2
      ;;
  esac
}

# ##############################
# MAIN
# ##############################

if [ ! -f .gitleaks.toml ]; then
  log_error "run this script from the repo root"
  exit 2
fi

if [ $# -eq 0 ]; then
  set -- history
fi

failed=()
for check in "$@"; do
  if ! run_check "${check}"; then
    failed+=("${check}")
  fi
done

if [ ${#failed[@]} -gt 0 ]; then
  log_error "failed checks: ${failed[*]} (if a finding is a false positive, allowlist it with a reason in .gitleaks.toml)"
  exit 1
fi
log_info "no secrets found: $*"
