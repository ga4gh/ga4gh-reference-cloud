# Feature Spec: Add static analysis of code to the refcloud repo

**Branch:** `ja-iss-42-add-static-analysis`

**Linked GitHub Issue:** [Issue 42](https://github.com/ga4gh/ga4gh-reference-cloud/issues/42)

## Objective

Add static analysis guardrails to Reference Cloud CI so bugs and vulnerabilities are caught during PR review.

Static analyses include:

* type checking / successful compilation
* linting
* security auditing

In addition, impose repo-wide security to prevent upload of sensitive credentials.

Lastly, include caching techniques to streamline CI wherever possible. For example, in the case that an app's code hasn't changed, don't rerun static analysis if possible.

## Engineering Tasks

### Task 1: Add Static Analysis to admin-cli

* The admin-cli is a Python UV app, so include the following static analysis methods:
  * Use Ruff and Pyright for Type Checking
  * Use Ruff for linting
  * Use Ruff's built-in security rules (the S / Bandit ruleset) to check for unsafe file permissions, hardcoded passwords, or insecure processes
* static analysis tools must be invokable both by devs locally and within CI.
* include the static analysis commands that should be run in `apps/admin-cli/README.md` as well as `apps/admin-cli/CLAUDE.md`
* add a github actions workflow file at `.github/workflows/static-analysis-admin-cli.yml` that performs static analysis checks, failing the overall workflow if any of the tests fail. Configure the workflow to only run when there are changes to `apps/admin-cli/**`

**Decisions (Task 1):**

* Ruff is not a type checker: Pyright does type checking (`standard` mode); Ruff does linting (rules `E, W, F, I, B, UP, S`) and formatting, which is also enforced in CI (`ruff format --check`). Line length is 120.
* Ruff and Pyright (`pyright[nodejs]`, so no system Node is needed) are pinned exactly in the uv `dev` dependency group, so local runs and CI use identical versions from `uv.lock`. The Docker image excludes them (`UV_NO_DEV=1`). Config is in `pyproject.toml`.
* Existing violations were fixed rather than ignored, including two real bugs found by Pyright: refget sequence aliases in `dataset onboard` could be linked to the previous object's sequence, and `dataset init` read `tag.id` without a `None` check. md5 checksums use `usedforsecurity=False` instead of a `# noqa`.
* The workflow triggers on `pull_request` only (no `push` to `main`), since PRs are the gate.
* The check is designed to be made a **required status check**: there is no workflow-level `paths` filter (a path-filtered workflow that never runs blocks required checks forever). Instead a `detect-changes` job (`dorny/paths-filter`) always runs, and the `Static Analysis (admin-cli)` job is skipped when nothing under `apps/admin-cli/**` (or the workflow file) changed; GitHub counts a skipped job as passing. Make `Static Analysis (admin-cli)` the required check. Because a skip passes, the static analysis job also runs, and fails immediately, whenever `detect-changes` did not succeed; otherwise a broken change detection would let a PR merge unchecked.
* Caching: `astral-sh/setup-uv` caches the uv package cache keyed on `uv.lock`. The Ruff and Pyright runs take seconds, so their caches aren't persisted. The check steps all run even if one fails, so a single run reports every problem.
* Testing commands remain TBD (handled in a later task).

### Task 2: Add Static Analysis to researcher-ui

* The researcher-ui is a TypeScript Next JS app, so include the following static analysis methods:
  * Use `tsc --noEmit` for type checking and compilation
  * Use ESLint configured with `eslint-config-next` for linting
  * Use `npm audit` to track package-level security vulnerabilities
  * Use semgrep rules tailored for Node.js
* static analysis tools must be invokable both by devs locally and within CI.
* include the static analysis commands that should be run in `apps/researcher-ui/README.md` as well as `apps/researcher-ui/CLAUDE.md`
* add a github actions workflow file at `.github/workflows/static-analysis-researcher-ui.yml` that performs static analysis checks, failing the overall workflow if any of the tests fail. Configure the workflow to only run when there are changes to `apps/researcher-ui/**`
* follow conventions established in the previous task (file naming, job naming, detect-changes job, etc.)

**Decisions (Task 2):**

* **TypeScript 7 vs typescript-eslint:** typescript-eslint (required by `eslint-config-next`) does not support TS 7, which ships no compiler API. Per Microsoft's guidance, `typescript` is aliased to `@typescript/typescript6` (the TS 6 API, used by ESLint and `next build`'s type check) and TS 7 is installed as `@typescript/native`, so `tsc` is still TS 7. Remove the aliases once typescript-eslint supports TS 7.1+.
* **ESLint:** migrated from the legacy `.eslintrc.json` (ignored by ESLint 9) to `eslint.config.mjs` with `eslint-config-next/core-web-vitals` + `eslint-config-next/typescript` + `eslint-config-prettier`. `next lint` was removed in Next 16, so `npm run lint` runs `eslint . --max-warnings 0` (warnings fail CI too).
* **Prettier** added (`printWidth` 120, otherwise defaults) and enforced in CI. Markdown (repo uses `*` bullets) and `tsconfig.json` (rewritten by Next) are excluded.
* **Existing violations fixed** (55 ESLint problems): typed the Ory flow UI with `@ory/client` types instead of `any`; `LogoutLink` now uses the app router only (fixes a conditional hook call) and creates its logout flow once instead of on every render (the Sidebar passed no deps); state resets moved out of effects (Navbar, DRS and refget modals) and `isLoadingToken` is derived; JSX moved out of try/catch in `DrsObjectDownloadButton`. The four `<img>` elements (static SVG logos, and a Kratos-supplied data URI) keep a one-line disable with a reason.
* **npm audit:** fails at `high` and above, dev dependencies included. `npm audit fix` resolved the existing critical/high advisories within existing semver ranges (10 moderate remain; they need breaking upgrades).
* **Semgrep:** run via a pinned `uvx semgrep@1.184.0` (uv is already required for admin-cli) with `p/nodejs`, `p/typescript`, `p/react` and `p/nextjs`, `--metrics=off`; no login needed. The binary is pinned but registry rules are fetched live, so rule drift is accepted.
* **Workflow:** same pattern as Task 1 (required job `Static Analysis (researcher-ui)`). Also runs weekly (Mondays 06:00 UTC) and on `workflow_dispatch`, checking everything regardless of changes, so new advisories surface without UI PRs; failures appear in the Actions tab. Node is pinned to 24.15.0 (matches the Dockerfile) with the `setup-node` npm cache; the uv cache (for semgrep) is keyed on `package.json`.
* Dependabot security updates (PRs for fixable advisories) are a repo setting, not part of this task.

