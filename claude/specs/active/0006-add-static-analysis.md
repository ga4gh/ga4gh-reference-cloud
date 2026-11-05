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
