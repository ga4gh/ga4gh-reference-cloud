# Project Guide: GA4GH Reference Cloud Local Deployment

## Project Overview & Architecture

This repo serves to provide configuration for the local deployment context 

* **Tech Stack:** Caddy, Docker, Docker Compose, Ory Kratos & Hydra, Funnel (TES), GA4GH Refcloud

### Directory Layout

* `Caddyfile` - Caddy-based ingress controller
* `docker-compose.yml` - compose file for spinning up dependency services (Ory Kratos, Ory Hydra, Funnel)
* `ory/hydra` - config files for deployment of Ory Hydra 
* `ory/kratos` - config files for deployment of Ory Kratos
* `compute/connections.yml` - compute connections for native mode, read by both core-api and the admin CLI (`REFCLOUD_COMPUTE_CONNECTIONS_FILE`). Only connections go here: no secrets (name the environment variable instead), no per-property env overrides, and relative paths resolve from this file's directory
* `funnel/` - the Funnel (TES executor) image (`Dockerfile`, local development only) and its config (`funnel.yml`)
* `.trivyignore` - Trivy exceptions for this directory's Dockerfiles only; the app Dockerfiles use the repo-wide `/.trivyignore`

---

## Core Commands

### Development & Build

* Start dependency services (from the repo root; the scripts set `REFCLOUD_COMPUTE_STORAGE_ROOT` themselves): `REFCLOUD_COMPUTE_STORAGE_ROOT=$PWD/compute/nextflow-funnel-docker docker compose -f deploy/local/docker-compose.yml up -d`
* Stop dependency services: the same with `down`
* Start Caddy ingress controller: `caddy run --config Caddyfile`

### Code Quality & Validation

All checks run through `./scripts/refcloud-static-analysis-deploy-local.sh` from the repo root (bash; needs only Docker, since each tool runs from an image pinned by version and digest). CI (`.github/workflows/static-analysis-deploy-local.yml`) calls the same script.

* Run all blocking checks: `./scripts/refcloud-static-analysis-deploy-local.sh`
* Validate the compose file: `./scripts/refcloud-static-analysis-deploy-local.sh compose`
* Validate and format-check the Caddyfile: `./scripts/refcloud-static-analysis-deploy-local.sh caddy` (fix formatting with `caddy fmt --overwrite deploy/local/Caddyfile`)
* Lint all app and `deploy/local` Dockerfiles (hadolint, config `.hadolint.yaml`): `./scripts/refcloud-static-analysis-deploy-local.sh hadolint`
* Scan all app and `deploy/local` Dockerfiles for security misconfigurations (Trivy, HIGH/CRITICAL, ignores in `/.trivyignore` for the apps and `deploy/local/.trivyignore` for this directory): `./scripts/refcloud-static-analysis-deploy-local.sh trivy-config`
* Scan images for CVEs (non-blocking report: compose images plus each Dockerfile's final base image): `./scripts/refcloud-static-analysis-deploy-local.sh images`
* The CVE scan runs weekly in CI and uploads results to the GitHub Security tab (code scanning); it never fails a build, since findings come from upstream images.
* Every ignored hadolint rule (`.hadolint.yaml`) or Trivy finding (`.trivyignore`) needs a reason. Bump a tool by changing its version and digest together in the script.
* Every app image must run as a non-root user (Trivy `DS-0002`), set by numeric id (e.g. `USER 10001`) so Kubernetes can verify `runAsNonRoot`; the Helm chart sets each pod's `runAsUser` to match. The docs image uses `nginxinc/nginx-unprivileged` and listens on 8080 (the Helm chart targets that port). The local-only Funnel image (`funnel/Dockerfile`) is the one exception, recorded in `deploy/local/.trivyignore`.

### Testing

* TBD: Run all tests
* TBD: Run specific test file

## Things to Note:

* All browser-facing traffic goes through Caddy. Any new backend path, whether a Kratos endpoint or a core-api route, needs a handle block, or it falls through to Next.js and returns a 404 page.
* Caddyfile routes must be kept in sync with the Helm ingress (`deploy/helm/charts/app/templates/common/ingress.yaml`). Add any new path to both.
* Docker containers reach host services through host.docker.internal, never the public hostname.
* Funnel mounts the host's Docker socket (root-equivalent) and runs as root: local development only, never in a shared deployment. Its TES API has no auth, so it's published on `127.0.0.1` only.
* Funnel mounts the compute storage root at the **same absolute path** as on the host, because the task containers it starts mount host paths. That path is `REFCLOUD_COMPUTE_STORAGE_ROOT` (exported by `scripts/lib/common.sh`); it must be the same directory as the storage root in `compute/connections.yml`.
