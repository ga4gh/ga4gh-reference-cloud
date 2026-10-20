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

### Task 5: Standard Table Type Definitions (Seed Content) (COMPLETED IN CLI REPO)

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

### Task 6: Implement `/tables` and `/table/{table_name}/info`

* Add `Table` and `ListTablesResponse` DTO records.
* Fill in `data_model_template` placeholders at runtime. This needs server base URL config (DRS already has `scheme` / `host-domain`).
* Filter by access:
  * **Anonymous:** open tables only (`datasets` plus tables in open datasets).
  * **With a passport:** open tables plus tables in datasets the user has an approved visa for.
* Add `canAccessDataConnectTable(authentication, tableName)` to `GA4GHPassportTokenEvaluator` for `/info`.
* Open decision: 403 vs 404 for tables the user can't access (recommended: 404, so restricted tables can't be discovered).

Decisions:

1. **404 for inaccessible tables:** a table the user can't access returns the same 404 and message as a table that doesn't exist (`No Data Connect table with name: <name>`), so restricted tables can't be discovered. `canAccessDataConnectTable` throws `ResourceNotFoundException` instead of returning false, because returning false would produce a 401/403 that reveals the table exists. A request with an invalid or expired token still gets a 401 from the security filter chain before this check runs.
2. **Base URL config:** a new `ga4gh.refcloud.data-connect.base-url` property (e.g. `http://127.0.0.1:8080`) in `DataConnectConfig`, independent of the DRS settings, as Task 2 decided. It's read at runtime to fill `{{refcloud_base_url}}`, and a trailing slash is removed if present.
3. **`/tables` includes the full data model:** each table in `/tables` has its complete, filled-in JSON Schema, not a `{"$ref": ".../info"}` link, so clients get everything in one call.
4. **No pagination on `/tables`:** `pagination` is optional in `ListTablesResponse` and is left out until there's a use case.
5. **Description fallback:** a table with no `description` uses its table type's description. If both are null, the field is omitted (`@JsonInclude(NON_NULL)`).

Implementation Notes:

* `DataConnectTableService` (`@Transactional(readOnly = true)`) returns `DataConnectTableResponseDTO` (`name`, `description`, `data_model`) and `ListTablesResponseDTO` (`tables`) records, never entities. The spec's optional `errors` field is left out for now.
* **Placeholders** are filled by walking a deep copy of the stored JSON template and replacing text inside string values only. Substituted text can't break the JSON structure, and the entity's template is never modified.
* **Visibility rule:** defined once in `DataConnectTableRepository` (`VISIBLE_TO_USER_FROM_WHERE`) and shared by `findAllVisibleToUser` (`/tables`) and `findVisibleByTableName` (`/info` access check).
  * The `Approved` status is bound as a query parameter. A JPQL enum literal doesn't work here: Hibernate turns it into a cast to the type name `passportvisaassertionstatus`, but the Postgres enum is `passport_visa_assertion_status`.
* `/tables` reads the caller's identity with `@AuthenticationPrincipal Jwt` (null for anonymous requests). `/info` uses `@PreAuthorize("@GA4GHPassportTokenEvaluator.canAccessDataConnectTable(authentication, #tableName)")`.
* **Verified** against local data:
  * anonymous `/tables` lists `refcloud.platform.datasets` and both tables in the open conformance dataset, with `$id` filled in;
  * `/info` returns 200 for those tables, and 404 for the visa-restricted 1000 Genomes table and for a table that doesn't exist;
  * an invalid token returns 401.
  * Not yet verified with a real passport token (needs Hydra).
* **Known limitations:**
  * `data_model_template` is stored as `jsonb`, which reorders object keys, so `properties` come back in Postgres's key order rather than the authored order. **Accepted for now (not a priority).** If column order becomes important to clients, change the column to `json` to keep the authored order.
  * Error responses still use the app's existing `ErrorDetails` format rather than the spec's `ErrorResponse`, which is covered by Task 10.

### Task 7: Query Engine

Implements the design in the Task 3 answers (Q2): parse → validate → authorize → rewrite → execute. Split into 7a (no database access) and 7b (database execution).

Decisions:

