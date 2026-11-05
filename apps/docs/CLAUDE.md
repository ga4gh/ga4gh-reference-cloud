# Project Guide: GA4GH Reference Cloud Docs

## Project Overview & Architecture

This repo serves as the documentation site for the refcloud

* **Tech Stack:** Docusaurus, React, TypeScript

### Directory Layout

* `docs/` - Main documentation folder
* `openapi/` - OpenAPI definitions that are used to build swagger pages
* `src/` - React content like components and pages
* `static/` - reusable web/art assets

---

## Core Commands

### Development & Build

* Start local dev server: `npm run start`

### Code Quality & Validation

All tools are pinned (`package-lock.json`, and the semgrep version in `package.json`), so local runs match CI (`.github/workflows/static-analysis-docs.yml`, which also runs weekly). Each command fails on any problem, warnings included.

* Check formatting (Prettier): `npm run format:check`
* Run linter (ESLint: `@docusaurus/eslint-plugin`, typescript-eslint, react, react-hooks): `npm run lint`
* Run type check: `npm run typecheck`
* Audit dependencies (`audit-ci`, fails at high severity and above, dev dependencies included): `npm run audit:deps`
* Run security rules (Semgrep, via `uvx`, so uv must be installed): `npm run semgrep`
* Fix formatting and auto-fixable lint errors: `npm run format && npm run lint:fix`
* Prettier and ESLint skip Markdown/MDX content and the generated `docs/api-reference/` (regenerated from `openapi/` by `docusaurus-plugin-openapi-docs`).
* **Audit exceptions:** `audit-ci.jsonc` allowlists advisories that have no compatible fix, each with a reason. Remove an entry as soon as upstream ships a fix; never allowlist without a reason.
* **Dependency overrides:** `overrides` in `package.json` forces patched versions of transitive dependencies Docusaurus and the OpenAPI plugin haven't updated yet (`tinypool`, `serialize-javascript`, and `js-yaml` under `openapi-to-postmanv2`). After bumping Docusaurus or the OpenAPI plugins, check whether each override is still needed, and confirm `npm run build` still succeeds.

### Testing

* TBD: Run all tests
* TBD: Run specific test file
