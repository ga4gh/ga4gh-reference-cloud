#!/bin/zsh

# Runs the refcloud in minikube via the helm chart in deploy/helm.
# Requires a prior ./scripts/refcloud-setup.sh run (databases, OAuth client, k8s secrets).
#
#   ./scripts/refcloud-run-helm.sh          build images, deploy, then run 'minikube tunnel'
#   ./scripts/refcloud-run-helm.sh --down   uninstall the helm release

# ##############################
# OPTIONS
# ##############################

typeset -A opts

zparseopts -D -A opts \
  -down \
  -=opts

# ##############################
# SHARED CONSTANTS & FUNCTIONS
# ##############################

source ./scripts/lib/common.sh

HELM_CHART_DIR="./deploy/helm/charts/app"
BUILD_LOG_DIR="${TMPDIR:-/tmp}/refcloud-build-logs"

# ##############################
# FUNCTIONS
# ##############################

check_port_443_free() {
  if nc -z 127.0.0.1 443 > /dev/null 2>&1; then
    local holder=$(lsof -nP -iTCP:443 -sTCP:LISTEN 2>/dev/null | awk 'NR==2 {print $1 " (pid " $2 ")"}')
    log_error "port 443 is already in use${holder:+ by $holder}. stop native mode (Caddy) or any running 'minikube tunnel' and try again"
    exit 1
  fi
}

check_k8s_secrets_exist() {
  kubectl_local get secret "${K8S_SECRETS[@]}" > /dev/null 2>&1 \
    || { log_error "kubernetes secrets are missing, run ./scripts/refcloud-setup.sh first"; exit 1; }
}

build_images() {
  local tag=$1
  local entry repository context log_file

  mkdir -p "$BUILD_LOG_DIR"

  for entry in "${REFCLOUD_IMAGES[@]}"; do
    repository=${entry%%:*}
    context=${entry#*:}
    log_file="$BUILD_LOG_DIR/$repository.log"

    log_info "building image '$repository:$tag' from '$context' into minikube"
    minikube image build -t "$repository:$tag" "$context" > "$log_file" 2>&1 \
      || { log_error "failed to build image '$repository:$tag', see '$log_file'"; exit 1; }

    # 'minikube image build' can exit 0 when the build fails, so confirm the image exists
    minikube image ls 2>/dev/null | grep -qx "docker.io/library/$repository:$tag" \
      || { log_error "image '$repository:$tag' was not built, see '$log_file'"; exit 1; }
  done
}

deploy_release() {
  local tag=$1

  log_info "deploying helm release '$HELM_RELEASE' with image tag '$tag' (runs database migrations first)"
  helm_local upgrade --install "$HELM_RELEASE" "$HELM_CHART_DIR" \
    --set image.tag="$tag" \
    --wait --timeout 10m \
  > /dev/null || { log_error "failed to deploy helm release '$HELM_RELEASE', check 'kubectl get pods' and 'kubectl get jobs'"; exit 1; }

  # rebuilt images can keep the same tag (uncommitted changes), so always restart app pods
  log_info "restarting refcloud app deployments to pick up rebuilt images"
  kubectl_local rollout restart deployment "${REFCLOUD_APP_DEPLOYMENTS[@]}" > /dev/null \
    || { log_error "failed to restart refcloud deployments"; exit 1; }

  local deployment
  for deployment in "${REFCLOUD_ALL_DEPLOYMENTS[@]}"; do
    log_info "waiting for deployment '$deployment' to be ready"
    kubectl_local rollout status deployment "$deployment" --timeout=5m > /dev/null \
      || { log_error "deployment '$deployment' did not become ready, check 'kubectl logs deploy/$deployment'"; exit 1; }
  done
}

uninstall_release() {
  if ! minikube_running; then
    log_info "minikube is not running, nothing to uninstall"
    return 0
  fi

  if helm_local status "$HELM_RELEASE" > /dev/null 2>&1; then
    log_info "uninstalling helm release '$HELM_RELEASE'"
    helm_local uninstall "$HELM_RELEASE" --wait > /dev/null || { log_error "failed to uninstall helm release '$HELM_RELEASE'"; exit 1; }
  else
    log_info "helm release '$HELM_RELEASE' is not installed"
  fi
  log_info "minikube is still running, stop it with 'minikube stop' if you no longer need it"
}

# ##############################
# MAIN SCRIPT
# ##############################

kubernetes_dependencies_installed || { log_error "minikube, kubectl, helm and mkcert are required for helm mode"; exit 1; }

if (( ${+opts[--down]} )); then
  uninstall_release
  exit 0
fi

log_info "starting refcloud in helm mode (minikube)"

check_port_443_free
start_minikube
use_minikube_context
check_k8s_secrets_exist

IMAGE_TAG=$(refcloud_image_tag)
log_info "using lockstep image tag '$IMAGE_TAG'"

build_images "$IMAGE_TAG"
deploy_release "$IMAGE_TAG"

print_service_urls
log_info "starting 'minikube tunnel' to expose the ingress on port 443 (may prompt for your password)"
log_info "press Ctrl+C to stop the tunnel. the release keeps running; use --down to uninstall it"

minikube tunnel

log_info "tunnel stopped. the refcloud is still running in minikube; run this script again to reconnect"
