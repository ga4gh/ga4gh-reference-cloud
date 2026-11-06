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
* **OWASP Dependency-Check: deferred.** It needs an NVD API key stored as a secret, which isn't available to fork or Dependabot PRs, and a cached NVD database. Revisit separately (Dependabot alerts cover Gradle dependencies in the meantime).
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

