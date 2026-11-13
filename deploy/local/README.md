# GA4GH Reference Cloud: Local Deployment

Configuration for running the reference cloud's dependency services and ingress on your machine.

**For instructions on how to run the full reference cloud, please see the main [README](../../README.md).**

* `docker-compose.yml` - dependency services (Ory Kratos, Ory Hydra, Funnel)
* `Caddyfile` - Caddy ingress for `https://refcloud.ga4gh.localhost` and `https://docs.refcloud.ga4gh.localhost`
* `ory/` - Kratos and Hydra configuration
* `compute/connections.yml` - compute connections (engine + executor + storage + pooled limits) for native mode, read by core-api and the admin CLI
* `funnel/` - the Funnel (TES executor) image and config for native-mode compute
* `.trivyignore` - Trivy exceptions for this directory's Dockerfiles only (each with its reason)

## Compute (native mode)

Native mode can run workflows through the refcloud's WES and TES APIs: a researcher submits an allowlisted workflow to a compute platform's WES, with refcloud DRS objects as inputs, and downloads its outputs from WES. The design is in [`claude/discussions/0000-about-adding-compute.md`](../../claude/discussions/0000-about-adding-compute.md) and [`claude/specs/active/0008-add-compute-wes-tes.md`](../../claude/specs/active/0008-add-compute-wes-tes.md).

**What runs where:**

| Component | Runs | Notes |
|-|-|-|
| core-api (WES, TES) | natively | launches one `nextflow run` per WES run, as a subprocess |
| Nextflow and its nf-ga4gh plugin (TES executor) | natively, on the host | needs `nextflow` on the `PATH` (see the requirements in the main [README](../../README.md)). core-api runs the Nextflow and nf-ga4gh versions pinned in `compute/connections.yml`; Nextflow downloads both into `~/.nextflow` the first time, so that needs internet access |
| Funnel (TES server) | Docker Compose (`funnel`, built from `funnel/Dockerfile`) | TES API on `127.0.0.1:8000`, with no auth. Starts each task as a container on the host's Docker through the mounted Docker socket: **for local development only** |
| task containers | the host's Docker | started by Funnel |

**Storage:** `compute/nextflow-funnel-docker/` (git-ignored) holds the run directories (`runs/<run_id>/`: the workflow, staged inputs, work, outputs and logs) and Funnel's database and work directory. Funnel mounts it at the same absolute path as on the host, because the task containers it starts mount host paths. `./scripts/refcloud-setup.sh --reset` deletes it.

**Setup:** `./scripts/refcloud-setup.sh` checks that `nextflow` can run the pinned version, downloading it the first time. If it can't, setup warns and skips compute. Otherwise it registers and enables the `nextflow-demo` compute platform on the `nextflow-funnel-docker-local` connection, with the bundled [`drs-checksum`](../../compute/workflows/nextflow/drs-checksum/README.md) workflow on its allowlist and small per-user limits: 2 concurrent runs; 2 CPU cores, 2 GB of memory and 10 GB of disk per task; 30 minutes per run; 10 core-hours a month. An existing platform is left as it is; to recreate it, delete it with `uv run refcloud compute-platform delete -p nextflow-demo` (in `apps/admin-cli`) and run setup again.

**Running the demo end to end** (the spec's "Done when" check). On a fresh setup:

1. Install Nextflow (`brew install nextflow`), then set up from scratch and start native mode. `--reset` deletes your local databases (including local accounts), and onboarding 1000 Genomes takes a while:

   ```bash
   ./scripts/refcloud-setup.sh --reset
   ./scripts/refcloud-run-native.sh
   ```

   The run script lists `nextflow-demo`'s WES and TES endpoints. If it says no compute platforms are enabled, setup didn't find Nextflow.
2. In the researcher UI (`https://refcloud.ga4gh.localhost`), register an account. Then request access to **1000 Genomes** (Browse Datasets) and to **Nextflow Demo** (Browse Compute Platforms).
3. Approve both requests as the admin, in `apps/admin-cli`:

   ```bash
   uv run refcloud visa list-requests
   uv run refcloud visa approve -u <user id> -v ga4gh_visa:aws_opendata_registry_1000_genomes
   uv run refcloud visa approve -u <user id> -v ga4gh_visa:compute_platform:nextflow-demo
   ```

4. Copy a passport token from the researcher UI's **View Passport Token** page, and run the demo from the repo root:

   ```bash
   ./scripts/refcloud-compute-demo.sh <passport token>
   ```

   The script submits the bundled [`drs-checksum`](../../compute/workflows/nextflow/drs-checksum/README.md) workflow through WES on the demo's DRS object (`drs://refcloud.ga4gh.localhost/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai`). Then it:
   * follows the run (`QUEUED` → `RUNNING` → `COMPLETE`, about 20 seconds once Nextflow is cached);
   * downloads `checksum-report.json` through the output download endpoint;
   * checks that the report's MD5 matches the checksum DRS reports for the object.

   It needs only `curl` and Node, and verifies HTTPS against Caddy's local CA. On a refusal it says why: `401` for an invalid or expired token, `403` for a visa that isn't approved. The same steps with `curl` are in the [drs-checksum README](../../compute/workflows/nextflow/drs-checksum/README.md) and on the docs site (Interactive Demos).

**Running Docker Compose by hand:** `docker-compose.yml` needs the storage root's absolute path, which the scripts set. From the repo root:

```bash
REFCLOUD_COMPUTE_STORAGE_ROOT=$PWD/compute/nextflow-funnel-docker docker compose -f deploy/local/docker-compose.yml up -d
```

**Helm mode** has no compute: no connection is configured until stage 2. Both modes share the host databases, so the `nextflow-demo` platform registered by setup shows as unavailable in helm mode ("connection not configured").

**Known limitation:** until nf-ga4gh releases a fix for transient TES errors (D23 in the discussion doc), restarting core-api for more than a few seconds fails the workflows that are running.

## Static Analysis

Static analysis of this directory (including `funnel/Dockerfile`) and of every app Dockerfile (`apps/**/Dockerfile`) runs in CI on every pull request that changes them. Run it locally from the repo root (requires only Docker; each tool runs from a pinned image, so local runs match CI):

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
