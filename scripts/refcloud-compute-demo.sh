#!/bin/zsh

# Runs the compute demo through WES, as a researcher would: submits the bundled drs-checksum workflow on the demo's
# 1000 Genomes DRS object, follows the run to its end, downloads its report through the output download endpoint, and
# checks the report's MD5 against the checksum DRS reports for the object.
#
#   ./scripts/refcloud-compute-demo.sh [-p <platform id>] <passport token>
#
# The passport token comes from the researcher UI's "View Passport Token" page (or REFCLOUD_PASSPORT_TOKEN). The
# researcher needs approved visas for the compute platform and the 1000 Genomes dataset. Either local mode works:
#   * native mode (./scripts/refcloud-run-native.sh): nextflow-demo, which setup registers when Nextflow is installed
#     (deploy/local/README.md);
#   * helm mode (./scripts/refcloud-run-helm.sh): nextflow-demo-kubernetes, which the run script registers
#     (deploy/helm/README.md).
# Without -p, it uses whichever of the two is healthy (both modes share the database, so both platforms exist, but only
# the running mode's is healthy). Needs only curl and node. REFCLOUD_DEMO_BASE_URL points it at another deployment
# (default https://refcloud.ga4gh.localhost); pass -p there.

source ./scripts/lib/common.sh

typeset -A opts
zparseopts -D -A opts p:
TOKEN=${1:-$REFCLOUD_PASSPORT_TOKEN}

if [[ -z "$TOKEN" ]]; then
  log_error "usage: ./scripts/refcloud-compute-demo.sh [-p <platform id>] <passport token> (or set REFCLOUD_PASSPORT_TOKEN)"
  exit 2
fi

