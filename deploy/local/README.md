# GA4GH Reference Cloud: Local Deployment

Configuration for running the reference cloud's dependency services and ingress on your machine.

**For instructions on how to run the full reference cloud, please see the main [README](../../README.md).**

* `docker-compose.yml` - dependency services (Ory Kratos, Ory Hydra)
* `Caddyfile` - Caddy ingress for `https://refcloud.ga4gh.localhost` and `https://docs.refcloud.ga4gh.localhost`
* `ory/` - Kratos and Hydra configuration

## Static Analysis

Static analysis of this directory and of every app Dockerfile (`apps/**/Dockerfile`) runs in CI on every pull request that changes them. Run it locally from the repo root (requires only Docker; each tool runs from a pinned image, so local runs match CI):

| Check | Command |
|-|-|
| All blocking checks below | `./scripts/refcloud-static-analysis-deploy-local.sh` |
| Compose file validation (`docker compose config`) | `./scripts/refcloud-static-analysis-deploy-local.sh compose` |
| Caddyfile validation and formatting ([Caddy](https://caddyserver.com/docs/command-line)) | `./scripts/refcloud-static-analysis-deploy-local.sh caddy` |
| Dockerfile linting ([hadolint](https://github.com/hadolint/hadolint)) | `./scripts/refcloud-static-analysis-deploy-local.sh hadolint` |
| Dockerfile security misconfigurations ([Trivy](https://trivy.dev/), HIGH and CRITICAL) | `./scripts/refcloud-static-analysis-deploy-local.sh trivy-config` |
| Image CVEs (non-blocking report) | `./scripts/refcloud-static-analysis-deploy-local.sh images` |

To fix Caddyfile formatting: `caddy fmt --overwrite deploy/local/Caddyfile`

The image CVE scan covers the images in `docker-compose.yml` and each Dockerfile's final base image. It runs weekly in CI and reports to the repository's Security tab instead of failing builds, since the findings come from upstream images.
