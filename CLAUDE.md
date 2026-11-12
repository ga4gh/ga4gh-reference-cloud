# Project Guide: GA4GH Reference Cloud

## Project Overview & Architecture

The GA4GH Reference Cloud (Refcloud) is a reference implementation of multiple API specifications from the [Global Alliance for Genomics and Health](https://ga4gh.org). The Refcloud is designed to provide a live model of GA4GH standards in action, demonstrating how they work as running applications, and elucidating how various models and concepts can be connected across different GA4GH Work Streams.

This repo serves as the multi-app repository for all apps involved in the refcloud.

## Core Commands

High-level bash/zsh scripts are located in the `scripts/` directory. Scripts include:

* set up baseline resources (safe to re-run): `./scripts/refcloud-setup.sh` (`--reset` to start fresh)
* run all refcloud services from source: `./scripts/refcloud-run-native.sh`
* run all refcloud services in minikube via helm: `./scripts/refcloud-run-helm.sh` (`--down` to uninstall)
* static analysis of `deploy/local` and the app Dockerfiles: `./scripts/refcloud-static-analysis-deploy-local.sh` (bash, needs only Docker; see `deploy/local/README.md`)
* static analysis of the helm chart (`deploy/helm`): `./scripts/refcloud-static-analysis-helm.sh` (bash, needs only Docker; see `deploy/helm/README.md`)
* static analysis of the GitHub Actions workflows: `./scripts/refcloud-static-analysis-workflows.sh` (bash, needs only Docker; conventions in `.github/workflows/README.md`)
* secret scanning (gitleaks): `./scripts/refcloud-static-analysis-secrets.sh` scans the full git history (as CI does); `./scripts/refcloud-static-analysis-secrets.sh staged` scans staged changes, so run it before committing

Scripts are run from the repo root and share constants/functions in `scripts/lib/common.sh`.

Each app's static analysis commands (lint, format, type check, security) are in its `README.md` and `CLAUDE.md`. Every check also runs in CI (`.github/workflows/static-analysis-*.yml`) with pinned tool versions, so local runs match CI.

## Repository Layout

### Application Directories

Source code for refcloud services/apps are stored in the `apps/` directory. Apps include:

* `apps/core-api` - core backend app implementing multiple GA4GH APIs
* `apps/researcher-ui` - researcher UI for profile registration, data access requests, browsing datasets, etc.
* `apps/admin-cli` - administrative CLI for dataset registration, onboarding data, processing data access requests, etc.
* `apps/docs` - public documentation site

### Deployment Directories

Configuration code for deploying the refcloud is stored in the `deploy/` directory. Deployment contexts include:

* `deploy/local` - local deployment of the refcloud on user's machine
* `deploy/helm` - helm chart for deploying the refcloud in a local or remote kubernetes cluster

### Other Directories

* `.github` - GitHub Actions workflows (conventions, including SHA-pinned actions, in `.github/workflows/README.md`) and Dependabot config
* `assets/` - web/art assets used by the refcloud
* `claude/` - contains historical and active specs that claude can reference to develop new features and fix bugs
* `compute/` - compute (WES/TES): bundled demo workflows in `compute/workflows/` (tracked), and native-mode run storage in `compute/nextflow-funnel-docker/` (git-ignored by `compute/.gitignore`, cleared by `--reset`)
* `docs/` - high-level planning and milestone documentation

## Things to Note

* When running the refcloud locally via either `deploy/local` or `deploy/helm`, the expected base URL is `https://refcloud.ga4gh.localhost` for all backend & frontend services, and `https://docs.refcloud.ga4gh.localhost` for the docs
* Only one local mode (native or helm) runs at a time: Caddy and `minikube tunnel` both bind port 443. Both modes share the host Postgres databases.
* Compute runs in native mode only (helm mode has no compute connection until stage 2, so the default `nextflow-demo` platform shows as unavailable there). Nextflow runs natively (core-api launches `nextflow run`); Funnel runs in Docker Compose and starts task containers through the host's Docker socket, for local development only. See `deploy/local/README.md`.
* The repo is public and has GitHub push protection on. Never commit real credentials: anything pushed is public and can't be fully removed. Dev-only defaults (`secret`, the Hydra system secret in `deploy/local/ory/hydra/hydra.yml`) are local/minikube-only and allowlisted in `.gitleaks.toml`; any new allowlist entry needs a reason.
* Versioning of the refcloud apps follows a "lockstep versioning" pattern. When a new release is minted, all apps will have the same docker tag. Docker tags are derived from `git describe --tags` (local helm builds append `-uncommitted` when the working tree has changes)
