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