* **Parser: JSqlParser** (new dependency, approved; no Spring starter provides SQL parsing). `trino-parser` was considered, but it would write back Trino-style SQL to run on Postgres. JSqlParser parses Postgres-style SQL and writes Postgres-style SQL, as a single jar with no dependencies. Because JSqlParser accepts a very wide range of syntax, validation **must reject anything not explicitly allowed**, checking every part of the parsed query (statement type, table names, functions).
* **No `data_connect` schema of views** (reverses the earlier Task 3 note). Table and column `GRANT`s to a restricted role give the same protection, row filtering is already done by authorization plus the bound `:dataset_id`, and views would require rewriting the CLI repo's `internal_query` files.
* **A restricted role is required:** `/search` runs SQL sent by anyone, and the app's `refcloudapi` login owns every table (it can read `passport_user` and visa assertions, write data, and call dangerous functions such as `query_to_xml` and `pg_sleep`). A separate role turns a validator bug into a harmless permission error. It must be a **separate login with its own connection pool**: `SET LOCAL ROLE` on the existing connection could be undone with `set_config('role', ...)` from inside a `SELECT`.
* **Initial function allow list:** the functions the spec says implementations SHOULD support, mapped to their Postgres equivalents: `AND`/`OR`/`NOT`, comparisons, `LIKE`, `IS NULL`, `CAST`, `CASE`, `COALESCE`, `substring`, `||`, `lower`/`upper`, `COUNT`/`MIN`/`MAX`/`SUM`, date/time functions, and `jsonb` operators (for `checksums`, `aliases`, `tags`). Everything else is rejected.
* **Limits (configurable in `application.yml`):** 30s statement timeout, 10,000-character maximum query length, 100-row page size.
* **Tests:** plain JUnit unit tests (no Spring context) for the validator, including malicious queries.
* Dialect differences between Trino and Postgres are deferred (see Task 3).

#### Task 7a: Parse, Validate, Authorize, Rewrite

* Add the JSqlParser dependency.
* **Parse** the query, rejecting it if it's over the maximum length or doesn't parse.
* **Validate:** a single `SELECT` statement only (no `;`-separated statements, no DML/DDL), registered `data_connect_table` names only, and allowed functions/expressions only. Reject system catalogs (`pg_*`, `information_schema`) and anything not explicitly allowed.
* **Authorize:** collect every table referenced anywhere in the query (including subqueries, joins, `UNION`s and CTEs) and check each with the Task 6 visibility rule. An inaccessible table returns the same 404 as a missing one.
* **Rewrite:** replace each table reference with `(<internal_query>) AS <alias>`, collect bind parameters (`:dataset_id`, `:drs_host_domain`) per reference so two tables from different datasets get separate values, and turn the result back into SQL.
* **Unit tests (JUnit):** valid queries rewrite correctly, plus malicious cases: system catalogs, `pg_sleep`, `query_to_xml`, extra statements after a `;`, comment tricks, `UNION` into tables that aren't allowed, CTE names shadowing table names, unknown functions.
* No database access in this task.

Outcome & Implementation Notes:

* **Dependency:** `com.github.jsqlparser:jsqlparser:5.4` (released 2026-09-13; no runtime dependencies).
* **Package** `dataconnect/query/`:
  * `DataConnectQueryRewriter`: plain Java with no Spring or database access. It does parse, validate, authorize and rewrite.
  * `DataConnectQueryService`: a Spring service that resolves tables through `DataConnectTableRepository.findVisibleByTableName` (the Task 6 visibility rule) and supplies `:drs_host_domain` from `DrsConfig`. **Not yet called by any controller**; Tasks 8 and 9 will use it.
  * `QueryAllowList`: the allow lists.
  * `AstWalker`: walks the parsed query.
  * `ResolvedTable` and `RewrittenQuery`: records.
* **Allowed-list checks reject anything not listed:** `AstWalker` uses reflection to visit *every* object in the parsed query, not JSqlParser's visitor adapters, which silently accept node types they don't know.
  * **Node classes** are matched exactly, so subclasses such as `LateralSubSelect` are rejected.
  * **Enum values** are allowed individually, e.g. `EmitMode.NONE` (a default present on every query) and `UNION`/`INTERSECT`/`EXCEPT`. So `FOR UPDATE` (`ForMode`) is rejected.
  * **Functions** must be unqualified names on the allowed list, so `pg_catalog.lower` and `"pg_sleep"` are rejected.
  * **Cast types** are checked without their arguments, so `regclass` and `regproc` are rejected.
  * **Bare `CURRENT_USER`-style keywords** (which JSqlParser parses as columns) are rejected.
  * **`WITH RECURSIVE`** is rejected.
  * **Researcher bind parameters** (`:name`, `?`) are rejected. Positional `?` comes back in Task 9.
  * **Table names** are accepted only as `FROM`/`JOIN` items or as column qualifiers, so `SELECT ... INTO` is rejected.
