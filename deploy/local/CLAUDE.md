# Project Guide: GA4GH Reference Cloud Local Deployment

## Project Overview & Architecture

This repo serves to provide configuration for the local deployment context 

* **Tech Stack:** Caddy, Docker, Docker Compose, Ory Kratos & Hydra, GA4GH Refcloud

### Directory Layout

* `Caddyfile` - Caddy-based ingress controller
* `docker-compose.yml` - compose file for spinning up dependency services
* `ory/hydra` - config files for deployment of Ory Hydra 
* `ory/kratos` - config files for deployment of Ory Kratos

---

## Core Commands

### Development & Build

* Start dependency services: `docker-compose up -d`
* Stop dependency services: `docker-compose down`
* Start Caddy ingress controller: `caddy run --config Caddyfile`

### Code Quality & Validation

All checks run through `./scripts/refcloud-static-analysis-deploy-local.sh` from the repo root (bash; needs only Docker, since each tool runs from an image pinned by version and digest). CI (`.github/workflows/static-analysis-deploy-local.yml`) calls the same script.

* Run all blocking checks: `./scripts/refcloud-static-analysis-deploy-local.sh`
* Validate the compose file: `./scripts/refcloud-static-analysis-deploy-local.sh compose`
* Validate and format-check the Caddyfile: `./scripts/refcloud-static-analysis-deploy-local.sh caddy` (fix formatting with `caddy fmt --overwrite deploy/local/Caddyfile`)
* Lint all app Dockerfiles (hadolint, config `.hadolint.yaml`): `./scripts/refcloud-static-analysis-deploy-local.sh hadolint`
* Scan all app Dockerfiles for security misconfigurations (Trivy, HIGH/CRITICAL, ignores in `.trivyignore`): `./scripts/refcloud-static-analysis-deploy-local.sh trivy-config`
* Scan images for CVEs (non-blocking report: compose images plus each Dockerfile's final base image): `./scripts/refcloud-static-analysis-deploy-local.sh images`
* The CVE scan runs weekly in CI and uploads results to the GitHub Security tab (code scanning); it never fails a build, since findings come from upstream images.
* Every ignored hadolint rule (`.hadolint.yaml`) or Trivy finding (`.trivyignore`) needs a reason. Bump a tool by changing its version and digest together in the script.
* Every app image must run as a non-root user (Trivy `DS-0002`). The docs image uses `nginxinc/nginx-unprivileged` and listens on 8080 (the Helm chart targets that port).

### Testing

* TBD: Run all tests
* TBD: Run specific test file

## Things to Note:

* All browser-facing traffic goes through Caddy. Any new backend path, whether a Kratos endpoint or a core-api route, needs a handle block, or it falls through to Next.js and returns a 404 page.
* Caddyfile routes must be kept in sync with the Helm ingress (`deploy/helm/charts/app/templates/common/ingress.yaml`). Add any new path to both.
* Docker containers reach host services through host.docker.internal, never the public hostname.
