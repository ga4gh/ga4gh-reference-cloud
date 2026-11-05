# Project Guide: GA4GH Reference Cloud

## Project Overview & Architecture

The GA4GH Reference Cloud (Refcloud) is a reference implementation of multiple API specifications from the [Global Alliance for Genomics and Health](https://ga4gh.org). The Refcloud is designed to provide a live model of GA4GH standards in action, demonstrating how they work as running applications, and elucidating how various models and concepts can be connected across different GA4GH Work Streams.

This repo serves as the multi-app repository for all apps involved in the refcloud.

## Core Commands

High-level bash/zsh scripts are located in the `scripts/` directory. Scripts include:

* set up baseline resources (safe to re-run): `./scripts/refcloud-setup.sh` (`--reset` to start fresh)
* run all refcloud services from source: `./scripts/refcloud-run-native.sh`
* run all refcloud services in minikube via helm: `./scripts/refcloud-run-helm.sh` (`--down` to uninstall)

Scripts are run from the repo root and share constants/functions in `scripts/lib/common.sh`.

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

* `.github` - GitHub Actions workflows
* `assets/` - web/art assets used by the refcloud
* `claude/` - contains historical and active specs that claude can reference to develop new features and fix bugs
* `docs/` - high-level planning and milestone documentation

## Things to Note

* When running the refcloud locally via either `deploy/local` or `deploy/helm`, the expected base URL is `https://refcloud.ga4gh.localhost` for all backend & frontend services, and `https://docs.refcloud.ga4gh.localhost` for the docs
* Only one local mode (native or helm) runs at a time: Caddy and `minikube tunnel` both bind port 443. Both modes share the host Postgres databases.
* Versioning of the refcloud apps follows a "lockstep versioning" pattern. When a new release is minted, all apps will have the same docker tag. Docker tags are derived from `git describe --tags` (local helm builds append `-uncommitted` when the working tree has changes)
