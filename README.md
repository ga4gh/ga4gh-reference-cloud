<img src="https://www.ga4gh.org/wp-content/themes/ga4gh/dist/assets/svg/logos/logo-full-color.svg" alt="GA4GH Logo" style="width: 400px;"/>

# GA4GH Reference Cloud

Reference implementation suite of multiple GA4GH standards working in tandem, designed to be applicable to a variety of use cases and workflows.

## Documentation

Please see the [documentation page](./docs) for high-level design decisions, ADRs, roadmap, and milestones.

## For Developers

This multi-app repo contains the following apps/components:
* `apps/core-api` - core backend app implementing multiple GA4GH APIs
* `apps/researcher-ui` - researcher UI for profile registration, data access requests, browsing datasets, etc.
* `apps/researcher-docs` - public documentation site for researchers
* `apps/admin-cli` - administrative CLI for dataset registration, onboarding data, processing data access requests, etc.
* `deploy/helm` - helm chart for deploying the entire reference cloud in a kubernetes cluster

### Getting Started

To run the reference cloud locally, you will need the following tools installed on your machine. The suggested commands for installing each tool on macOS are provided. 

| Requirement | Install (macOS) |
|---|---|
| Docker Desktop | visit [docs.docker.com](https://docs.docker.com/desktop/setup/install/mac-install/) and follow instructions |
| PostgreSQL 18+ | `brew install postgresql@18` |
| Liquibase 4+ | `brew install liquibase` |
| SDKMAN | visit [sdkman.io](https://sdkman.io) and follow instructions
| Java 25+ | Install through sdkman using `sdk install java 25.0.3-amzn` |
| Gradle 9+ | Install through sdkman using `sdk install gradle 9.5.1` |

## Contributing

If you are interested in helping us build the reference cloud, please see the documentation on [how to contribute](./docs/CONTRIBUTING.md).

## AI Disclosure

Artificial intelligence tools, including large language models (LLMs), were used in the development of this project to support clarifying technical concepts and generating code snippets. These tools served as an aid for idea refinement, debugging, and testing of the program. All AI-generated code was thoroughly reviewed, verified for correctness, and fully understood before being incorporated into this work. Responsibility for all final decisions, interpretations, and implementations remains solely with the contributors.
