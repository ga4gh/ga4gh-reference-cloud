# GitHub Actions Workflows

| Workflows | Purpose |
|-|-|
| `static-analysis-<app>.yml` | Static analysis for one app or context (required PR checks) |
| `push-docker-image-<app>.yml` | Builds and pushes an app image to ECR on pushes to `develop`, via the reusable `push-docker-image.yml` |

## Conventions

Every workflow here is linted and audited by `static-analysis-workflows.yml`. Run the same checks locally from the repo root (requires only Docker):

| Check | Command |
|-|-|
| All checks | `./scripts/refcloud-static-analysis-workflows.sh` |
| Syntax, expressions and shellcheck of `run:` steps ([actionlint](https://github.com/rhysd/actionlint)) | `./scripts/refcloud-static-analysis-workflows.sh actionlint` |
| Security ([zizmor](https://docs.zizmor.sh/), config `.github/zizmor.yml`) | `./scripts/refcloud-static-analysis-workflows.sh zizmor` |

Secrets are scanned by `static-analysis-secrets.yml` on every PR, across the full git history ([gitleaks](https://github.com/gitleaks/gitleaks), config `.gitleaks.toml` and `.gitleaksignore`). Run `./scripts/refcloud-static-analysis-secrets.sh staged` before committing to catch a secret before it is pushed. GitHub push protection (a repository setting) also blocks known credential formats at push time.

zizmor's online audits (known-vulnerable actions, impostor commits) need a GitHub token: run with `GH_TOKEN=<token>` to include them, as CI does.

* **Pin every action to a full commit SHA**, with the version as a trailing comment: `uses: actions/checkout@<sha> # v7.0.1`. Never pin to a tag, since tags can be moved (as happened to Trivy's actions in March 2026). Dependabot (`.github/dependabot.yml`) opens one grouped PR a month with minor and patch updates; major upgrades are manual.
* **Checkouts never keep the token**: every `actions/checkout` sets `persist-credentials: false`.
* **Pass reusable workflows only the secrets they need**, declared under `workflow_call.secrets`; never `secrets: inherit`.
* **Never interpolate `${{ ... }}` expressions into `run:` scripts** (template injection); pass them through `env:` instead.
* **Keep permissions minimal**: `contents: read` at the workflow level, and anything more only on the job that needs it.
* **Static analysis workflows follow one pattern** (except `static-analysis-secrets.yml`, which runs on every PR because a secret can be in any file): a `detect-changes` job (`dorny/paths-filter`) that always runs, and a required job named `Static Analysis (<app>)` that is skipped (counted as passing) when nothing it checks changed, and fails if change detection fails. Each check is its own step and runs even if an earlier one failed.
* Every zizmor suppression in `.github/zizmor.yml` needs a reason.
