<img src="https://www.ga4gh.org/wp-content/themes/ga4gh/dist/assets/svg/logos/logo-full-color.svg" alt="GA4GH Logo" style="width: 400px;"/>

# GA4GH Reference Cloud (Refcloud)

Reference implementation suite of multiple GA4GH standards working in tandem, designed to be applicable to a variety of use cases and workflows.

## Documentation

Please see the [documentation page](./docs) for high-level design decisions, ADRs, roadmap, and milestones.

## For Developers

This multi-app repo contains the following apps/components:

* `apps/core-api` - core backend app implementing multiple GA4GH APIs
* `apps/researcher-ui` - researcher UI for profile registration, data access requests, browsing datasets, etc.
* `apps/admin-cli` - administrative CLI for dataset registration, onboarding data, processing data access requests, etc.
* `apps/docs` - public documentation site
* `deploy/helm` - helm chart for deploying the entire reference cloud in a kubernetes cluster

The refcloud also includes the following dependency services (run via docker / docker-compose). Expected config for each service when running locally is included in `deploy/local/`

* Ory Kratos (`deploy/local/ory/kratos`) - manages researcher identities
* Ory Hydra (`deploy/local/ory/hydra`) - provisions GA4GH Passport / OAuth2.0 tokens

### Getting Started

To run the reference cloud locally, you will need the following tools installed on your machine. The suggested commands for installing each tool on macOS are provided.

| Requirement | Install (macOS) |
|---|---|
| Docker Desktop | visit [docs.docker.com](https://docs.docker.com/desktop/setup/install/mac-install/) and follow instructions |
| PostgreSQL 18+ | `brew install postgresql@18` |
| Liquibase 4+ | `brew install liquibase` |
| SDKMAN | visit [sdkman.io](https://sdkman.io) and follow instructions
| Java 25+ | Install through sdkman using `sdk install java 25.0.3-amzn` then activate with `sdk use java 25.0.3-amzn` |
| Gradle 9+ | Install through sdkman using `sdk install gradle 9.5.1` |
| Node Version Manager (nvm) | visit [nodejs.org](https://nodejs.org/en/download) and follow instructions |
| Node.js v24+ | Install through nvm using `nvm install v24.15.0` then activate with `nvm use v24.15.0` |
| kubectl v1.36+ | `brew install kubectl` |
| minikube v1.38+ | `brew install minikube` |
| helm v4.2+ | `brew install kubernetes-helm` |
| mkcert v1.4.4+ | `brew install mkcert` |
| caddy v2.11+ | `brew install caddy` then `brew services start caddy` |

### Initial Setup

In order to run the reference cloud, you will first need to perform initial setup of local resources, including:

* create local host mappings
* create databases and roles 
* create OAuth 2.0 client(s)
* load dependencies
* create reference datasets and onboard data

First, add the following 2 lines at the bottom of your `/etc/hosts` file (ensure there is a tab separation between the IP address and hostname):

```
127.0.0.1   refcloud.ga4gh.local
127.0.0.1   docs.refcloud.ga4gh.local
```

Next, run `./scripts/refcloud-setup.sh` to perform the remaining setup steps.

### Running the Reference Cloud natively

When running the refcloud natively, all apps in the `apps/` folder will run directly on your machine (without docker). All apps will be run in dev mode so that code changes are reflected automatically (without having to restart the app).

All dependency services (i.e. Ory Kratos, Ory, Hydra, etc.) will be run via docker-compose.

To start all services in native mode, run `./scripts/refcloud-run-native.sh`

### Running the Reference Cloud via Helm

When running the refcloud via helm, all services will be run through a helm chart rather than through the source code in the `apps/` folder.

To start all services in helm mode, run `./scripts/refcloud-run-helm.sh`

## Contributing

If you are interested in helping us build the reference cloud, please see the documentation on [how to contribute](./docs/CONTRIBUTING.md).

## AI Disclosure

Artificial intelligence tools, including large language models (LLMs), were used in the development of this project to support clarifying technical concepts and generating code snippets. These tools served as an aid for idea refinement, debugging, and testing of the program. All AI-generated code was thoroughly reviewed, verified for correctness, and fully understood before being incorporated into this work. Responsibility for all final decisions, interpretations, and implementations remains solely with the contributors.