BASE=${REFCLOUD_DEMO_BASE_URL:-"https://$REFCLOUD_BASE_URL"}
DEMO_OBJECT_ID=${DEMO_DRS_URI##*/}
POLL_SECONDS=5
TIMEOUT_SECONDS=900

# the local modes serve https://refcloud.ga4gh.localhost with local CAs: Caddy's (native mode) and mkcert's (helm
# mode). curl takes one CA file and then trusts only it, so for a .localhost URL both are combined into a temporary
# bundle; any other deployment uses the system's trust store. REFCLOUD_CA_CERT names a CA file to use instead
CURL_OPTS=(-sS)
CA_FILES=()
if [[ -n "$REFCLOUD_CA_CERT" ]]; then
  CA_FILES=("$REFCLOUD_CA_CERT")
elif [[ "${${BASE#*://}%%[:/]*}" == *.localhost ]]; then
  caddy_ca="$HOME/Library/Application Support/Caddy/pki/authorities/local/root.crt"
  [[ -f "$caddy_ca" ]] && CA_FILES+=("$caddy_ca")
  if command -v mkcert > /dev/null 2>&1 && [[ -f "$(mkcert -CAROOT)/rootCA.pem" ]]; then
    CA_FILES+=("$(mkcert -CAROOT)/rootCA.pem")
  fi
fi
if (( ${#CA_FILES} == 1 )); then
  CURL_OPTS+=(--cacert "$CA_FILES[1]")
elif (( ${#CA_FILES} > 1 )); then
  CA_BUNDLE=$(mktemp "${TMPDIR:-/tmp}/refcloud-demo-ca.XXXXXX")
  trap 'rm -f "$CA_BUNDLE"' EXIT
  cat "${CA_FILES[@]}" > "$CA_BUNDLE"
  CURL_OPTS+=(--cacert "$CA_BUNDLE")
fi

# a field of the JSON on stdin, by dotted path (array elements by index, e.g. outputs.files.0.url); objects and arrays
# are printed as JSON, a missing field (or a body that isn't JSON, e.g. an empty 401) as nothing
json() {
  node -e '
    let value;
    try { value = JSON.parse(require("fs").readFileSync(0, "utf8")); } catch { value = undefined; }
    for (const key of process.argv[1].split(".")) value = value == null ? undefined : value[key];
    process.stdout.write(value == null ? "" : typeof value === "object" ? JSON.stringify(value) : String(value));
  ' "$1"
}

# calls the API with the passport token; prints the body, and the HTTP status on the last line. it runs inside $(...),
# so a failure is reported on stderr and callers stop with "|| exit 1"
api() {
  curl "${CURL_OPTS[@]}" -w '\n%{http_code}' -H "Authorization: Bearer $TOKEN" "$@" \
    || { log_error "could not reach $BASE: $NOT_RUNNING_HINT" >&2; return 1; }
}

body_of() { print -r -- "${1%$'\n'*}"; }
status_of() { print -r -- "${1##*$'\n'}"; }

NOT_RUNNING_HINT="is the refcloud running (./scripts/refcloud-run-native.sh or ./scripts/refcloud-run-helm.sh)?"

# a platform's health from its (public) WES service-info, or nothing if it doesn't answer
platform_health() {
  curl "${CURL_OPTS[@]}" "$BASE/ga4gh/wes/$1/v1/service-info" 2> /dev/null | json health.status
}

# without -p: the running mode's demo platform, the one that's healthy
if [[ -n "${opts[-p]}" ]]; then
  PLATFORM_ID=${opts[-p]}
else
  PLATFORM_ID=$DEFAULT_COMPUTE_PLATFORM_ID
  for candidate in "$DEFAULT_COMPUTE_PLATFORM_ID" "$KUBERNETES_COMPUTE_PLATFORM_ID"; do
    if [[ "$(platform_health "$candidate")" == "healthy" ]]; then
      PLATFORM_ID=$candidate
      break
    fi
  done
fi
WES="$BASE/ga4gh/wes/$PLATFORM_ID/v1"

log_info "compute demo on platform '$PLATFORM_ID' ($WES)"

# 1. the platform is up (service-info is public and reports its health)
response=$(curl "${CURL_OPTS[@]}" -w '\n%{http_code}' "$WES/service-info") \
  || { log_error "could not reach $BASE: $NOT_RUNNING_HINT"; exit 1; }
if [[ "$(status_of "$response")" != 200 ]]; then
  log_error "no compute platform '$PLATFORM_ID' ($(status_of "$response")): pass -p <platform id>. locally, native mode's setup registers '$DEFAULT_COMPUTE_PLATFORM_ID' when Nextflow is installed, and helm mode's run script registers '$KUBERNETES_COMPUTE_PLATFORM_ID'"
  exit 1
fi
health=$(body_of "$response" | json health.status)
log_info "platform health: $health"
if [[ "$health" != "healthy" ]]; then
  log_error "the platform is unhealthy: core-api's log says why. native mode: is Nextflow installed and Funnel running (deploy/local/README.md)? helm mode: kubectl logs deploy/refcloud-api (deploy/helm/README.md)"
  exit 1
fi

# 2. submit the bundled drs-checksum workflow with the demo's DRS object as its input
log_info "submitting refcloud:bundled/drs-checksum on $DEMO_DRS_URI"
response=$(api -X POST "$WES/runs" \
  -F workflow_url=refcloud:bundled/drs-checksum \
  -F workflow_type=NFL \
  -F workflow_type_version=DSL2 \
  -F "workflow_params={\"input\": \"$DEMO_DRS_URI\"}" \
  -F 'tags={"purpose": "refcloud compute demo"}') || exit 1
if [[ "$(status_of "$response")" != 200 ]]; then
  log_error "the run was refused ($(status_of "$response")): $(body_of "$response" | json msg)"
  case "$(status_of "$response")" in
    401) log_error "the passport token is invalid or expired: copy a new one from the researcher UI's View Passport Token page" ;;
    403) log_error "a visa isn't approved yet: you need ga4gh_visa:compute_platform:$PLATFORM_ID and $DEMO_DATASET_VISA" ;;
  esac
  exit 1
fi
run_id=$(body_of "$response" | json run_id)
log_info "run $run_id submitted"

# 3. follow the run: QUEUED while its input downloads, then INITIALIZING, RUNNING and a final state
state=""
for (( waited = 0; waited <= TIMEOUT_SECONDS; waited += POLL_SECONDS )); do
  response=$(api "$WES/runs/$run_id/status") || exit 1
  current=$(body_of "$response" | json state)
  if [[ "$current" != "$state" ]]; then
    state=$current
    log_info "  ${waited}s: $state"
  fi
  case "$state" in
    COMPLETE|EXECUTOR_ERROR|SYSTEM_ERROR|CANCELED|PREEMPTED) break ;;
  esac
  sleep $POLL_SECONDS
done

response=$(api "$WES/runs/$run_id") || exit 1
run_log=$(body_of "$response")
if [[ "$state" != "COMPLETE" ]]; then
  log_error "the run ended $state (or didn't finish within ${TIMEOUT_SECONDS}s): $(print -r -- "$run_log" | json run_log.system_logs)"
  log_error "engine output: $WES/runs/$run_id/logs/stdout and .../logs/stderr; run log: $WES/runs/$run_id"
  exit 1
fi
response=$(api "$WES/runs/$run_id/tasks") || exit 1
tasks=$(body_of "$response" | json task_logs.length)
log_info "the run completed with $tasks TES task(s) (see $WES/runs/$run_id/tasks)"

# 4. download the report through the output download endpoint (its URL is in the run log's outputs)
report_url=$(print -r -- "$run_log" | json outputs.files.0.url)
log_info "downloading $report_url"
response=$(api "$report_url") || exit 1
if [[ "$(status_of "$response")" != 200 ]]; then
  log_error "could not download the report ($(status_of "$response"))"
  exit 1
fi
report=$(body_of "$response")
print -r -- "$report"

# 5. the report's MD5 against the checksum DRS reports for the object (for this file, S3's ETag is its MD5)
md5=$(print -r -- "$report" | json md5)
response=$(api "$BASE/ga4gh/drs/v1/objects/$DEMO_OBJECT_ID") || exit 1
drs_checksum=$(body_of "$response" | json checksums.0.checksum)
if [[ -n "$md5" && "$md5" == "$drs_checksum" ]]; then
  log_info "the report's MD5 matches the DRS object's checksum ($drs_checksum)"
else
  log_warn "the report's MD5 ($md5) doesn't match the DRS object's checksum (${drs_checksum:-none})"
  exit 1
fi
log_info "compute demo complete: run $run_id"
