# Project Guide: GA4GH Reference Cloud Admin CLI

## Project Overview & Architecture

This repo serves as a commandline interface (CLI) utility to help with initial preparation of the refcloud database as well as data onboarding.

* **Tech Stack:** Python, UV, PostgreSQL, sqlalchemy

### How It Works

* The database schema is owned and migrated by the `refcloud-api` backend repo. This CLI does not create tables; `DatabaseEngine` reflects the existing schema at startup, so backend migrations must have been applied first.
* Database connection is configured via `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (see `config/env_config.py` for local defaults).
* Supported datasets are defined in `SUPPORTED_DATASETS` (`config/constants.py`). Adding a dataset means adding an entry there plus a custom onboarding function in `custom_onboarding/` that yields `drs_object_set` dicts (see existing onboarders for the shape). Dataset ids must be lowercase snake case (`^[a-z_][a-z0-9_]*$`) and visa ids must be `ga4gh_visa:{dataset_id}`; `validate_supported_datasets()` enforces this. Each dataset's Data Connect tables are listed in its `data_connect_tables` entry; table names are derived as `refcloud.{dataset_id}.{table_type}`.
* Onboarding reads from public S3 buckets using unsigned requests.
* `resources/seed/data-connect/` holds Data Connect seed content (table types, data models, internal queries). Its README defines the contract with `refcloud-api`; keep the two in sync.

### Directory Layout

* `src/refcloud` - all source code and resources
* `src/refcloud/resources` - non-python resource files (e.g. JSON files, SQL templates)

---

## Core Commands

### Development, Build, & Run

* Build project: `uv sync`
* Run CLI: `uv run refcloud`
* Seed platform-wide data (required before any `dataset init` / `dataset onboard`): `uv run refcloud seed`
* Initialize and empty reference cloud dataset: `uv run refcloud dataset init -d {DATASET_ID}`
* Onboard reference data to a dataset: `uv run refcloud dataset onboard -d {DATASET_ID}`
* Delete an existing dataset: `uv run refcloud dataset delete -d {DATASET_ID}`
* Reset a dataset (delete all data, preserve dataset and passport visas): `uv run refcloud dataset reset -d {DATASET_ID}`

### Code Quality & Validation

Ruff and Pyright are pinned in the `dev` dependency group (`uv.lock`), so local runs match CI (`.github/workflows/static-analysis-admin-cli.yml`). Config lives in `pyproject.toml`.

* Check formatting: `uv run ruff format --check`
* Run linter (includes the Bandit `S` security rules): `uv run ruff check`
* Run type checks (Pyright, standard mode): `uv run pyright`
* Audit `uv.lock` (runtime and dev dependencies) for HIGH/CRITICAL CVEs (Trivy, pinned Docker image; needs only Docker): `./scripts/audit-deps.sh`. Fix findings with `uv lock --upgrade-package <name>`; only add an ID to `.trivyignore` when no usable fix exists, with a reason comment.
* Fix formatting and auto-fixable lint errors: `uv run ruff format && uv run ruff check --fix`
* CI fails if any of these checks fails (the audit also runs weekly, so a new advisory can fail CI without code changes). Don't silence a rule with `# noqa` or `# pyright: ignore` without a comment explaining why; prefer a real fix (e.g. `hashlib.md5(..., usedforsecurity=False)` for checksums).

### Testing

* TBD: Run all tests
* TBD: Run specific test file

---

## ⚠️ Behavioral Guardrails & Rules

* Onboarding is not transactional or idempotent: each insert commits individually and re-running hits duplicate keys. To recover from a failed run, `dataset reset`, then `dataset onboard`. Avoid `dataset delete` for recovery: it cascades to `passport_visa` and destroys researcher access grants.
* Never run `dataset delete`, `dataset reset`, or `dataset onboard` against a non-local database without explicit confirmation.
* The CLI requires the `refcloud-api` data connect migration (`data_connect_table`) to be applied to the target database.