* **Errors:**
  * Parse or validation failures → 400 (`BadRequestException`). Validation of the whole query finishes before any table is looked up.
  * Unknown *or* inaccessible tables → 404 (`DataConnectTableService.tableNotFound`, the same message as Task 6). This includes system catalogs and base tables such as `pg_user` and `drs_object`, and tables nested in subqueries, `UNION`s and CTEs.
* **Rewrite:** each distinct referenced table becomes a `WITH dc_table_N AS NOT MATERIALIZED (<internal_query>)` CTE.
  * `NOT MATERIALIZED` keeps Postgres pushing the researcher's filters into the internal query even when a table is referenced more than once.
  * Table CTEs come before any researcher CTEs. Since `RECURSIVE` is rejected, a researcher CTE can never shadow a base table that an internal query reads.
  * **Names and aliases:** an unaliased table gets its last name part as its alias (e.g. `datasets`). Column qualifiers that use the full table name are re-pointed at that alias. Generated CTE names skip any names the researcher's CTEs already use.
  * **Bind parameters:** `:dataset_id` is renamed per table (`:dc_table_N_dataset_id`), so a join across datasets binds separate values. Other internal parameters (`:drs_host_domain`) are shared.
  * Table names follow Postgres rules: unquoted names are case-insensitive, quoted ones are case-sensitive.
* **Config:** `ga4gh.refcloud.data-connect.query.max-length: 10000` (`DataConnectConfig.query().maxLength()`). The timeout and page size come in 7b and Task 8.
* **Tests:** `DataConnectQueryRewriterTest` uses **TestNG**, not JUnit, because the build is set up for TestNG (`useTestNG()`). It has 68 cases, all passing:
  * valid rewrites;
  * 11 allowed queries;
  * 35 rejected queries: multiple statements, DML/DDL, `pg_sleep`, `query_to_xml`, `set_config`, `current_setting`, `pg_read_file`, `dblink`, `generate_series`, `version()`, `current_user`, `INTO`, `FOR UPDATE`, `WITH RECURSIVE`, `LATERAL`, window functions, bind parameters, `regclass` casts, the length limit, and comment tricks;
  * 13 unknown or inaccessible table cases, nested in subqueries, `UNION`s, CTEs and joins.
* **Verified:** rewritten queries (aggregates, joins between tables, `EXCEPT`, `jsonb` operators, full-name column qualifiers) were also run read-only against the local database using the real seeded internal queries, and returned correct results.

#### Task 7b: Restricted Role, DataSource and Execution

