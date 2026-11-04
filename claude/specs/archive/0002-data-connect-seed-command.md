# Feature Spec: Implement Data Connect 'Seed' Command and apply to existing datasets

**Branch:** `ja-iss-29-create-data-connect-seed-content`

**Linked GitHub Issue:** [Issue 29](https://github.com/ga4gh/ga4gh-reference-cloud/issues/29)

## Objective

Seed the refcloud database with table rows that are necessary to enable data connect functionality. In particular, add rows to the `data_connect_table_type` and `data_connect_table` tables to allow data connect queries.

## Context

* Current Refcloud liquibase database schema: [Link](https://raw.githubusercontent.com/ga4gh/refcloud-api/refs/heads/ja-iss-28-implement-data-connect-v1/liquibase/dbchangelog.xml)

## Engineering Tasks

### Task 1: Create a `dataset reset` command

* The current command `dataset delete` deletes the dataset and all cascading objects, including all passport information (i.e. what users were granted access to this dataset).
* We need to create a **reset** command that scrubs all data (files, tables) from the dataset but preserves the dataset object itself and the authorized user information.
* This will allow us to reformat the available data in a dataset while preserving researcher auth information so that researchers don't have to reapply to the same dataset.

#### Design Decisions

**Scope of the reset** (confirmed against the liquibase schema linked above)

| Deleted (data) | Preserved (auth & dataset metadata) |
|---|---|
| `drs_object` rows where `dataset_id` matches | `dataset` |
| ↳ cascades to `drs_object_alias`, `drs_object_checksum`, `aws_s3_access_object`, `refget_sequence` → `refget_sequence_alias` | `passport_visa` |
| `data_connect_table` rows where `dataset_id` matches | `passport_user_visa_assertion` (researcher grants; cascades from `passport_visa`, which is why `dataset delete` destroys them) |
| | `passport_user`, `tag`, `dataset_tag` |

* Only two explicit `DELETE` statements are needed (`drs_object`, `data_connect_table`); everything else is removed by `ON DELETE CASCADE`.
* `data_connect_table_type` rows are platform-wide seed content, not dataset data, and are not touched.
* Reset does not refresh dataset metadata (name, description, tags, visa) from `SUPPORTED_DATASETS`.

**Behaviour**

* All deletes run in a single transaction, so a failed reset leaves the dataset unchanged. This differs from the rest of the CLI, where each insert commits individually.
* The command asks for confirmation before deleting. A `--yes` / `-y` flag skips the prompt for scripted use.
* Reset only clears data; it does not re-onboard. The operator runs `dataset onboard` afterwards.
* Like `dataset delete`, reset requires the dataset to exist in the database but does not require it to be in `SUPPORTED_DATASETS`.
* Deletion logic lives in a `DatabaseEngine` method; the CLI command and handler follow the existing `dataset delete` pattern.

**Recovery workflow**

* The recommended recovery from a failed or outdated onboarding run becomes `dataset reset` → `dataset onboard`, replacing `dataset delete` → `dataset init` → `dataset onboard`, which loses researcher grants. CLAUDE.md is updated to match.

**Dependency**

* `DatabaseEngine` reflects the schema at startup and now needs the `data_connect_table` table, which is added on the `refcloud-api` branch `ja-iss-28-implement-data-connect-v1`. The target database must have that migration applied. This requirement applies to every command (`init`, `onboard`, `delete`, `reset`), not just `reset`; this was an explicit decision, as later tasks in this issue depend on the data connect tables anyway.

### Task 2: Create stub for `seed` command

* Create a CLI stub method `seed` that sits beside the `dataset` group (do not nest under `dataset`)
* Create a helpful docstring for the method that will show up in the terminal
* For now, simply get the method to print a message. We will implement the method in the next task

### Task 3: Begin implementation of the `seed` command - adding data connect table types

* Begin implementation of the method invoked by 'refcloud seed'
* Create a method to add/update a row to the `data_connect_table_type`. It should add: `id`, `description`, `data_model_template`, `internal_query`, `created_at`, `updated_at`
* Run this method 3 times for the following 3 table types:
  1. datasets
  2. drs_object
  3. refget_sequence
* you will see 3 subdirectories under `src/refcloud/resources/seed/data-connect/table-types/`, each one corresponding to one of the expected table types. for each subdirectory:
  * load `id` and `description` from `table-type.json`
  * load `data_model_template` from `data-model.json`
  * load `internal_query` from `internal-query.sql`
  * set `created_at` and `updated_at` to the same value, based on the current time

#### Design Decisions

**Upsert semantics**

* Rows are written with `INSERT ... ON CONFLICT (id) DO UPDATE`, so `refcloud seed` is safe to re-run.
* On insert, `created_at` and `updated_at` are both set to the same current timestamp.
* On update, `description`, `data_model_template`, `internal_query`, and `updated_at` are overwritten; `created_at` is kept so it records when the type was first seeded.

**Table type discovery**

* Table types are discovered automatically: every subdirectory of `resources/seed/data-connect/table-types/` is one table type. Adding a new type requires only a new directory, not a code change.
* Each directory name must match the `id` in its `table-type.json`. On a mismatch, seed exits with an error before writing anything (guards against copy/paste mistakes).

**File handling**

* Resource files are read via `importlib.resources` (not paths relative to `__file__`), so they resolve whether the CLI runs from source, as an installed uv tool, or in Docker.
* `data-model.json` is parsed as JSON and stored as `jsonb`, so a malformed file fails loudly. Postgres `jsonb` does not preserve key order or whitespace; "verbatim" (per the seed README) means `{{...}}` placeholders are not substituted, which holds.
* `internal-query.sql` is stored as-is in a `text` column.

**Transactions & code layout**

* All table types are upserted in a single transaction; a failure leaves the table unchanged.
* The single-row upsert is `DatabaseEngine._upsert_data_connect_table_type(conn, ...)`, wrapped by the public `upsert_data_connect_table_types(table_types)`, which owns the transaction.
* File loading and discovery live in `handlers/seed/__init__.py`.
* Output follows CLI conventions: one `seeded table type: <id>` line per type, then a `done:` line.

**Out of scope**

* Seeding `tables/datasets.json` into `data_connect_table` is left for a later task.

### Task 4: Add the `datasets` table to the seed command

* There is one `data_connect_table` row that is independent of any particular dataset, the `datasets` table. It should be added to `data_connect_table` as part of the `seed` command.
* Create a method to add/update a row to the `data_connect_table` table. It should add: `table_name`, `dataset_id`, `table_type`, `description`, `created_at`, `updated_at`
* Run the method for the `datasets` table using the information in: `src/refcloud/resources/seed/data-connect/tables/datasets.json`
* NOTE: this method will be run in `dataset onboard` for dataset-specific tables, but that is out of scope for this particular task.

#### Design Decisions

**Upsert semantics**

* Rows are written with `INSERT ... ON CONFLICT (table_name) DO UPDATE` (`table_name` is the primary key).
* On update, `dataset_id`, `table_type`, `description`, and `updated_at` are overwritten; `created_at` is kept, consistent with Task 3.

**Platform table discovery**

* `datasets.json` moved from `tables/` to `tables/platform/`. `refcloud seed` loads every `*.json` file in `tables/platform/` only, leaving room for dataset-specific table definitions elsewhere under `tables/` without `seed` picking them up.
* Every file in `tables/platform/` must have `"dataset_id": null`; seed exits with an error otherwise, so it can never create a dataset-scoped row by mistake.
* The seed README layout was updated to match.

**Ordering & transactions**

* All seed files (table types and platform tables) are loaded and validated before anything is written, so a bad file aborts the run with no writes.
* Table types are written first (`data_connect_table.table_type` is a foreign key to `data_connect_table_type.id`), in their own transaction; platform tables follow in a second transaction. If the second fails, the committed table types are harmless since re-running seed overwrites them. A single transaction across both was considered unnecessary.

**Code layout & reuse**

* The single-row upsert is `DatabaseEngine._upsert_data_connect_table(conn, ...)`, wrapped by the public `upsert_data_connect_tables(tables)`, which owns the transaction (same pattern as Task 3). `dataset onboard` will call `upsert_data_connect_tables` for its dataset-specific rows in a later task.

**Known follow-up**

* `data_connect_table.table_name` has a check constraint requiring dot-separated lowercase identifiers (`^[a-z_][a-z0-9_]*(\.[a-z_][a-z0-9_]*)*$`). Current dataset ids such as `aws.opendata.registry.1000-genomes` do not satisfy it (hyphen, segment starting with a digit), so dataset-specific table names will need a naming rule. To be addressed in a later task on this branch.

### Task 5: change datasets to use snake case instead of dot-notation to satisfy data connect spec

* as noted above, dataset names include dot '.' characters which breaks the data connect table naming convention
* migrate the code so that it will create datasets with underscores in the name instead of dots

#### Design Decisions

**New ids**

* Both dots **and hyphens** are replaced with underscores. Dots alone are not the problem (the `table_name` check allows dot-separated segments); the hyphen in `1000-genomes` is, and replacing only dots would leave `aws_opendata_registry_1000-genomes`, which still fails.
  * `aws.opendata.registry.1000-genomes` → `aws_opendata_registry_1000_genomes`
  * `ga4gh.refget.sequences.v2.conformance` → `ga4gh_refget_sequences_v2_conformance`
* Visa ids follow the dataset id exactly, with the prefix also snake-cased: `ga4gh-visa:aws.opendata.registry.1000-genomes` → `ga4gh_visa:aws_opendata_registry_1000_genomes`. This gives a direct mapping `visa id = ga4gh_visa:{dataset id}`.
* Dataset names, descriptions, and drs object ids are unchanged.

**Per-dataset table naming (for a later task)**

* Dataset-specific Data Connect tables will be named `refcloud.{dataset_id}.{table_type}`, e.g. `refcloud.aws_opendata_registry_1000_genomes.drs_object`. Each dataset id is therefore a single segment and must match `^[a-z_][a-z0-9_]*$`.

**Configuration guard**

* `validate_supported_datasets()` in `config/constants.py` checks every `SUPPORTED_DATASETS` entry:
  * the key matches `^[a-z_][a-z0-9_]*$`
  * `dataset.id` equals the key
  * if a visa is configured, its id equals `ga4gh_visa:{key}`
* `dataset init` and `dataset onboard` run the check first and exit, listing every error, before touching the database. All entries are checked (not just the requested one) so a bad entry is caught as early as possible.

**Migrating existing databases in place**

* `migrations/0001-snake-case-dataset-ids.sql` renames existing datasets and visas without losing data or researcher grants. It is intended for prod; local dev can use it too, or delete and re-init.
* Foreign keys referencing `dataset.id` and `passport_visa.id` are `ON UPDATE NO ACTION` and not deferrable, so ids cannot be updated directly. For each dataset, the script:
  1. inserts a copy of the `dataset` row under the new id
  2. if a visa exists: temporarily suffixes the old visa's `name`/`description` (both unique), inserts the replacement visa under `ga4gh_visa:{new id}`, re-points `passport_user_visa_assertion.visa_id`, and deletes the old visa
  3. re-points `dataset_tag`, `drs_object`, and `data_connect_table` to the new id (drs sub-objects reference `drs_object.id`, which does not change)
  4. deletes the old `dataset` row, which has no remaining children, so nothing cascades
* The script is a single `DO` block (atomic) and skips datasets not found under their old id, so it is safe to re-run.
* Verified against a throwaway Postgres 18 database with the relevant schema rebuilt from the liquibase changelog: all rows moved, grants preserved with their statuses, unrelated datasets untouched, and a second run was a no-op.
* `migrations/` lives at the repo root rather than under `src/refcloud/resources/`, since it is an operational script and not used by the CLI at runtime.

### Task 6: Add rows to `data_connect_table` during `dataset onboard`

* dataset-specific data connect tables should be added as rows to the `data_connect_table`
* configuration can go in the `SUPPORTED_DATASETS` object in `constants.py`
* for the 1000 genomes dataset, just a `drs_object` table is required
* for the refget conformance dataset, both a `drs_object` table and a `refget_sequence` table are required

#### Design Decisions

**Configuration**

* Each `SUPPORTED_DATASETS` entry has a `data_connect_tables` list of `{"table_type", "description"}`. Configuration stays in `constants.py` (not `resources/seed/data-connect/tables/`), as that is where all dataset-specific config lives.
* Descriptions are written explicitly per table, not generated.
* `table_name` and `dataset_id` are never configured; they are derived from the dataset id and table type, so they cannot drift. `table_name` is built by `data_connect_table_name()` as `refcloud.{dataset_id}.{table_type}`.
* 1000 Genomes: `drs_object`. Refget conformance: `drs_object`, `refget_sequence`.

**Onboarding**

* Rows are written after all DRS objects have been loaded, so a table is never registered against partially loaded data.
* Rows are written with `upsert_data_connect_tables` (Task 4), so re-running is safe. `dataset reset` (Task 1) deletes them, and the next `dataset onboard` recreates them.

**Seed is required before any dataset work**

* `require_database_seeded()` (in `handlers/seed/`) exits with an error unless every table type under `resources/seed/data-connect/table-types/` and every platform table under `tables/platform/` exists in the database. It lists what is missing and tells the operator to run `refcloud seed`.
* `dataset init` and `dataset onboard` both run it before writing anything. This catches a missing seed up front, rather than as a foreign key error after every DRS object has been loaded.
* It checks that seed rows are present, not that they match the current seed files; re-running `refcloud seed` after changing seed files remains the operator's responsibility.

**Configuration guard additions**

* `validate_supported_datasets()` also checks, without a database, that each configured `table_type` is one of the seed table types, and that no table type appears more than once for a dataset (`data_connect_table` has a unique `(dataset_id, table_type)` constraint).

**Code layout**

* Seed file discovery (`DATA_CONNECT_SEED_DIR`, `list_table_type_dirs()`, `list_platform_table_files()`) moved to `config/seed_resources.py`, so both `config/constants.py` and `handlers/seed/` can use it without `config` importing from `handlers`.
* `DatabaseEngine` gained `get_data_connect_table_type_ids()` and `get_data_connect_table_names()` for the seed check.