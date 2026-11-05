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
* The check is designed to be made a **required status check**: there is no workflow-level `paths` filter (a path-filtered workflow that never runs blocks required checks forever). Instead a `detect-changes` job (`dorny/paths-filter`) always runs, and the `Static Analysis (admin-cli)` job is skipped when nothing under `apps/admin-cli/**` (or the workflow file) changed; GitHub counts a skipped job as passing. Make `Static Analysis (admin-cli)` the required check.
* Caching: `astral-sh/setup-uv` caches the uv package cache keyed on `uv.lock`. The Ruff and Pyright runs take seconds, so their caches aren't persisted. The check steps all run even if one fails, so a single run reports every problem.
* Testing commands remain TBD (handled in a later task).
