# Feature Spec: Run the Reference Cloud locally via Helm

**Branch:** `ja-iss-38-helm-setup-and-run-scripts`

**Linked GitHub Issue:** [Issue 38](https://github.com/ga4gh/ga4gh-reference-cloud/issues/38)

## Objective

Make it possible to run the whole refcloud in a local minikube cluster using the Helm chart in `deploy/helm`, as an alternative to native mode (`scripts/refcloud-run-native.sh`). Both modes share the same host Postgres databases and OAuth clients, so one-time setup (`scripts/refcloud-setup.sh`) is done once and works for either mode.

## Context

* Native mode: apps run on the host, Kratos/Hydra run in docker compose (`deploy/local`), Caddy is the ingress, and Postgres runs on the host.
* Helm mode: apps, Kratos and Hydra run in minikube; ingress-nginx is the ingress (exposed via `minikube tunnel`); Postgres is **still the host's** Postgres.
* Base URLs are the same in both modes: `https://refcloud.ga4gh.localhost` and `https://docs.refcloud.ga4gh.localhost`. `.localhost` names resolve to loopback without `/etc/hosts` entries.
* Only one mode runs at a time: Caddy and `minikube tunnel` both bind port 443.

### Current gaps in the chart (found while scoping)

* `templates/common/ingress.yaml` routes only `/self-service/`, `/oauth2/`, `/.well-known/`, `/ga4gh/drs/v1/`. It is missing `/sessions/`, `/schemas/`, `/webhook/kratos/`, `/ga4gh/refget/v2/`, `/ga4gh/data-connect/v1/` (all present in `deploy/local/Caddyfile`). The ingress TLS block also only lists the base host, not `docs.<base>`.
* Image references are hardcoded to private ECR with per-app, stale tags (e.g. `refcloud-api:v0.3.2-99-g5f94342`, `refcloud-ui:v0.1.0-18-g7a32e0f`), which conflicts with lockstep versioning.
* The chart expects ~8 pre-existing k8s secrets (`common-db-info`, `kratos-db-credentials`, `hydra-db-credentials`, `refcloud-api-db-credentials`, `kratos-secrets`, `hydra-secrets`, `refcloud-ui-hydra-researcher-client`, `refcloud-api-kratos-webhook`) that are currently created by hand.

## Engineering Tasks

### Task 1: Parameterize images in the Helm chart

* Remove every hardcoded image registry/tag from `deploy/helm/charts/app/templates`.
* Add values so the same chart can target a local build or a remote registry, e.g.:
  * `image.registry` — empty for local minikube builds; the ECR registry host for AWS
  * `image.tag` — a single lockstep tag shared by all refcloud app images
  * `image.pullPolicy` — `Never`/`IfNotPresent` locally, `IfNotPresent`/`Always` in AWS
  * `image.pullSecrets` — empty locally (template omits `imagePullSecrets` when empty); `ecr-registry-key` in AWS
  * per-app repository names (`refcloud-api`, `refcloud-api-liquibase`, `refcloud-ui`, `refcloud-docs`), keeping the legacy ECR names
* Third-party images (Kratos, Hydra) keep their public image references but their versions move into values too.
* `values.yaml` defaults to the local deployment; AWS settings are supplied as overrides at deploy time.

### Task 2: Ingress parity with the Caddyfile

* Add the missing paths listed under **Current gaps**, routed to the same backends as the Caddyfile (Kratos public 4433, Hydra public 4444, core-api 8080).
* Add `docs.<baseURL>` to the local TLS hosts.
* The Hydra admin API (4445) must **not** be exposed through the ingress.
* Add a note to `deploy/helm` and `deploy/local` CLAUDE.md files: routes must be kept in sync between the Caddyfile and the ingress.

### Task 3: Add admin-cli to the helm chart

* Add an "admin-cli" deployment to the helm chart so that admin commands can be run from within the cluster

### Task 4: Connect in-cluster services to host Postgres and align config with native mode

* Point `common-db-info` at the host: `PSQL_HOST=host.docker.internal` (or `host.minikube.internal`) — verify which name resolves from a pod and that host Postgres accepts the connection without `pg_hba.conf`/`listen_addresses` changes (docker compose already reaches host Postgres via `host.docker.internal`, so this is expected to work with minikube's docker driver).
* Kratos and Hydra in the cluster share databases with the compose instances, so their **secrets must match** `deploy/local/ory`:
  * Hydra `secrets.system` — Hydra encrypts stored client secrets with it; a mismatch makes the OAuth client created by setup unusable.
  * Kratos `secrets.cookie` / `secrets.cipher`.
* Check chart Kratos/Hydra config against `deploy/local/ory` (URLs, webhook target, login/consent URLs) and reconcile. In-cluster, the Kratos webhook should target the core-api service directly, not the public hostname.
* Check the UI and core-api deployment env against `apps/researcher-ui/.env.development` and native core-api config (e.g. `HYDRA_ADMIN_API_BASE_URL` → `http://hydra:4445`, any AWS/S3 settings core-api needs).
* The chart's migration jobs (Liquibase, Kratos, Hydra) still run on install; they are idempotent against the already-migrated host DBs.

### Task 5: Extract shared script helpers

* Move duplicated helpers (`log_info`, `log_error`, docker compose start/stop, DB and Hydra functions) into `scripts/lib/common.sh`, sourced by all scripts.
* No behaviour change to the native scripts, except: make `./scripts/refcloud-setup.sh` safe to re-run without `--reset`, so contributors can re-run it (e.g. to get the Kubernetes setup) without breaking existing data:
  * `init_db` / `create_data_connect_reader_role` skip databases and roles that already exist instead of erroring.
  * Dataset onboarding (`refcloud seed`, `dataset init`, `dataset onboard`) is skipped for datasets that are already onboarded, since onboarding is not idempotent and re-running hits duplicate keys.

### Task 6: Extend the setup script with Kubernetes setup

* Keep a single entry point: `./scripts/refcloud-setup.sh [--reset]`. DB, Liquibase, Hydra client and dataset onboarding steps are unchanged and shared by both modes.
* Add a Kubernetes section that:
  * checks for `minikube`, `kubectl`, `helm`, `mkcert`
  * starts minikube (docker driver) if not running and enables the `ingress` addon
  * generates a TLS cert with mkcert for `refcloud.ga4gh.localhost` and `*.refcloud.ga4gh.localhost` into a git-ignored directory and creates the `refcloud-tls` secret
  * creates/updates all secrets the chart needs (idempotent, e.g. `kubectl create secret ... --dry-run=client -o yaml | kubectl apply -f -`), using the same values as native mode
* `--reset` also deletes the refcloud k8s secrets (and the Helm release, if installed) before recreating them.

### Task 7: Create `scripts/refcloud-run-helm.sh`

* Fails fast if Caddy/native mode is holding port 443.
* Computes the lockstep tag: `git describe --tags --match 'v[0-9]*' --always`, with `-uncommitted` appended when `git status --porcelain` shows any changes (including untracked files, which `git describe --dirty` misses).
* Builds all app images into minikube (`minikube image build`) with that tag.
* `helm upgrade --install` with the tag, then `kubectl rollout restart` the refcloud deployments so rebuilt images with an unchanged tag (uncommitted changes) are picked up. The chart is unchanged; in AWS every deploy has a new tag.
* Migration Jobs (Liquibase, Kratos, Hydra) become Helm pre-install/pre-upgrade hooks, since a Job's pod template is immutable and the tag changes between runs.
* Waits for rollouts, prints the same service URL list as the native script, then runs `minikube tunnel` in the foreground (prompts for sudo to bind 443).
* Ctrl+C stops the tunnel only; the release keeps running so the next run is fast.
* `--down` uninstalls the Helm release (and optionally stops minikube).

### Task 8: Documentation

* Update root `README.md`, `deploy/helm/README.md`, root `CLAUDE.md` and add `deploy/helm/CLAUDE.md`: prerequisites, setup, run, teardown, and the "one mode at a time" rule.
* Remove the manual ECR secret and manual TLS steps from `deploy/helm/README.md` (now handled by setup), keeping ECR notes only for AWS deployments.

## Open Questions

Defaults in **bold** will be used if not answered.

1. Should the setup script's Kubernetes section always run, or skip with a warning when `minikube`/`kubectl` are not installed (so native-only contributors aren't forced to install them)? **Skip with a warning.**
2. Where should mkcert output live? **`deploy/helm/.certs/` (git-ignored).**
3. Should `--down` also stop minikube? **No — uninstall the release only; `minikube stop` is left to the user.**
4. Root `CLAUDE.md` lists the docs URL as `https://docs.refcloud.ga4gh.org`; should that be `https://docs.refcloud.ga4gh.localhost` for local deployments? **Yes.**
