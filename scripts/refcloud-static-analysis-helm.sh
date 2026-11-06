#!/usr/bin/env bash

# Static analysis for the helm chart (deploy/helm/charts/app), rendered with its default (local) values.
# Every tool runs from a pinned docker image (version + digest), so the only requirement is Docker, and
# local runs match CI (.github/workflows/static-analysis-helm.yml), which calls this script.
#
#   ./scripts/refcloud-static-analysis-helm.sh                run all checks
#   ./scripts/refcloud-static-analysis-helm.sh <check>...     run specific checks:
#       lint          helm lint --strict (chart structure, template errors)
#       kubeconform   render the chart (helm template) and validate every resource against the kubernetes
#                     API schemas, including the helm hook Jobs
#       trivy-config  scan for security misconfigurations (HIGH/CRITICAL), both the chart and its rendered
#                     manifests (trivy's chart mode skips helm hooks, so the migration Jobs are only in the
#                     rendered scan); ignores: deploy/helm/.trivyignore.yaml
#
# Written for bash 3.2 (macOS) as well as CI, so it avoids zsh and bash 4 features. Run from the repo root.

set -uo pipefail

# ##############################
# PINNED TOOL IMAGES (bump version and digest together; TRIVY_IMAGE is also pinned in
# scripts/refcloud-static-analysis-deploy-local.sh and apps/{admin-cli,core-api}/scripts/audit-deps.sh)
# ##############################

HELM_IMAGE="alpine/helm:4.3.1@sha256:e3f8d5c462d75470fbb1fce79ad5c20063657096d1f98c0fcea869e2600e2e5e"
KUBECONFORM_IMAGE="ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43f95aa6425306e1ab8d6fcad72acb9049158f38e574c085ea1ec0f64e"
TRIVY_IMAGE="aquasec/trivy:0.76.2@sha256:8f6bb947dbc528902e11f2f5be52f7b519f6a39d5d148f41166f2040499768fb"

HELM_DIR="deploy/helm"
CHART_DIR="${HELM_DIR}/charts/app"
# image.tag is required by the chart; any value renders the same manifests
IMAGE_TAG="static-analysis"
# kubernetes version whose API schemas kubeconform validates against (minikube's default)
KUBERNETES_VERSION="1.35.1"
# trivy's vulnerability db and checks bundle, reused between runs (outside the repo)
TRIVY_CACHE_DIR="${TRIVY_CACHE_DIR:-${HOME}/.cache/trivy}"
# trivy runs as the calling user (not root) with the cache mounted at /cache, so cached files stay owned by the
# user and CI can save and restore them between runs (actions/cache)

# ##############################
# FUNCTIONS
# ##############################

log_info() {
  echo "[INFO] $1"
}

log_error() {
  echo "[ERROR] $1" >&2
}

# helm, with deploy/helm mounted read-only
helm() {
  docker run --rm -v "${PWD}/${HELM_DIR}:/helm:ro" -w /helm/charts/app "${HELM_IMAGE}" "$@"
}

# trivy, with deploy/helm mounted read-only and a persistent cache. reads stdin when given `-` paths
trivy() {
  mkdir -p "${TRIVY_CACHE_DIR}"
  docker run --rm -i -v "${PWD}/${HELM_DIR}:/helm:ro" --user "$(id -u):$(id -g)" -e HOME=/tmp -e TRIVY_CACHE_DIR=/cache -v "${TRIVY_CACHE_DIR}:/cache" \
    -w /helm/charts/app "${TRIVY_IMAGE}" "$@"
}

render() {
  helm template refcloud . --set image.tag="${IMAGE_TAG}"
}

check_lint() {
  log_info "linting ${CHART_DIR} (helm lint --strict)"
  helm lint . --strict --set image.tag="${IMAGE_TAG}"
}

check_kubeconform() {
  log_info "validating rendered manifests against kubernetes ${KUBERNETES_VERSION} schemas (kubeconform)"
  local rendered
  rendered=$(render) || { log_error "helm template failed"; return 1; }
  printf '%s\n' "${rendered}" | docker run --rm -i "${KUBECONFORM_IMAGE}" \
    -strict -summary -kubernetes-version "${KUBERNETES_VERSION}" -
}

check_trivy_config() {
  local failed=0 rendered
  log_info "scanning ${CHART_DIR} for security misconfigurations (trivy config, HIGH/CRITICAL)"
  trivy config --quiet --exit-code 1 --severity HIGH,CRITICAL --helm-set image.tag="${IMAGE_TAG}" \
    --ignorefile /helm/.trivyignore.yaml . || failed=1

  # the rendered manifests are one file, so path-scoped ignores can't apply: this scan covers only the helm
  # hook resources (the migration Jobs), which the chart scan above skips
  log_info "scanning the rendered helm hook Jobs for security misconfigurations (trivy config, HIGH/CRITICAL)"
  rendered=$(render) || { log_error "helm template failed"; return 1; }
  printf '%s\n' "${rendered}" | hook_jobs | docker run --rm -i --user "$(id -u):$(id -g)" -e HOME=/tmp -e TRIVY_CACHE_DIR=/cache -v "${TRIVY_CACHE_DIR}:/cache" \
    --entrypoint sh "${TRIVY_IMAGE}" -c \
    'mkdir -p /tmp/hooks && cat > /tmp/hooks/jobs.yaml && trivy config --quiet --exit-code 1 --severity HIGH,CRITICAL /tmp/hooks' \
    || failed=1
  return "${failed}"
}

# prints only the rendered documents that are helm hooks
hook_jobs() {
  # documents start at `---` lines (helm template emits one before each); portable across awk implementations
  awk '
    /^---/ { if (doc ~ /helm\.sh\/hook/) printf "%s", doc; doc = $0 "\n"; next }
    { doc = doc $0 "\n" }
    END { if (doc ~ /helm\.sh\/hook/) printf "%s", doc }
  '
}

run_check() {
  case "$1" in
    lint) check_lint ;;
    kubeconform) check_kubeconform ;;
    trivy-config) check_trivy_config ;;
    *)
      log_error "unknown check: $1 (expected lint, kubeconform or trivy-config)"
      return 2
      ;;
  esac
}

# ##############################
# MAIN
# ##############################

if [ ! -f "${CHART_DIR}/Chart.yaml" ]; then
  log_error "run this script from the repo root"
  exit 2
fi

if [ $# -eq 0 ]; then
  set -- lint kubeconform trivy-config
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