* **Role creation (admin setup, outside Liquibase):** `setup/postgres/create-data-connect-reader-role.sql`, referenced from README step 1.
  * Creates `data_connect_reader` (`LOGIN`, no superuser, no CREATEDB, no CREATEROLE) with `default_transaction_read_only = on` and `statement_timeout = '30s'`.
  * Safe to re-run, so it also rotates the password.
  * It's outside Liquibase because `refcloudapi` has no `CREATEROLE` privilege, and a password must not go in the changelog.
  * The password comes from the `DATA_CONNECT_READER_PASSWORD` environment variable (read with psql `\getenv`, so it's not visible in the process list), falling back to `secret` for local dev.
  * In prod, the Helm chart (or whatever provisions the database) runs this with the value from a Kubernetes Secret.
* **Grants (Liquibase, in this repo):** `GRANT SELECT` on only the tables the `internal_query`s read: `dataset`, `tag`, `dataset_tag`, `drs_object`, `drs_object_checksum`, `drs_object_alias`, `refget_sequence`, `refget_sequence_alias`, plus `passport_visa (id, dataset_id)` columns only (needed by `requires_visa`). `refcloudapi` owns the tables, so it can grant. The role must exist before the changelog is applied.
* **App credentials:** `ga4gh.refcloud.data-connect.datasource.{url, username, password}` in `application.yml` (local default `secret`). In prod, set `GA4GH_REFCLOUD_DATACONNECT_DATASOURCE_PASSWORD` through Spring Boot's relaxed binding, from the **same** Kubernetes Secret used for role creation so the passwords can't drift apart. The same pattern as `SPRING_DATASOURCE_PASSWORD`.
* A **separate `DataSource`/connection pool** for `data_connect_reader`. It's not the app's primary JPA `DataSource`, which must remain the default.
* **Execute** the rewritten query with named bind parameters, in a read-only transaction. The app also sets `statement_timeout` per transaction, so limits hold even if a prod setup leaves out the role-level settings. Cap the rows returned.
* **Convert results to JSON** following the Data Connect type mapping: `bigint` → string, timestamps → ISO 8601 strings, `jsonb` → embedded JSON, booleans and smaller numbers as-is.
* Add `GA4GH_REFCLOUD_DATACONNECT_DATASOURCE_PASSWORD` to the README configuration table once the app reads it.

Outcome & Implementation Notes:

* **Grants:** Liquibase changeset `grant-select-data-connect-reader` grants **read-only (`SELECT`)** on the 8 tables, plus `passport_visa (id, dataset_id)` columns only, to `data_connect_reader`, with a matching `REVOKE` rollback.
* **Config** (`DataConnectConfig`, `application.yml`):
  * `query.statement-timeout: 30s` and `query.page-size: 100`;
  * `datasource.{url, username, password, maximum-pool-size: 5}` (local dev password `secret`).
  * The README configuration table lists the Data Connect environment variables.
* **Connection pool** (`DataConnectDataSourceConfig`):
  * a Hikari pool named `data-connect`, with read-only connections, that starts on first use;
  * declared as `@Bean(name = "dataConnectDataSource", defaultCandidate = false)`, so Spring Boot still auto-configures the primary JPA `DataSource`. Verified: the app starts and `/tables` (JPA) still works.
  * Inject it with `@Qualifier(DataConnectDataSourceConfig.DATA_SOURCE)`.
* **Execution** (`DataConnectQueryExecutor.execute(RewrittenQuery, maxRows)` → `QueryResult(columns, rows, hasMore)`):
  * It uses its own `DataSourceTransactionManager`, which isn't a bean so it doesn't replace JPA's. The transaction is read-only (`setEnforceReadOnly`, so `SET TRANSACTION READ ONLY`) and sets `SET LOCAL statement_timeout` and `SET LOCAL intervalstyle = 'iso_8601'` per transaction.
  * It streams rows with a fetch size of page size + 1, and fetches one extra row to work out `hasMore`, which Task 8's pagination will use.
  * Duplicate column names are rejected with a 400, because rows are JSON objects keyed by column name.
  * **Errors:**
    * time limit exceeded (`57014`) → 400;
    * mistakes in the researcher's SQL (SQLSTATE classes `22` and `42`, e.g. unknown column or operator) → 400 with the first line of the Postgres message;
    * permission denied (`42501`, which validation should prevent) and everything else → 500, without details.
* **Value conversion** (`QueryValueConverter`, following the spec's SQL-to-JSON table):
  * `bool` → boolean;
  * `int2`/`int4`/`float4`/`float8` → number (NaN and Infinity → string);
  * `int8`/`numeric` → string;
  * `date`/`time`/`timetz`/`timestamp`/`timestamptz` → ISO 8601 strings;
  * `interval` → ISO 8601 duration (`P1DT2H`);
  * `json`/`jsonb` → embedded JSON;
  * arrays → JSON arrays, with elements converted the same way;
  * anything else → its text form.
  * `QueryColumn` records each column's Postgres type name, for Task 9's `/search` data model.
* **Verified locally:** the role was created with `setup/postgres/create-data-connect-reader-role.sql`; grants were applied temporarily by hand and then **revoked**, so the Liquibase changeset is still to be applied.
  * A real rewritten query (`drs_object`) returned correctly converted JSON: `size` as a string, `checksums` as embedded JSON, `created_time` as ISO 8601.
  * Every type listed above was checked, including arrays and NaN.
  * **Row limit:** 3 rows with `hasMore = true` on the 1000 Genomes table; 2 rows with `hasMore = false` when fewer rows exist than the limit.
  * **Role restrictions:** the role is **denied** `passport_user`, `passport_user_visa_assertion`, `passport_visa.name`, `aws_s3_access_object`, `data_connect_table`, and `set_config('role', ...)`. It is **allowed** `passport_visa (id, dataset_id)`.
  * **Writes blocked:** `INSERT`, a `DELETE` hidden in a CTE, and `CREATE TEMP TABLE` all fail with "read-only transaction", and the data was unchanged afterwards.
  * **Timeout:** `pg_sleep(60)` was cut off after about 2s (the test setting) with a 400.
  * **Session settings:** queries ran as `data_connect_reader` with `default_transaction_read_only = on`, `transaction_read_only = on`, and the app-set timeout.
* **Tests:** all 69 tests pass. The executor and converter have no automated tests, because they need a real database; they were verified against the local database instead. Integration tests (e.g. Testcontainers) are Task 11.

> **⚠️ DRAFT: Tasks 8–11 are AI-proposed and incomplete.** They are an outline for planning and may be modified, reordered, merged or removed before implementation. Treat each task as a starting point to review, not a finalized requirement.

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
