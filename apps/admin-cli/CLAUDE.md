# Project Guide: GA4GH Reference Cloud CLI

## Project Overview & Architecture

The GA4GH Reference Cloud (Refcloud) is a reference implementation of multiple API specifications from the [Global Alliance for Genomics and Health](https://ga4gh.org). The Refcloud is designed to provide a live model of GA4GH standards in action, demonstrating how they work as running applications, and elucidating how various models and concepts can be connected across different GA4GH Work Streams.

This repo serves as a commandline interface (CLI) utility to help with initial preparation of the refcloud database as well as data onboarding. There are separate repos for the backend, frontend, helm chart, etc.

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
* `migrations` - one-off SQL scripts for fixing existing databases in place (run manually with `psql`, not by the CLI)

---

## 🛠️ Core Commands

### Development, Build, & Run

* Build project: `uv sync`
* Run CLI: `uv run refcloud`
* Seed platform-wide data (required before any `dataset init` / `dataset onboard`): `uv run refcloud seed`
* Initialize and empty reference cloud dataset: `uv run refcloud dataset init -d {DATASET_ID}`
* Onboard reference data to a dataset: `uv run refcloud dataset onboard -d {DATASET_ID}`
* Delete an existing dataset: `uv run refcloud dataset delete -d {DATASET_ID}`
* Reset a dataset (delete all data, preserve dataset and passport visas): `uv run refcloud dataset reset -d {DATASET_ID}`

### Code Quality & Validation

* TBD: Run linter
* TBD: Fix linting/formatting errors
* TBD: Run type checks

### Testing

* TBD: Run all tests
* TBD: Run specific test file

---

## 🎨 Code Style & Preferences

---

## 🔀 Git Workflow

* Feature branches open PRs against `develop`. `develop` is merged into `main` only for releases.
* Pushes to `develop` build and push the Docker image to AWS ECR (`.github/workflows/ecr.yml`).

---

## ⚠️ Behavioral Guardrails & Rules

* Onboarding is not transactional or idempotent: each insert commits individually and re-running hits duplicate keys. To recover from a failed run, `dataset reset`, then `dataset onboard`. Avoid `dataset delete` for recovery: it cascades to `passport_visa` and destroys researcher access grants.
* Never run `dataset delete`, `dataset reset`, or `dataset onboard` against a non-local database without explicit confirmation.
* The CLI requires the `refcloud-api` data connect migration (`data_connect_table`) to be applied to the target database.