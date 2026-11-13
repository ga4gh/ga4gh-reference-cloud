#!/bin/zsh

# Runs the compute demo through WES, as a researcher would: submits the bundled drs-checksum workflow on the demo's
# 1000 Genomes DRS object, follows the run to its end, downloads its report through the output download endpoint, and
# checks the report's MD5 against the checksum DRS reports for the object.
#
#   ./scripts/refcloud-compute-demo.sh [-p <platform id>] <passport token>
#
# The passport token comes from the researcher UI's "View Passport Token" page (or REFCLOUD_PASSPORT_TOKEN). The
# researcher needs approved visas for the compute platform and the 1000 Genomes dataset. Native mode must be running
# (./scripts/refcloud-run-native.sh), with compute set up (see deploy/local/README.md). Needs only curl and node.
# REFCLOUD_DEMO_BASE_URL points it at another deployment (default https://refcloud.ga4gh.localhost).

source ./scripts/lib/common.sh

typeset -A opts
zparseopts -D -A opts p:
PLATFORM_ID=${opts[-p]:-$DEFAULT_COMPUTE_PLATFORM_ID}
TOKEN=${1:-$REFCLOUD_PASSPORT_TOKEN}

if [[ -z "$TOKEN" ]]; then
  log_error "usage: ./scripts/refcloud-compute-demo.sh [-p <platform id>] <passport token> (or set REFCLOUD_PASSPORT_TOKEN)"
  exit 2
fi

BASE=${REFCLOUD_DEMO_BASE_URL:-"https://$REFCLOUD_BASE_URL"}
WES="$BASE/ga4gh/wes/$PLATFORM_ID/v1"
DEMO_OBJECT_ID=${DEMO_DRS_URI##*/}
POLL_SECONDS=5
TIMEOUT_SECONDS=900

# Caddy serves https://refcloud.ga4gh.localhost with its own local CA: verify against it when it's in the default
# place (or REFCLOUD_CA_CERT), otherwise rely on the system's trust store
CA_CERT=${REFCLOUD_CA_CERT:-"$HOME/Library/Application Support/Caddy/pki/authorities/local/root.crt"}
CURL_OPTS=(-sS)
if [[ -f "$CA_CERT" ]]; then
  CURL_OPTS+=(--cacert "$CA_CERT")
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
    || { log_error "could not reach $BASE: is native mode running (./scripts/refcloud-run-native.sh)?" >&2; return 1; }
}

body_of() { print -r -- "${1%$'\n'*}"; }
status_of() { print -r -- "${1##*$'\n'}"; }

log_info "compute demo on platform '$PLATFORM_ID' ($WES)"

# 1. the platform is up (service-info is public and reports its health)
response=$(curl "${CURL_OPTS[@]}" -w '\n%{http_code}' "$WES/service-info") \
  || { log_error "could not reach $BASE: is native mode running (./scripts/refcloud-run-native.sh)?"; exit 1; }
if [[ "$(status_of "$response")" != 200 ]]; then
  log_error "no compute platform '$PLATFORM_ID' ($(status_of "$response")): run setup with Nextflow installed, or pass -p <platform id>"
  exit 1
fi
health=$(body_of "$response" | json health.status)
log_info "platform health: $health"
if [[ "$health" != "healthy" ]]; then
  log_error "the platform is unhealthy: is Nextflow installed and Funnel running? (see deploy/local/README.md)"
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