### Task 3: Add Static Analysis to docs

* The docs app is a TypeScript Docusaurus app, so include the following static analysis methods:
  * Use `tsc --noEmit` for type checking and compilation
  * Use ESLint configured with `@docusaurus/eslint-plugin` (plus `typescript-eslint`, `eslint-plugin-react` and `eslint-plugin-react-hooks`) for linting
  * Use `npm audit` (via `audit-ci`, to allow documented exceptions) to track package-level security vulnerabilities
  * Use semgrep rules tailored for Node.js
* static analysis tools must be invokable both by devs locally and within CI.
* include the static analysis commands that should be run in `apps/docs/README.md` as well as `apps/docs/CLAUDE.md`
* add a github actions workflow file at `.github/workflows/static-analysis-docs.yml` that performs static analysis checks, failing the overall workflow if any of the tests fail. Configure the workflow to only run when there are changes to `apps/docs/**`
* follow conventions established in the previous two tasks (file naming, job naming, detect-changes job, etc.)

Note: Overall this is very similar to Task 2, just applied to the docs app.

**Decisions (Task 3):**

* **ESLint:** `eslint-config-next` is Next.js-specific, so the docs use Docusaurus' official `@docusaurus/eslint-plugin` (recommended rules) with `typescript-eslint` (recommended), `eslint-plugin-react` (recommended + jsx-runtime), `eslint-plugin-react-hooks` and `eslint-config-prettier`; `--max-warnings 0`. The app is on TypeScript 6, so no alias is needed (unlike Task 2). Fixed the existing problems, including a conditional `useBaseUrl` hook call in the swizzled `Navbar/Logo`; raw headings now use `@theme/Heading`; `sidebars.ts` imports the generated DRS sidebar instead of `require()`-ing it.
* **tsc** was failing on PNG imports (Docusaurus only declares `*.svg`); added a `*.png` declaration to `src/env.d.ts`.
* **Prettier** added with the same config as Task 2. Markdown/MDX (repo style, and Prettier's MDX support predates MDX 3), `static/`, `openapi/` and the generated `docs/api-reference/` are excluded from Prettier and ESLint.
* **npm audit:** 50 advisories (18 critical, 19 high), mostly from transitive dependencies of Docusaurus 3.10.2 (the latest release) and `docusaurus-plugin-openapi-docs`. Resolved by bumping the OpenAPI plugins to 5.2.0, `npm audit fix`, and npm `overrides` forcing patched `tinypool` (^2.2.0), `serialize-javascript` (^7.1.4) and `js-yaml` (4.3.2, only under `openapi-to-postmanv2`, which pins 4.3.0). The site builds from a clean `npm ci`.
* **Audit exceptions (option b):** the one remaining high advisory, `@faker-js/faker` 5.x `helpers.fake` (GHSA-qxc2-j82w-r537, via the OpenAPI plugin's Postman code-sample generator), has no compatible fix (forcing faker 10 breaks the build) and only runs at build time on our own specs. It is allowlisted with a reason in `audit-ci.jsonc` (`audit-ci` 7.1.0, pinned), which otherwise fails at `high` and above like Task 2; any new advisory still fails CI. Remove entries and overrides once upstream ships fixes.
* **Semgrep:** `p/nodejs`, `p/typescript`, `p/react` (no `p/nextjs`).
* **Workflow:** same as Task 2 (`Static Analysis (docs)` is the required job; weekly + manual runs; Node 24.15.0 to match the Dockerfile).

### Task 4: Add Static Analysis to core-api

* The core-api is a Java Spring Boot app, so include the following static analysis methods:
  * Compiling: Use javac (Gradle's `compileJava`) with all lint warnings enabled and treated as errors (`-Xlint:all -Werror`)
  * Formatting: Use Spotless with `palantir-java-format` (4-space indent, 120 columns) to enforce and auto-fix code formatting
  * Linting & Quality: Use SpotBugs to catch structural bugs, performance issues, and code smells. Use Checkstyle (custom config) to enforce non-formatting rules such as imports, naming, and common coding problems.
  * Security Audits (SAST): Plug the FindSecBugs plugin into SpotBugs to scan for security issues like SQL injection, weak cryptography, improper authorization controls
  * ~~Dependency Audits: Use the OWASP Dependency-Check Gradle Plugin to scan compiled .jar dependencies against known Common Vulnerabilities and Exposures (CVEs)~~ (deferred, see decisions)
* static analysis tools must be invokable both by devs locally and within CI.
* include the static analysis commands that should be run in `apps/core-api/README.md` as well as `apps/core-api/CLAUDE.md`
* add a github actions workflow file at `.github/workflows/static-analysis-core-api.yml` that performs static analysis checks, failing the overall workflow if any of the tests fail. Configure the workflow to only run when there are changes to `apps/core-api/**`
* follow conventions established in the previous task (file naming, job naming, detect-changes job, etc.)

**Decisions (Task 4):**

* **Compiling:** SpotBugs analyzes bytecode and does not compile, so compilation is checked by javac itself with `-Xlint:all -Werror` (`-Xlint:-processing` silences Lombok's annotation-processor noise). Fixed the 13 existing warnings by adding `serialVersionUID` (with `@Serial`) to exception and composite-ID classes.
* **Formatting:** stock Checkstyle configs don't fit (`sun_checks`: 1,927 violations, mostly Javadoc/`final` parameters; `google_checks` requires 2-space indent while the code uses 4) and Checkstyle can't auto-fix. Formatting is enforced by Spotless with `palantir-java-format` (4-space indent, 120 columns, closest to the existing style), with `spotlessApply` to fix; the codebase was reformatted once. Checkstyle uses a small custom config (`config/checkstyle/checkstyle.xml`) with non-formatting rules only (imports, naming, empty blocks, `equals`/`hashCode`, switch defaults, boolean simplification, braces), so the two tools never conflict.
* **SpotBugs + FindSecBugs:** effort `MAX`, fails on medium confidence and above, `main` sources only. Project-wide exclusions in `config/spotbugs/exclude.xml`: `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` (Lombok getters/setters on entities and DTOs) and `SPRING_ENDPOINT` (an informational endpoint inventory). Individual findings were fixed or suppressed with `@SuppressFBWarnings(justification = ...)`:
  * fixed: a possible null dereference in `OrySessionFilter` (`getBody()` called twice); `== true`/`== false` comparisons, including a nullable `Boolean isManifest` now read null-safely.
  * suppressed with justification: CSRF disabled in `SecurityConfig` (core-api never authenticates from cookies: `X-Session-Token`, `Authorization: Bearer` and basic auth only), `SQL_INJECTION_SPRING_JDBC` in `DataConnectQueryExecutor` (the only concatenation is a numeric `SET LOCAL statement_timeout` from config), `REDOS` on the Data Connect table name pattern (segments separated by a literal `.`, no catastrophic backtracking), and `UWF_UNWRITTEN_FIELD` on the `@Immutable` Data Connect entities (populated by Hibernate).
* **OWASP Dependency-Check: deferred.** It needs an NVD API key stored as a secret, which isn't available to fork or Dependabot PRs, and a cached NVD database. Revisited in Task 10 (Dependabot alerts may not cover Gradle at all: GitHub often needs Gradle dependencies submitted from CI).
* **Gradle:** a `staticAnalysis` task runs everything (tests excluded); a Java 25 toolchain is declared in `build.gradle` to match the Dockerfile and CI (Corretto 25). Pinned: SpotBugs plugin 6.5.13 (SpotBugs 4.10.5), FindSecBugs 1.14.0, Checkstyle 14.4.0, Spotless 8.11.0 (palantir-java-format 2.103.0).
* **Docker:** the Dockerfile builds with `gradle bootJar` instead of `gradle build -x test`, so image builds don't run static analysis; CI is the gate.
* **Workflow:** same detect-changes pattern (`Static Analysis (core-api)` is the required job) on `pull_request` and `workflow_dispatch`; no weekly run, since there's no dependency audit yet. `gradle/actions/setup-gradle` caches dependencies (writable on PRs, since the workflow doesn't run on the default branch); reports are uploaded as an artifact when a check fails.

### Task 5: Add Static Analysis to Local Deployment Context

* Local deployment files are included in `deploy/local/`. These include docker-compose configs for dependency services (Ory Kratos, Hydra) and the Caddyfile for setting up the local ingress controller.
* Syntactically validate docker compose files
* Run Trivy to scan docker files and compose files for base image vulnerabilities, root user execution, and insecure port errors.
  * Would it also be good to run Trivy scanning for the Dockerfiles in "apps/" ? e.g.
    * apps/core-api/Dockerfile
    * apps/core-api/liquibase/Dockerfile
    * apps/admin-cli/Dockerfile
    * apps/researcher-ui/Dockerfile
    * apps/docs/Dockerfile

**Decisions (Task 5):**

* Yes, scan the `apps/` Dockerfiles: Trivy's misconfiguration scanner (`trivy config`) supports Dockerfiles but not docker-compose files, so compose files get `docker compose config` validation and a CVE scan of the images they reference.
* One workflow, `.github/workflows/static-analysis-deploy-local.yml` (required job `Static Analysis (deploy-local)`), triggered by `deploy/local/**` and `apps/**/Dockerfile`.
* `trivy config` blocks on HIGH/CRITICAL; ignored findings (e.g. LOW `HEALTHCHECK`, which Kubernetes replaces with probes) go in `.trivyignore` with reasons. Fix the root-user findings: non-root user in the admin-cli image, explicit `USER liquibase`, and `nginxinc/nginx-unprivileged` for docs (listens on 8080, so the Helm service port changes too).
* `trivy image` CVE scanning (compose images and the Dockerfiles' base images) is non-blocking: weekly + manual runs with `--ignore-unfixed`, results uploaded as SARIF to GitHub code scanning. Every image currently has HIGH CVEs, including the latest Hydra release, so a blocking gate would be permanently red.
* Also validate the Caddyfile (`caddy validate`, `caddy fmt --diff`) and remove the obsolete `version:` key from `docker-compose.yml`.
* Pin Trivy to an exact version, run from a pinned image digest or a SHA-pinned action, never a tag (Trivy's actions were compromised in March 2026). Pin the `ghcr.io/astral-sh/uv` image in the admin-cli Dockerfile (Trivy doesn't check `COPY --from` images).
* Out of scope: upgrading Kratos from `v0.9.0-alpha.2` (132 HIGH/CRITICAL CVEs on an EOL Alpine) is tracked as a separate ticket, kept in sync with the Helm chart.
* **hadolint** added for the Dockerfiles (best practices plus ShellCheck of `RUN` lines; complements Trivy's security checks). Fails on warnings; `DL3018` (pin `apk` versions) is ignored with a reason in `.hadolint.yaml`, since Alpine drops superseded package versions. dclint was not added: `docker compose config` already validates the single compose file.
* **One script, no local installs:** `scripts/refcloud-static-analysis-deploy-local.sh` (bash, so it runs on CI's Ubuntu runners and macOS) runs every check from a tool image pinned by version and digest (Trivy 0.76.2, hadolint 2.15.1, Caddy 2.11.9); CI calls the same script, one step per check. Checks: `compose`, `caddy`, `hadolint`, `trivy-config` (blocking) and `images` (report only). `.hadolint.yaml` and `.trivyignore` live at the repo root because they cover every Dockerfile.
* **Fixes:** admin-cli runs as a non-root user (uid 10001) and pins `ghcr.io/astral-sh/uv:0.11.24` by digest; Liquibase states its base image's `WORKDIR /liquibase` and `USER liquibase`; docs uses `nginxinc/nginx-unprivileged` (uid 101, port 8080) and the Helm docs deployment/service now target 8080 (the service still listens on 80). `docker-compose.yml` drops the obsolete `version` key. All three images were built and smoke-tested.
* **CVE workflow:** on schedule and manual runs only, a `list-images` job feeds a matrix (one job per image, since code scanning needs a distinct `category` per SARIF upload); each uploads Trivy SARIF (`--ignore-unfixed`, HIGH/CRITICAL) with `github/codeql-action/upload-sarif`, pinned to a commit SHA because that job holds a `security-events: write` token. Code scanning is free for public repositories.

### Task 6: GitHub Actions workflow security

* Pin every third-party action in `.github/workflows/` to a full commit SHA (with the version as a trailing comment) instead of a tag.
* Add a Dependabot `github-actions` entry so the pinned SHAs are kept up to date.
* Lint the workflows themselves with actionlint (syntax, expressions, shellcheck of `run:` steps) and zizmor (security: unpinned actions, template injection, excessive permissions), in a `.github/workflows/static-analysis-workflows.yml` triggered by `.github/**`.

**Decisions (Task 6):**

* **Pinning:** every action (GitHub-owned ones included) is pinned to a full commit SHA with the version as a trailing comment, at the latest release within its current major, so behavior doesn't change. The Docker push workflows keep their older majors (`actions/checkout` v4, `docker/*` v3, `build-push-action` v6) for a later upgrade; `configure-aws-credentials` moved within v6 (6.1.0 to 6.3.0).
* **Dependabot (`.github/dependabot.yml`), tuned for minimal PRs:** `github-actions` and `docker` (the app Dockerfiles' base images) only, each a single grouped PR per month, minor/patch only (majors are manual), with a 7-day cooldown on new releases. npm/uv/gradle version updates are not enabled. Not covered: versions pinned inside scripts (tool images and digests in `scripts/refcloud-static-analysis-*.sh`, `uvx semgrep@...`), and the Ory images in `docker-compose.yml`, which must change together with the Helm chart. Both entries set `target-branch: develop`, so update PRs go to the integration branch; Dependabot still reads this file only from the default branch (`main`), so it takes effect once the file reaches `main`.
* **zizmor fixes:** `persist-credentials: false` on every checkout (`artipacked`); the 5 Docker push callers pass `AWS_ACCOUNT_ID`, `AWS_REGION` and `AWS_ROLE_ARN` explicitly, declared under `workflow_call.secrets`, instead of `secrets: inherit`. The `self-repository` audit (GitHub's July 2026 `uses: $/...` syntax) is disabled with a reason in `.github/zizmor.yml`, because actionlint 1.7.12 rejects that syntax.
* **Tooling:** `scripts/refcloud-static-analysis-workflows.sh` runs actionlint 1.7.12 and zizmor 1.32.0 from images pinned by digest (Docker only). zizmor also audits `dependabot.yml` and fails on any finding; its online audits run in CI with the read-only `github.token`, and offline locally unless `GH_TOKEN` is set.
* **Workflow:** `static-analysis-workflows.yml`, triggered by `.github/**` and the script, with the required job `Static Analysis (workflows)`. Conventions are documented in `.github/workflows/README.md`.

### Task 7: Secret scanning

* Implements the objective's "prevent upload of sensitive credentials".
* Enable GitHub secret scanning and push protection (repo settings).
* Add gitleaks in CI (and optionally as a pre-commit hook) so secrets are also caught in commits pushed from anywhere; allowlist the dev-only default passwords in `deploy/local` and `deploy/helm`, each with a reason.

**Decisions (Task 7):**

* **Importance:** the repo is public, so anything pushed is world-readable and can't be fully removed. GitHub secret scanning and push protection (enabled in repo settings) are the high-value layer: they block known token formats before they land. gitleaks in CI adds generic detection (passwords, private keys, JWTs) but runs after the push, so its role is to block the merge and raise the alarm.
* **gitleaks** 8.30.1 runs through `scripts/refcloud-static-analysis-secrets.sh` from an image pinned by digest (not `gitleaks-action`, which needs a paid license for organization repos). CI scans the **full history** on every PR (about a second), so a secret committed and removed within a PR is still caught; `staged` scans staged changes before committing (documented instead of a pre-commit hook framework). Output is redacted.
* **Workflow:** `static-analysis-secrets.yml`, required job `Static Analysis (secrets)`, with no detect-changes job: a secret can be in any file, so it runs on every PR.
* **Existing findings (28 in history), all reviewed:**
  * `.gitleaks.toml` allowlists, each narrowed to a rule and path or an exact value: the base64 `api:` frontmatter in docs generated by docusaurus-plugin-openapi-docs (`generic-api-key` in `*.api.mdx`); example JWTs from the GA4GH DRS OpenAPI spec and the request schemas generated from it; and the local/minikube-only Hydra system secret, matched by exact value (confirmed never used outside local and minikube), so a different secret in the same files is still caught.
  * `.gitleaksignore` pins by fingerprint the simulated GA4GH Starter Kit `visaSecret` values in a doc that has since been removed (confirmed not real).
* Verified: a staged GitHub-format token, a changed `system-secret` in `scripts/lib/common.sh`, and a correctly formatted AWS key committed and then removed are all caught.

### Task 8: Dependency audit for admin-cli

* Ruff's `S` rules only check admin-cli's own code. Add a dependency CVE audit of `uv.lock` (e.g. `pip-audit` via a pinned `uvx`, or `trivy fs`) to `static-analysis-admin-cli.yml`, failing at high and above like the other apps, with a weekly run.

**Decisions (Task 8):**

* **Importance:** medium-low (an operator CLI with 19 locked packages), but the first run found real issues: `urllib3` 2.7.0 (transitive, via `boto3` → `botocore`) had 2 high and 1 medium CVE. Fixed by `uv lock --upgrade-package urllib3` (2.8.0, lock-only change).
* **Tool: `trivy fs`** (0.76.2, the same image and digest as `scripts/refcloud-static-analysis-deploy-local.sh`; each script notes the other). It reads `uv.lock` natively and is the only option that fails at HIGH and above (`--severity HIGH,CRITICAL --exit-code 1`). Not `uv audit` (experimental in uv 0.11.24 and no severity threshold) or `pip-audit` (no `uv.lock` support without `uv export`, no severity threshold).
* **Scope:** runtime and dev dependencies (`--include-dev-deps`, 20 packages), like the Node apps' audits. Vulnerabilities fail whether or not a fix exists, like `npm audit`; an unfixable one can be accepted in `apps/admin-cli/.trivyignore` with a reason.
* **Command:** `apps/admin-cli/scripts/audit-deps.sh` (bash, needs only Docker), run from `apps/admin-cli` like the `uv run` checks. It mounts only `uv.lock` and `.trivyignore` read-only and keeps Trivy's DB in `~/.cache/trivy`, so nothing is written to the repo. The Dockerfile copies only named files, so the script isn't in the image. Documented in the admin-cli `README.md` and `CLAUDE.md`; Dependabot can't bump the pin (noted in `dependabot.yml`).
* **Workflow:** a new step in `static-analysis-admin-cli.yml` (same required job, `Static Analysis (admin-cli)`) that runs even if `uv sync` fails. The workflow now also runs weekly (Mondays 06:00 UTC) and on `workflow_dispatch`, checking everything regardless of changes, like Tasks 2 and 3.
* Verified: passes on the fixed lock; fails (exit 1) on the previous lock with the two high `urllib3` CVEs; passes again with those IDs in `.trivyignore`.

### Task 9: Static analysis for the Helm chart

* Scan `deploy/helm` with `trivy config` (already flags `KSV-0109`: Kratos and Hydra secrets in ConfigMaps), plus `helm lint` and template rendering checks, in its own workflow following the same conventions.
* Note: `values.yaml` defines `livenessProbe`/`readinessProbe`, but no template uses them, so no deployment has probes. Wire them up (or remove the unused values).

**Decisions (Task 9):**

* **Importance:** medium. Locally the chart only runs in minikube, but it is built for hosting (ECR values, gitignored staging/prod values). The probe gap was the most practical issue: rollouts sent traffic to pods before they were ready, and a hung pod was never restarted.
* **Checks:** `scripts/refcloud-static-analysis-helm.sh` (bash, pinned images: `alpine/helm` 4.3.1, kubeconform 0.8.0, Trivy 0.76.2), called by `static-analysis-helm.yml` (required job `Static Analysis (helm)`, detect-changes on `deploy/helm/**`, PRs and `workflow_dispatch`, no weekly run since nothing it checks changes without a commit):
  * `lint`: `helm lint --strict`.
  * `kubeconform`: `helm template` output validated in strict mode against the Kubernetes 1.35.1 schemas (minikube's version), including the hook Jobs.
  * `trivy-config`: HIGH/CRITICAL fail. Trivy's chart mode skips Helm hooks, so the migration Jobs are also scanned from the rendered output.
  * Rendered with the default (local) values only; the remote (AWS) rendering is left out for now.
* **KSV-0109** (ConfigMap with secrets) is a false positive: the Kratos and Hydra ConfigMaps hold only `<SECRET>` placeholders, overridden by env vars from Kubernetes Secrets. Accepted in `deploy/helm/.trivyignore.yaml`, scoped to those two files with a reason (verified that an entry with a wrong path no longer matches).
* **Security contexts (fixes KSV-0014 and KSV-0118, and most mediums):** every pod and container, Jobs included, uses `refcloud.podSecurityContext` / `refcloud.containerSecurityContext` (`_helpers.tpl`): `runAsNonRoot`, explicit `runAsUser` matching the image (required for images with a named `USER`), RuntimeDefault seccomp, no privilege escalation, all capabilities dropped, read-only root filesystem with an `emptyDir` at `/tmp` (and researcher-ui's `/app/.next/cache`). The core-api image now creates its user with fixed id 10001 (`USER 10001`); researcher-ui and Liquibase use `USER 1001` (same ids as before, now numeric).
* **Probes** (defined per template; the unused `values.yaml` probes are removed):
  * core-api: `/actuator/health/liveness` and `/actuator/health/readiness` (option A). Both paths are added to `PUBLIC_ENDPOINTS` in `SecurityConfig` (GET only, status without details; the ingress doesn't route `/actuator`), and `management.endpoint.health.probes.enabled` is set explicitly so they also exist outside Kubernetes. A startup probe allows up to 3 minutes before liveness checks begin.
  * Kratos: `/admin/health/alive` and `/admin/health/ready` on 4434 (Kratos 0.9 serves its admin API under `/admin`; the unprefixed paths only redirect). Hydra: `/health/alive` and `/health/ready` on 4445. Admin ports are never routed by the ingress.
  * docs: `/` on 8080. researcher-ui: TCP on 3000 (its pages call Kratos and Hydra, so an HTTP check could flap). admin-cli: none (`sleep infinity`).
  * The `.trivyignore` reason for `DS-0026` (no Dockerfile `HEALTHCHECK`) now points to the chart's probes.
* **Chart metadata:** removed the boilerplate `appVersion: "1.16.0"` (the deployed version is the lockstep image tag). No `icon`: the repo has no logo yet (`helm lint` reports it as INFO only).

### Task 10: Dependency audit for core-api

* core-api's Gradle dependencies have no vulnerability audit: OWASP Dependency-Check was deferred in Task 4 (it needs an NVD API key, which fork and Dependabot PRs can't use), and Dependabot alerts may not see Gradle dependencies without a dependency submission from CI. `software.amazon.awssdk:s3` is pinned at 2.25.15 (2024), so the first run will probably find something.
* Add a dependency CVE audit to `static-analysis-core-api.yml`, failing at HIGH and above like the other apps, with a weekly run (Mondays 06:00 UTC) and `workflow_dispatch`, checking everything on those runs.
* Proposed approach: Gradle **dependency locking** (`gradle.lockfile`, committed, refreshed with `gradle dependencies --write-locks`) scanned with **`trivy fs`**, the same image and pattern as admin-cli (`uv.lock`): no build, no NVD key, and a lockfile also makes builds reproducible. Exceptions go in `apps/core-api/.trivyignore` with a reason.
* Alternatives to weigh:
  * Trivy against the built `bootJar`: no lockfile to maintain, but needs a build first and Trivy's Java database (several hundred MB, to be cached like the vulnerability db).
  * GitHub dependency submission (`gradle/actions/dependency-submission`): feeds Dependabot alerts, but doesn't block PRs, so it complements rather than replaces a CI audit.
* Open questions: lockfile (proposed) or built jar? Which configurations to lock and audit (runtime and compile classpaths only, or test and tooling too, like the other apps' dev dependencies)? Fix existing findings in this task?

### Task 11: Cache Trivy's databases in CI

* Implements the objective's caching for the Trivy checks (deploy-local, admin-cli and helm workflows), which otherwise downloaded Trivy's data on every run.

**Decisions (Task 11):**

* `actions/cache` (v6.1.0, SHA-pinned) caches `~/.cache/trivy` subdirectories, keyed by day with a prefix `restore-keys` fallback; Trivy refreshes stale data itself, so restoring an older entry is safe.
* Two keys, because each scan type downloads only what it uses: `trivy-checks-*` (the misconfiguration checks bundle, `~/.cache/trivy/policy`) for the `trivy config` steps in deploy-local and helm, and `trivy-db-*` (the vulnerability database, `~/.cache/trivy/db`, about 99 MB compressed) for admin-cli's audit. With one shared key, a config-only job could save the day's entry without the database, and the audit could never cache it.
* The weekly image CVE matrix only restores the database (`actions/cache/restore`): its jobs run in parallel and would race to save the same key. Image layer caches are never saved.
* The scripts now run Trivy as the calling user (`--user $(id -u):$(id -g)`, cache mounted at `/cache`), so cached files are owned by the runner user and `actions/cache` can save them (it can't read root-owned files). Verified locally: all Trivy checks pass with a fresh cache, and every cached file is owned by the user.
* GitHub cache scoping: an entry saved on a PR branch is only reused by that PR (and its reruns); entries saved by the scheduled runs on `main` are reused by every branch. So the main benefit is fewer downloads and less exposure to registry rate limits, not a faster first run on each PR.

## Manual Actions

GitHub settings to apply after this branch's PR is opened (some depend on its checks having run once).

**Settings → Rules → Rulesets → New branch ruleset**

* Target `develop`, and `main` if releases reach it by PR. Enforcement: **Active**.
* **Restrict deletions** and **Block force pushes**.
* **Require a pull request before merging:** 1 approval (0 for a solo maintainer, since GitHub doesn't let you approve your own PR), dismiss stale approvals on new commits, require conversation resolution.
* **Require status checks to pass**, each with source **GitHub Actions** (so they can't be spoofed):
  * `Static Analysis (admin-cli)`
  * `Static Analysis (researcher-ui)`
  * `Static Analysis (docs)`
  * `Static Analysis (core-api)`
  * `Static Analysis (deploy-local)`
  * `Static Analysis (helm)`
  * `Static Analysis (workflows)`
  * `Static Analysis (secrets)`
* A check appears in the picker only after it has run, so add them once the PR's workflows have run. A check skipped because nothing it covers changed counts as passing (intended).
* Leave "Require branches to be up to date before merging" off: each check covers only its own paths, so it mostly forces rebases.
* Don't require code scanning results: the weekly image CVE scan is deliberately non-blocking.

**Settings → Actions → General**

* Allowed actions: enable "Require actions to be pinned to a full-length commit SHA" (enforces Task 6's convention).
* Workflow permissions: default `GITHUB_TOKEN` to "Read repository contents"; leave "Allow GitHub Actions to create and approve pull requests" off.
* Fork pull request workflows: require approval for outside contributors.

**Settings → Advanced Security**

* Secret scanning and push protection: done (Task 7).
* Enable the dependency graph, Dependabot alerts and code scanning (it receives the weekly image CVE SARIF uploads).
* Dependabot security updates: optional (PRs only for vulnerable dependencies, so few).

**Settings → General**

* Optional: "Automatically delete head branches".

**Other one-time steps**

* After the first merge to `develop`, confirm all five `push-docker-image-*` runs succeed (Task 6 changed how the reusable workflow receives its AWS secrets).
* The scheduled workflows and `dependabot.yml` run from `main` only, so they start once this work reaches `main`.
* Check **Insights → Dependency graph** for Gradle packages, to see whether Dependabot alerts cover core-api (relevant to Task 10).

## Future Directions

Follow-ups outside this spec's scope:

* **Build checks on PRs:** `next build`, the Docusaurus build and the Docker image builds only run after merge (image pushes to `develop`), so a broken build or Dockerfile is found late. Not static analysis, so a separate spec.
* **Kratos upgrade** from `v0.9.0-alpha.2` (132 HIGH/CRITICAL CVEs on an EOL Alpine): separate ticket, kept in sync with the Helm chart. Check the probe paths (`/admin/health/*`) still apply.
* **docs audit exceptions:** remove the `@faker-js/faker` allowlist entry in `apps/docs/audit-ci.jsonc` and the npm `overrides` once upstream ships fixes.
* **Test commands** are still TBD across the apps (Task 1 onward).
* **Helm chart rendered for AWS:** static analysis currently renders only the default (local) values (Task 9).
* **Chart icon:** add `icon` to `Chart.yaml` once the project has a logo.
* **admin-cli pod shutdown:** the pod takes the full 30-second grace period to stop on every redeploy, because `sleep infinity` (PID 1) ignores SIGTERM. Pre-existing; fix with `terminationGracePeriodSeconds: 5` or a shell that traps SIGTERM.
* **docs pod IPv6:** with the read-only root filesystem, the nginx-unprivileged entrypoint can't enable its IPv6 listener (logged as info). Harmless in an IPv4 cluster; mount a writable `conf.d` or add the listener to the image's config if IPv6 is needed.
* **Trivy cache on Linux:** caches created before Task 11 by root-run Trivy containers may be root-owned on a Linux host; delete `~/.cache/trivy` if a local run fails with permission errors (not an issue on macOS Docker Desktop or fresh CI runners).
