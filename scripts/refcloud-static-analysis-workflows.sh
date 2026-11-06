#!/usr/bin/env bash

# Static analysis of the GitHub Actions setup in .github/ (workflows and dependabot.yml).
# Every tool runs from a pinned docker image (version + digest), so the only requirement is Docker, and
# local runs match CI (.github/workflows/static-analysis-workflows.yml), which calls this script.
#
#   ./scripts/refcloud-static-analysis-workflows.sh                run all checks
#   ./scripts/refcloud-static-analysis-workflows.sh <check>...     run specific checks:
#       actionlint    workflow syntax, expressions, and shellcheck of every `run:` script
#       zizmor        workflow security: unpinned actions, template injection, credential persistence,
#                     secrets handling, dependabot settings; config: .github/zizmor.yml
#
# zizmor's online audits (known-vulnerable actions, impostor commits) need a GitHub token: set GH_TOKEN to run
# them (CI passes its read-only token). Without one, zizmor runs offline. Set ZIZMOR_FORMAT=github in CI for
# inline annotations.
#
# Written for bash 3.2 (macOS) as well as CI, so it avoids zsh and bash 4 features. Run from the repo root.

set -uo pipefail

# ##############################
# PINNED TOOL IMAGES (bump version and digest together)
# ##############################

ACTIONLINT_IMAGE="rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667"
ZIZMOR_IMAGE="ghcr.io/zizmorcore/zizmor:1.32.0@sha256:3014cc8f68f663935f69b3c3e0e41f5ce102b268d97db13db0f62208747f860d"

# ##############################
# FUNCTIONS
# ##############################

log_info() {
  echo "[INFO] $1"
}

log_error() {
  echo "[ERROR] $1" >&2
}

check_actionlint() {
  log_info "linting workflows (actionlint)"
  # actionlint finds .github/workflows from the repository root, so the whole repo is mounted
  docker run --rm -v "${PWD}:/repo:ro" -w /repo "${ACTIONLINT_IMAGE}" -color=false
}

check_zizmor() {
  local mode=(--offline)
  if [ -n "${GH_TOKEN:-}" ]; then
    mode=()
    log_info "auditing .github (zizmor, online audits enabled)"
  else
    log_info "auditing .github (zizmor, offline: set GH_TOKEN to enable online audits)"
  fi
  # GH_TOKEN is passed by name only, so its value never appears in the command line
  docker run --rm -e GH_TOKEN -v "${PWD}:/repo:ro" -w /repo "${ZIZMOR_IMAGE}" \
    "${mode[@]+"${mode[@]}"}" --config .github/zizmor.yml --format "${ZIZMOR_FORMAT:-plain}" .github
}

run_check() {
  case "$1" in
    actionlint) check_actionlint ;;
    zizmor) check_zizmor ;;
    *)
      log_error "unknown check: $1 (expected actionlint or zizmor)"
      return 2
      ;;
  esac
}

# ##############################
# MAIN
# ##############################

if [ ! -d .github/workflows ]; then
  log_error "run this script from the repo root"
  exit 2
fi

if [ $# -eq 0 ]; then
  set -- actionlint zizmor
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
