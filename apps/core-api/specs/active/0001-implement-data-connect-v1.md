# Feature Spec: Implement Data Connect v1.0

**Branch:** `ja-iss-28-implement-data-connect-v1`

**Linked GitHub Issue:** [Issue 28](https://github.com/ga4gh/ga4gh-reference-cloud/issues/28)

## Objective

Implement the GA4GH Data Connect Specification v1.0 within the Refcloud. The Data Connect implementation will provide searchable tables to data types that are already registered in the database/data model. So far, 3 searchable data type tables are planned:
1. DRS Objects (on a per dataset basis)
2. Refget Sequences (on a per dataset basis)
3. Datasets (all datasets platform-wide, not tied to a particular dataset)

## Context

Use the Data Connect Specification on GitHub (develop branch) to implement the standard. Resources include:
* [specification preamble](https://github.com/ga4gh/data-connect/tree/develop)
* [human/agent readable specification in Markdown](https://github.com/ga4gh/data-connect/blob/develop/SPEC.md)
* [OpenAPI specification](https://github.com/ga4gh/data-connect/blob/develop/spec/api.yaml)

## High-Level Tasks:

* Implement all base functionality of the Data Connect API under /ga4gh/data-connect/v1/, including the following endpoints:
  * `/tables`
  * `/tables/{id}/info`
  * `/tables/{id}/data`
  * `/search`
  * `/service-info`
* The solution should be fully integrated with the current reference cloud data model. This means that when a dataset contains records of a certain data type, a searchable table is created for that data type. For example:
  * If a dataset contains Drs Objects, create a searchable "Data Connect on DRS" table
  * If a dataset contains Refget Sequences, create a searchable "Data Connect on Refget" table
* A Data Connect table has the same level of access control as the dataset it belongs to. If a dataset requires a visa, then the underlying tables all require the same visa. Tables belonging to an open dataset do not require a visa
* There should also be an open "datasets" Data Connect Table, which lists all datasets available on the platform. This table will be open access and not tied to a particular dataset.

## Engineering Tasks

### Task 1: Add Base Packages and Stub Controllers

* create a package structure in the application code to hold data connect-related classes
* create one or more controller classes that will hold all Data Connect controller methods
* create a stub method for all controller methods:
  * `/tables`
  * `/tables/{id}/info`
  * `/tables/{id}/data`
  * `/search`
  * `/service-info`
* at this point, each controller method can simply return a string that says "you have reached the {endpoint} endpoint"

### Task 2: Implement Service Info

* Implement the Data Connect service info endpoint based off of what was done for DRS and Refget. Use a config class that imports custom config from application.yml and presents it via the controller. The Data Connect service info details should be independently customizable from all other web services.

### Task 3: Implement underlying data model in liquibase and prepare for dynamic query engine

* Add database migrations to liquibase dbchangelog
* Add a `data_connect_table` table with the following properties:
  * `dataset_id` - the dataset to which the table belongs (foreign key that links it to a dataset)
  * `table_name` - the table name (a composite of `dataset_id` and `table_name` will be globally unique and can hence act as the primary key)
  * `data_model_template` - a template of the data model as a JSON schema. Some properties will be hydrated such as the refcloud server URL (which will be known at runtime)
  * an `internal_query` which acts as a base query to retrieve the corresponding items from the database.

Questions for AI:
1. There is at least one table (the `datasets` table) that will not be associated with a specific dataset, and hence will not have a `dataset_id`. Can a composite of `dataset_id` and `table_name` still be used as a primary key?
2. I am envisioning a conversion engine that will convert an external user's query to a fully formatted end query by making use of the `internal_query`. This is because there will be things like table joins that we will perform internally and abstract away from the end researcher. They should be able to query based on the data model they are presented via the info endpoint. Does this make sense? Is something like this possible?
3. Do we need to capture any other properties to fulfill the spec?

AI Answers & Decisions:

1. **Primary key:** No. Postgres doesn't allow NULLs in primary key columns, so a composite `(dataset_id, table_name)` key can't hold the `datasets` table. Also, the API identifies a table by `table_name` alone (`/table/{table_name}/info`), so `table_name` must be globally unique anyway.
   * **Decision:** `table_name` is the primary key and includes the dataset in its name. `dataset_id` is a nullable foreign key to `dataset(id)` with `ON DELETE CASCADE`. NULL means a platform-wide table such as `datasets`.
   * **Naming:** follows Data Connect's `catalog.schema.table` pattern, e.g. `refcloud.<dataset>.drs_object` and `refcloud.platform.datasets`. Periods and dashes in dataset IDs are replaced with underscores.
   * **Decision:** underscores only, so table names never need quoting in `/search` (e.g. `refcloud.aws_opendata_registry_1000_genomes.drs_object`). Dashes aren't allowed because SQL reads them as the minus operator. A CHECK constraint enforces this: `table_name` must be dot-separated lowercase identifiers, each made of `[a-z0-9_]` and not starting with a digit.
2. **Query conversion engine:** Feasible. Each Data Connect table works like a view defined by its table type's `internal_query`. The engine will:
   1. **Parse** the user's SQL with a real SQL parser (e.g. `trino-parser`, which matches the spec's Trino-based grammar, or JSqlParser). Never build SQL by string concatenation. The parser is a new dependency and needs maintainer sign-off.
   2. **Validate:** accept a single `SELECT` only, allow only registered `data_connect_table` names, and allow only listed functions. Reject things like `pg_sleep`, `dblink` and system catalogs.
   3. **Authorize:** collect every table the query references and check access to each table's dataset (visa). This also covers joins across datasets.
   4. **Rewrite:** replace each table reference with `(<internal_query>) AS <alias>`, or put them in a `WITH` clause, with the dataset filter as a bind parameter, then turn the result back into SQL.
   5. **Execute** safely: in a read-only transaction with `statement_timeout`, a row limit and pagination, as a dedicated database role. That role can only read a dedicated `data_connect` schema of views, so even if validation misses something, the query can't reach tables like `passport_user`.
   * `/table/{table_name}/data` is the simple case: `SELECT * FROM <table_name>` through the same pipeline.
   * **Researcher filters:** yes, they work. The user's `WHERE`, `JOIN`, `ORDER BY`, `LIMIT` and aggregates apply to the rewritten outer query, and the Postgres planner pushes those filters down into the `internal_query` subquery, so they also use indexes on the base tables.
   * **Requirement on `internal_query`:** it must be a self-contained `SELECT` whose output columns exactly match the properties in the data model, with the dataset as a bind parameter (e.g. `WHERE dataset_id = :dataset_id`), never a hard-coded value.
   * **Dialect differences** between Trino and Postgres (e.g. `json_extract`, `unnest`, casts) are deferred and considered a niche case for now.
3. **Additional properties:** the spec's `Table` object is `name`, `description` (optional) and `data_model`. Pagination and errors are worked out when a request runs, so nothing needs to be stored for them.
   * **Decision:** add `description`, `table_type`, `created_at` and `updated_at`.

Further Decisions:

* **Option A, normalized table types:** one standard query and data model per table type.
  * `data_connect_table_type`: `id` (primary key, e.g. `datasets`, `drs_object`, `refget_sequence`), `description`, `data_model_template` (jsonb), `internal_query`, `created_at`, `updated_at`.
  * `data_connect_table`: `table_name` (primary key), `dataset_id` (nullable foreign key to `dataset`, cascades on delete), `table_type` (foreign key to `data_connect_table_type`, restricts on delete), `description`, `created_at`, `updated_at`.
  * `UNIQUE (dataset_id, table_type)` allows at most one table of each type per dataset. NULL `dataset_id`s count as distinct, so platform-wide tables aren't limited by it.
* **Rows:** `data_connect_table` rows are created by the onboarding script. The `datasets` table row and the standard `data_connect_table_type` rows will be seeded later through the CLI app, not in this migration.
* **`updated_at`:** both tables have a `BEFORE UPDATE` trigger that calls a shared `set_updated_at()` function, so the database keeps it current.
* **Scope:** Liquibase migration only. No JPA entities or repositories in this task.
* **Deferred to the query engine task:** the restricted database role and the `data_connect` schema of views. They depend on the final data models, and the role's password has to be managed outside the changelog.

### Task 4: JPA Entities and Repositories

* create JPA entity classes for `DataConnectTableType` and `DataConnectTable`.
  * `DataConnectTable` entity has an optional `@ManyToOne` to `Dataset` and a `@ManyToOne` to `DataConnectTableType`
  * Map the `data_model_template` (jsonb) property to `JsonNode`
* Add repositories: look up a table by `table_name`, and list tables visible for a given set of dataset IDs, including platform-wide tables with no `dataset_id`.

### Task 5: Standard Table Type Definitions (Seed Content)

* Define the columns for each table type:
  * `datasets`: id, name, description, tags, open vs visa-required
  * `drs_object`: id, `drs://` URI, name, size, mime type, checksums, created/updated times
  * `refget_sequence`: id, md5, ga4gh digest, length, circular, aliases
* Write the `data_model_template` for each type as JSON Schema:
  * use placeholders for runtime values (e.g. the server URL in `$id`)
  * use `$ref`s for column meaning (e.g. mark DRS identifier columns)
* Write the `internal_query` for each type, with the dataset as a bind parameter.
* The output is the seed content the CLI loads. The column names agreed here are needed by Tasks 6–8.
* Open decision: which JSON Schema draft to use (the spec examples use draft-07).

Outcome & Contract Summary:

The seed content now lives in the **CLI repo** (`seed/data-connect/`, loaded by `refcloud seed`), not in this repo. Its README is the full contract. This summary records what `refcloud-api` depends on.

* **Seed layout:** one directory per table type (`table-type.json`, `data-model.json` → `data_model_template`, `internal-query.sql` → `internal_query`), plus `tables/datasets.json` for the platform-wide `refcloud.platform.datasets` table (`dataset_id` null). The CLI loads table types first and uses `INSERT ... ON CONFLICT DO UPDATE`, so re-runs are safe. Files are stored verbatim, with no substitution at seed time.
* **Table types and columns:**
  * `datasets` (platform-wide): `id`, `name`, `description`, `tags`, `requires_visa`
  * `drs_object` (per dataset): `id`, `drs_uri`, `name`, `description`, `size`, `mime_type`, `checksums`, `aliases`, `created_time`, `updated_time`, `version`, `is_manifest`
  * `refget_sequence` (per dataset): `id` (`SQ.` + sha512t24u), `md5`, `trunc512`, `length`, `circular`, `aliases`, `drs_uri`
  * Refget sequences also appear in their dataset's `drs_object` table. Join them on `drs_uri`.
* **Data model placeholders (Task 6 must substitute these at runtime):**
  * `{{refcloud_base_url}}`: the server's public base URL, with no trailing slash
  * `{{table_name}}`: the `data_connect_table.table_name` being served
  * Used in `$id`: `{{refcloud_base_url}}/ga4gh/data-connect/v1/table/{{table_name}}/info`
* **Query bind parameters (Task 7 must bind these, never concatenate them):**
  * `:dataset_id`: the table's `data_connect_table.dataset_id` (used by `drs_object` and `refget_sequence`)
  * `:drs_host_domain`: `ga4gh.refcloud.drs.host-domain` (builds `drs_uri`, used by `drs_object` and `refget_sequence`)
  * `datasets` takes no parameters.
* **Query rules:** each `internal_query` is a single self-contained `SELECT` with no `ORDER BY` or `LIMIT`, reading the existing tables directly. Output column names exactly match the data model `properties`. Arrays and nested objects are returned as `jsonb`, sorted inside the query.
* **Data model conventions:** JSON Schema draft-07. `format` holds the SQL type name (`varchar`, `bigint`, `boolean`, `timestamp`, `json`). There are no `$ref` meaning links yet (no stable schema URLs identified), so each column has a `description` instead.
* **Type conversion (Task 7 must apply this):** following the Data Connect SQL-to-JSON mapping, `bigint` columns (`size`, `length`) are declared as `"type": "string"`, and `timestamp` columns are ISO 8601 strings. The queries return plain numbers and timestamps, so the engine must convert them.
* **Verified:** all three queries ran read-only against the local database with correct, de-duplicated results (row count equals distinct ID count).

> **⚠️ DRAFT: Tasks 6–11 are AI-proposed and incomplete.** They are an outline for planning and may be modified, reordered, merged or removed before implementation. Treat each task as a starting point to review, not a finalized requirement.

### Task 6: Implement `/tables` and `/table/{table_name}/info` (DRAFT, incomplete)

* Add `Table` and `ListTablesResponse` DTO records.
* Fill in `data_model_template` placeholders at runtime. This needs server base URL config (DRS already has `scheme` / `host-domain`).
* Filter by access:
  * **Anonymous:** open tables only (`datasets` plus tables in open datasets).
  * **With a passport:** open tables plus tables in datasets the user has an approved visa for.
* Add `canAccessDataConnectTable(authentication, tableName)` to `GA4GHPassportTokenEvaluator` for `/info`.
* Open decision: 403 vs 404 for tables the user can't access (recommended: 404, so restricted tables can't be discovered).

### Task 7: Query Engine (DRAFT, incomplete)

Implements the design in the Task 3 answers (Q2): parse → validate → authorize → rewrite → execute.

* Choose the SQL parser library. It's a new dependency and needs sign-off. `trino-parser` is the leading candidate because the spec's grammar is based on Trino.
* **Validate:** accept a single `SELECT` only, registered `data_connect_table` names only, and an allowed list of functions.
* **Authorize:** check access to every table the query references.
* **Rewrite:** replace each table reference with its type's `internal_query` subquery, with the dataset as a bind parameter, then turn the result back into SQL.
* **Database security:**
  * a `data_connect` schema of views
  * a restricted read-only database role, with its password managed outside the changelog
  * a separate `DataSource` for that role
* **Execute:** read-only transaction, `statement_timeout`, row cap.
* Convert database results to JSON values following the spec (e.g. timestamps as ISO 8601 strings).
* Add unit tests with malicious queries: system catalogs, `pg_sleep`, extra statements after a `;`, comment tricks, `UNION` into tables that aren't allowed.
* Dialect differences between Trino and Postgres are deferred (see Task 3).

### Task 8: Implement `/table/{table_name}/data` and Pagination (DRAFT, incomplete)

* Run `SELECT * FROM <table_name>` through the query engine.
* Implement pagination via `pagination.next_page_url`. Every non-empty page must include `data_model`.
* Open decision: a signed (tamper-proof), stateless page token encoding the query, parameters and offset (recommended), or server-side cursor storage. Make the page size configurable.

### Task 9: Implement `/search` (DRAFT, incomplete)

* Add a `SearchRequest` DTO record with Bean Validation (`@NotBlank` query).
* Bind positional `?` parameters using the spec's type mapping, and check that the parameter count matches the placeholders.
* Build the `data_model` for search results from the query's output columns, not from a stored template.
* Support `ga4gh_type()`. The spec says SHOULD, so this may be deferred.
* Reuse Task 8's pagination.

### Task 10: Error Handling and Spec Compliance (DRAFT, incomplete)

* Shape Data Connect errors to the spec's `ErrorResponse` in `GlobalExceptionHandler`: 400 for bad SQL or parameters, 404 for unknown tables, 500 otherwise.
* Check responses against `api.yaml`.
* Find out whether a Data Connect compliance suite exists and whether passing it is a separate task, as with Refget.

### Task 11: Integration, Docs and Cross-Repo Work (DRAFT, incomplete)

* Optional end-to-end integration test. Testcontainers would be a new dependency and needs sign-off.
* Update the README and config docs.
* Changes in other repos:
  * **CLI:** seed the standard `data_connect_table_type` rows and the `datasets` table row.
  * **Onboarding script:** create a `data_connect_table` row when a dataset is loaded. Table names must follow the naming rule: lowercase, `.`/`-` → `_`, no part starting with a digit.
* Spec clean-up: Task 1 and High-Level Tasks still say `/tables/{id}/...`. Update them to match the Data Connect spec's `/table/{table_name}/...`.
