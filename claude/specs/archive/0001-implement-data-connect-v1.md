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

### Task 8: Implement `/table/{table_name}/data` and Pagination

* Run `SELECT * FROM <table_name>` through the query engine.
* Implement pagination via `pagination.next_page_url`. Every non-empty page must include `data_model`.
* Open decision: a signed (tamper-proof), stateless page token encoding the query, parameters and offset (recommended), or server-side cursor storage. Make the page size configurable.

Decisions:

1. **Unsigned offset page token:** `GET /table/{table_name}/data?page_token=<token>`. The token is base64url-encoded JSON (`{"offset":N}`), so fields can be added later. It isn't stored on the server and isn't signed: every page reruns validation and access checks for the current caller, so a modified token can only return rows the caller could already read. The table name is already in the path, so the token only needs the offset. `/search` will probably need something more sophisticated (its tokens would have to carry the SQL itself, up to 10,000 characters), which is decided in Task 9.
2. **OFFSET pagination ordered by `id`:** `SELECT * FROM <table> ORDER BY id LIMIT <page size + 1> OFFSET <n>`. This depends on every table type having a unique `id` column (true for all three; Task 5 checked row count equals distinct ID count). It will be replaced with continuing after the last `id` seen if deep pages get too slow.
3. **Page size:** the configured `query.page-size` (100). Clients can't choose a page size, since the spec doesn't define a parameter for it.
4. **Table name check:** the path's `table_name` is checked against the same rule as the `data_connect_table_table_name_format_check` constraint before it's put into SQL. Names that don't match get the usual 404. The query then goes through the full rewrite step like any other.
5. **Scope:** `/data` only, plus pieces Task 9 can reuse (`TableDataResponseDTO`, `PaginationDTO`, `PageTokenCodec`). `/search` is unchanged.

Outcome & Implementation Notes:

* **Access:** the same `@PreAuthorize("@GA4GHPassportTokenEvaluator.canAccessDataConnectTable(...)")` as `/info`, so an inaccessible table returns the same 404 as a missing one. `prepareQuery` checks visibility again when it resolves the table.
* **New classes:**
  * `table/DataConnectTableDataService` (`@Transactional(readOnly = true)`): checks the name, decodes the token, gets the filled-in data model from `DataConnectTableService.getTable`, then runs the query through `DataConnectQueryService.prepareQuery` and `DataConnectQueryExecutor.execute`.
  * `query/TableDataResponseDTO` (`data_model`, `data`, `pagination`), with `pagination` left out on the last page (`@JsonInclude(NON_NULL)`). The spec's optional `errors` field is left out for now.
  * `query/PaginationDTO` (`next_page_url`).
  * `query/PageToken` and `query/PageTokenCodec`. A token that doesn't decode, or has a negative offset, returns a 400 (`invalid page_token`).
* **`data_model`** is the same filled-in template `/info` returns. It's included on every page, including empty ones (the spec allows leaving it out on empty pages).
* **`next_page_url`** is absolute, built from `base-url`. `DataConnectConfig.baseUrlWithoutTrailingSlash()` now handles the trailing slash for both this and the `{{refcloud_base_url}}` placeholder.
* **The extra row:** the query asks for one row more than the page size, so a table with exactly page-size rows correctly has no next page.
* **Verified locally:**
  * with the default page size, all three open tables return their rows, `data_model` and no `pagination`. Values are converted (`size` and `length` as strings, `checksums` and `aliases` as embedded JSON);
  * with `GA4GH_REFCLOUD_DATACONNECT_QUERY_PAGESIZE=2`, the 4-row `drs_object` table returns 2 pages that don't overlap, ordered by `id`, and only page 1 has `next_page_url`. The 2-row `datasets` table returns 1 page with no `next_page_url`. `data_model` matches `/info` exactly;
  * an offset past the end returns an empty page with no `pagination`;
  * a page token that doesn't decode, or has a negative offset → 400;
  * the visa-restricted 1000 Genomes table, a table that doesn't exist, a name in uppercase, a base table (`drs_object`) and a system catalog (`pg_user`) → 404;
  * an invalid bearer token → 401.
  * Not yet verified with a real passport token (needs Hydra).
* **Tests:** all existing tests pass. No new automated tests: `/data` needs a real database, which is Task 11's integration test work.
* **Open for Task 9 (not verified):** with Spring Boot's default `spring.jpa.open-in-view`, a primary-pool connection is likely held for the whole request once JPA uses one, including while the Data Connect query runs. That's harmless for `/data` (fast, paged queries), but `/search` can run for up to 30s, so check this and consider turning it off then.

### Task 9: Implement `/search`

* Add a `SearchRequest` DTO record with Bean Validation (`@NotBlank` query).
* Bind positional `?` parameters using the spec's type mapping, and check that the parameter count matches the placeholders.
* Build the `data_model` for search results from the query's output columns, not from a stored template.
* Support `ga4gh_type()`. The spec says SHOULD, so this may be deferred.
* Reuse Task 8's pagination.

Decisions:

1. **Save results on the server, not in the page token:** a `/search` token would have to carry the query and parameters (up to 10,000 characters), which can exceed URL length limits.
   * `POST /search` runs the query **once**. A result that fits on one page is returned directly and nothing is saved. For a longer result, page 1 is returned and the remaining rows are saved; later pages are read from the saved copy.
   * Pages are therefore stable without the researcher writing an `ORDER BY`, follow the researcher's own `ORDER BY` exactly, and later pages don't rerun the query.
   * Adding `ORDER BY` to the internal queries was considered and rejected: Postgres doesn't keep a subquery's order through joins, `GROUP BY`, `DISTINCT` or `UNION`; it would sort every query and stop Postgres merging the internal query into the outer one; and it would break the Task 5 contract ("no `ORDER BY`").
   * Sorting by the whole row (`ORDER BY q::text`) and rerunning the query per page was also rejected: it replaces the researcher's own `ORDER BY` and re-sorts the full result for every page.
2. **Storage:** two tables in the existing Postgres database (an in-memory cache would break with more than one replica; Redis would be new infrastructure).
   * `data_connect_search` (`id` uuid, `user_id`, `data_model`, `table_names`, `size_bytes`, `created_at`, `expires_at`) and `data_connect_search_row` (`search_id`, `row_number`, `data`), primary key `(search_id, row_number)`, rows cascade-deleted with their search.
   * **Expiry: 2 hours.** Expired searches are deleted whenever a new one is saved (no scheduler), and an expired search returns 404.
   * Measured row sizes on local data: `drs_object` ~650 bytes of JSON per row (~750 stored), `datasets` ~400, `refget_sequence` ~300. A 10,000-row `drs_object` result is ~7.5 MB.
3. **Limits** (all configurable under `ga4gh.refcloud.data-connect.query.search`):
   * `max-rows: 10000`: larger results get a 400 asking the researcher to narrow the query or add `LIMIT` (whole tables are browsed with `/data`). Silently cutting results off was rejected.
   * `max-bytes: 25MB` per result, counted as rows stream in, so a researcher concatenating columns into huge rows can't exhaust memory or storage (400).
   * `storage-max-bytes: 1GB` total across unexpired saved searches. When full, a new multi-page search gets **503 with `Retry-After`**. Single-page searches and existing saved searches keep working.
4. **Follow-up pages:** `GET /ga4gh/data-connect/v1/search/{search_id}?page_token=<offset token>`. The spec's `api.yaml` defines only `POST /search` and leaves the pagination URL scheme to the server. The token is Task 8's offset token.
5. **Auth on follow-up pages:**
   * The bearer token is fully validated (signature, expiry, issuer) by the security filter chain; an invalid or expired token is a 401.
   * A search run by a signed-in user stores their subject (`sub`), and later pages must come from the same subject. A missing or different token returns the same 404 as a missing search, so other users can't tell it exists.
   * An anonymous search stores no subject. Any caller can continue it: it can only read open tables, because restricted tables already return 404 when the search is first run. This is the special case that lets anonymous users page through searches on open data.
   * Every follow-up page rechecks that the caller can still access every table the search read, so losing a visa mid-search cuts off further pages.
6. **Parameters** (spec mapping): boolean → boolean, number → double, string → varchar, null → NULL, arrays whose elements share one type → SQL arrays. JSON objects (`row`) get a 400. The number of values must match the number of `?`s. Named (`:name`) and numbered (`?1`, `$1`) placeholders are still rejected.
7. **`ga4gh_type()` built now:** allowed only as an item of the outermost select list, with a `'$ref:<url>'` string literal as its second argument. The column is named by its alias, or by the column itself; an expression without an alias gets a 400. Anywhere else it's a 400.
8. **`data_model`** is built from the result columns, physical types only: `type` is the JSON type returned and `format` the SQL type, using the same names as the seeded table models. Arrays get `items`; `json`/`jsonb` columns get no `type`; `ga4gh_type()` columns get only `$ref`. No descriptions are copied from stored table models. An empty result still gets a `data_model`.
9. **`spring.jpa.open-in-view: false`** for the whole app (approved), after confirming it held a primary-pool connection for the whole of a search request.

Outcome & Implementation Notes:

* **Query engine changes (`dataconnect/query`):**
  * `DataConnectQueryRewriter` takes positional parameter values. `?` placeholders are allowed (`JdbcParameter` added to `QueryAllowList`), checked against the value count before any table is looked up, and renamed to `:dc_param_1`, `:dc_param_2`, … in text order (the parser numbers them in text order), so they bind alongside the internal queries' named parameters.
  * `ga4gh_type()` wrappers are removed before validation, so the wrapped value is still fully validated, and any remaining `ga4gh_type()` is rejected with a specific message.
  * `RewrittenQuery` now also returns `tableNames` (the Data Connect tables read) and `columnSchemaRefs` (from `ga4gh_type()`).
  * `ANY` added to the function allow list, because `id = ANY(?)` is how an array parameter is used in Postgres. It only compares values.
  * `ArrayParameter` (element type + values) is bound by the executor as one SQL array (`Connection.createArrayOf`); a plain collection would be expanded by `NamedParameterJdbcTemplate` into a list of values.
  * `DataConnectQueryExecutor.execute(query, maxRows, maxBytes)` counts each row's JSON size as it streams and stops with a 400 when the limit is passed.
* **New classes (`dataconnect/search`):**
  * `DataConnectSearchController`: `POST /search` (`@Valid SearchRequestDTO`) and `GET /search/{search_id}`, both open with an optional token (added to `SecurityConfig`'s custom-security endpoints).
  * `DataConnectSearchService`: runs the search, builds the `data_model`, saves results longer than one page, and serves later pages with the subject and access checks. `search()` runs with `Propagation.NOT_SUPPORTED`, so no primary-pool connection is held while the researcher's query runs.
  * `DataConnectSearchStorageService`: the transactional save. It locks `data_connect_search` (`SHARE ROW EXCLUSIVE`, which doesn't block reads) so the storage-limit check and the insert can't interleave, deletes expired searches, checks the limit, then inserts.
  * `DataConnectSearchRepository`: `JdbcTemplate`, not JPA, so up to 10,000 rows insert in batches of 1,000. All expiry comparisons use the database clock (`now()`).
  * `SearchRequestDTO`, `SearchParameterConverter`, `SearchDataModelBuilder`, `DataConnectSearch`.
  * `exception/ServiceUnavailableException` → 503 with a `Retry-After` header (seconds until the next saved search expires and frees space).
* **Storage format:** `data_model` and row `data` are stored as `json`, not `jsonb`, which keeps key order (the Task 6 `jsonb` reordering issue). Only rows after page 1 are saved; `row_number` is the row's position in the full result, so a page token's offset maps directly onto it.
* **No grants:** `data_connect_reader` can't read the new tables (verified: "permission denied"), so researcher SQL can never read or change saved searches.
* **Liquibase:** changesets `create-table-data-connect-search`, `index-tb-data-connect-search-column-expires-at`, `create-table-data-connect-search-row` and the cascade foreign key. Applied to the local database.
* **`open-in-view` check:**
  * With the primary pool limited to one connection and `open-in-view` on, a `/tables` request made during a 25.6s search waited 24.6s, even though the search ran outside a transaction. With it off, `/tables` answered in 0.03s.
  * Turning it off broke one path: `DrsObjectService.validateUserIsAuthorizedForDrsObject` (and `getMultiDrsObjects`) had no transaction and reached the lazy `drsObject.getDataset()` through an internal call, so a signed-in request for a restricted DRS object returned 500 (`LazyInitializationException`). Fixed by giving both methods `@Transactional(readOnly = true)`; the request now correctly returns 403.
  * Other services were checked: every other lazy relationship is read inside a transaction or loaded with a fetch join. Refget, DRS (anonymous, auth-info and with a token), service-info and all Data Connect endpoints were exercised with no lazy-loading errors. Endpoints behind a Kratos session (`/datasets`) weren't exercised with a session; their service methods are transactional and use fetch joins.
  * `/table/{table_name}/data` also now runs its query with `Propagation.NOT_SUPPORTED`.
* **Verified locally** (page size 2; signed-in calls used a temporary Hydra client-credentials token, deleted afterwards):
  * single-page searches return no `pagination`; a 4-row join ordered `DESC` returns 2 pages in the researcher's order, with the same `data_model` on both;
  * parameters: boolean, number, string, null, a string array with `= ANY(?)`, `CAST(? AS date)`, and `LIMIT ?` (Postgres accepts a double there, so no `CAST` is needed);
  * `ga4gh_type()` produces a `$ref` column in the `data_model`; misuse → 400;
  * saved search: anonymous, `table_names` recorded, 2-hour expiry, rows numbered from the page size;
  * follow-up pages: anonymous search continued anonymously → 200; with no `page_token` → second page; invalid bearer → 401; bad token → 400; malformed or unknown ID → 404; owned by another subject → 404; a table no longer accessible → 404; expired → 404, and deleted on the next save;
  * limits (with tiny test values): over `max-rows` → 400; over `max-bytes` → 400; storage full → 503 with `Retry-After: 7189`, while single-page searches and existing saved searches still work;
  * count mismatch, JSON object parameter, blank or missing `query`, restricted table (404) and `pg_sleep` (400) errors.
* **Tests:** 95 pass (26 new). `DataConnectQueryRewriterTest` covers positional parameter binding and count mismatches, numbered placeholders, `ga4gh_type()` (valid and 9 invalid uses), `ANY` and `tableNames`. The new `SearchParameterConverterTest` covers the type mapping and rejected parameters. The search service, storage and executor need a real database (Task 11).
* **Known limitations:**
  * A blank `query` gets Spring's long default validation message; error bodies are reshaped in Task 10.
  * The new `query.search.*` settings aren't in the README configuration table yet (Task 11 docs).
  * Found while testing and **fixed** (an existing DRS bug, unrelated to `open-in-view`): `DrsObjectService.getVisaIdByDrsObjectId` called `getPassportVisa().getId()`, which threw a `NullPointerException` for objects in open datasets (no visa), so a signed-in request for an open DRS object, or a bulk request including one, returned 500. It now returns `Optional<String>` (empty for an open dataset), and `validateUserIsAuthorizedForDrsObject` allows open objects, matching `RefgetSequenceService`. Verified with a token: open object GET and POST → 200, bulk resolves the open object, a restricted object without a visa → still 403.
  * The same assumption was **fixed in `DatasetService`**: `getAllDatasets`, `getDatasetById`, `convertToResponseDto`, `getVisaIdByDatasetId` and `validateUserIsAuthorizedForDataset` all called `getPassportVisa().getId()`, so `/datasets` and `/datasets/{id}` failed whenever an open dataset existed, as did `/datasets/{id}/manifests` for an open dataset. Open datasets now return `"visa": null`, `getVisaIdByDatasetId` returns `Optional<String>`, open datasets pass the manifests access check, and `POST /datasets/{id}/request-access` on an open dataset returns 400 ("open access and does not require an access request"). Checked with a temporary unit test using stub repositories; not exercised live, because those endpoints need a Kratos browser session. **The UI must handle `visa: null`** for open datasets.

### Task 10: Error Handling and Spec Compliance

* Shape Data Connect errors to the spec's `ErrorResponse` in `GlobalExceptionHandler`: 400 for bad SQL or parameters, 404 for unknown tables, 500 otherwise.
* Check responses against `api.yaml`.
* Find out whether a Data Connect compliance suite exists and whether passing it is a separate task, as with Refget.

Decisions:

1. **Data Connect only:** errors use the spec's `ErrorResponse` (`{"errors": [{"title", "detail"}]}`) for Data Connect endpoints only, through handlers that apply only to Data Connect. DRS and Refget keep `GlobalExceptionHandler`'s existing `ErrorDetails` format, since their own specs define their errors.
2. **`title` is fixed per kind of error, `detail` is specific to the occurrence:**
   * 400 "Invalid query": SQL, parameter, `ga4gh_type()` and limit errors;
   * 400 "Invalid request": malformed body, blank `query`, bad `page_token`;
   * 404 "Table not found" / "Search not found";
   * 503 "Search storage full", with `Retry-After`;
   * 500 "Internal server error", with no internals in `detail`; the full exception is logged on the server.
   * `source` is never set: the spec reserves it for errors from an attached data source, and every table here comes from the refcloud database.
3. **Everything Data Connect uses the format**, including errors Spring raises itself (invalid JSON, validation, 405 wrong method, 415 wrong content type, 404 unknown path) and errors from the security filters (401 invalid or missing token, 403 CSRF rejection), keeping their status codes and headers (`Allow`, `WWW-Authenticate`).
4. **No `errors` list on successful responses:** there are no partial-result cases, since queries run against one database and either succeed or fail.
5. **Response check:** a one-off validation against `api.yaml` now (below). A proper conformance test comes later.
6. **No compliance suite to pass:** none exists for Data Connect v1. The only candidate, [ga4gh-search-compliance](https://github.com/ga4gh-discovery/ga4gh-search-compliance), targets "GA4GH Search", the spec's name before it became Data Connect, and was last updated in November 2020, before v1.0. So there's no separate compliance task, unlike Refget.

Outcome & Implementation Notes:

* **New package `dataconnect/error`:**
  * `DataConnectErrorResponseDTO` and `DataConnectErrorDTO` (`title`, `detail`).
  * `InvalidQueryException`, `TableNotFoundException` and `SearchNotFoundException`. They extend the existing `BadRequestException` and `ResourceNotFoundException`, so code and tests that expect those still work. All query errors (rewriter, executor, parameter converter, the row limit) now throw `InvalidQueryException`; the page-token error stays a plain `BadRequestException` ("Invalid request").
  * `DataConnectExceptionHandler`: `@RestControllerAdvice(basePackages = "org.ga4gh.refcloud.api.dataconnect")` at `HIGHEST_PRECEDENCE`, so it handles every exception from a Data Connect controller (including the `@PreAuthorize` table check) ahead of `GlobalExceptionHandler`. Spring exceptions that carry their own status (`org.springframework.web.ErrorResponse`) keep that status and headers; anything else is a logged 500.
  * `DataConnectHandlerlessExceptionResolver`: errors raised before a controller is chosen (405, 415, unknown path) never reach a controller-scoped advice. This resolver catches them for `/ga4gh/data-connect/` paths and passes them to `DataConnectExceptionHandler`. Other paths are left to Spring.
  * `DataConnectAuthenticationEntryPoint`: wraps the standard bearer-token entry point, so status and `WWW-Authenticate` are unchanged, and adds an `ErrorResponse` body for Data Connect paths only. Used by the custom-security and fallback chains.
  * `DataConnectAccessDeniedHandler`: 403s from the security filters on Data Connect paths, e.g. CSRF rejecting a POST to a path with no Data Connect endpoint. It's registered with `defaultAccessDeniedHandlerFor(..., /ga4gh/data-connect/**)`, so other paths keep the default handling.
  * `DataConnectErrorWriter`: writes an `ErrorResponse` body directly, for the resolver, entry point and access-denied handler.
* `GlobalExceptionHandler`'s 503 handler from Task 9 was removed, since Data Connect was its only user; the file is back to its committed version.
* **Verified locally** (every case below returned the expected status, title and headers):
  * 400 "Invalid query": parse error, `pg_sleep`, parameter count, an unknown column reported by Postgres;
  * 400 "Invalid request": blank `query` (`query: must not be blank`), invalid JSON, missing body, bad `page_token`;
  * 404 "Table not found": restricted and unknown tables in `/info`, `/data` and `/search`; 404 "Search not found"; 404 "Not found" for an unknown path (with a token);
  * 405 with `Allow: POST`; 415 for a non-JSON body; 401 for invalid and missing tokens, keeping `WWW-Authenticate`; 403 for CSRF rejection;
  * 500: forced by briefly revoking `data_connect_reader`'s `SELECT` on `tag` (restored immediately). The body is `"Internal server error"` only, and the server log has the full cause (`permission denied for table tag`);
  * DRS and Refget errors are unchanged (`ErrorDetails` body; DRS 401 still has an empty body).
* **`api.yaml` validation (one-off):** 21 real responses were captured from the running app, covering every operation and every error status above. They were validated against `api.yaml` (develop) with `openapi-schema-validator` in a temporary Python environment; nothing was added to the repo. The two remote schemas it references were fetched: service-info's `Service`, and the draft-07 meta-schema for `data_model`.
  * **All 21 pass.** Every `data_model` is valid draft-07 JSON Schema, and every data row matches its `data_model` (columns from `ga4gh_type()` are skipped, since their `$ref` points at external schemas).
  * The validator was itself checked against deliberately bad responses: `TableData` without `data`, and a `bigint` column returned as a number, both fail.
  * **Spec gap:** `api.yaml` doesn't mark `ErrorResponse.errors` as required, so any JSON object passes as an error body; the old `ErrorDetails` format would too. The check therefore also requires a non-empty `errors` list of titled errors. That check fails the old format and passes all our error responses.
  * **Statuses `api.yaml` doesn't list** (not failures; the bodies still use `ErrorResponse`): 400 on `/table/{table_name}/data` (bad `page_token`); 401 anywhere (the spec declares bearer auth but no 401 response); 503 on `/search`; 405 and 415.
  * **Different meaning of 404 on `/search`:** `api.yaml` uses it for "this server does not implement search". We return 404 for a table that's missing or inaccessible, matching how `/info` and `/data` use it.
* **Tests:** 95 pass. The new handlers need a running app and database, so they were verified locally as above; automated coverage belongs with the Task 11 conformance and integration work.

### Task 11: Integration, Docs and Cross-Repo Work (POSTPONED, DRAFT)

> **⏸ POSTPONED to a separate branch:** the testing library will be built comprehensively there, so this task is out of scope for `ja-iss-28-implement-data-connect-v1`.

> **⚠️ DRAFT: Task 11 is AI-proposed and incomplete.** They are an outline for planning and may be modified, reordered, merged or removed before implementation. Treat each task as a starting point to review, not a finalized requirement.

Carried over from Tasks 7–10, for the testing branch:

* Automated tests for the parts verified only by hand against a local database: `DataConnectQueryExecutor` and `QueryValueConverter` (7b), `/table/{table_name}/data` (8), the search service, storage, caps and follow-up page rules (9), and the Data Connect error handlers (10).
* A repeatable `api.yaml` conformance test, replacing Task 10's one-off validation (its script wasn't kept). It should also enforce the intended `ErrorResponse` shape, since `api.yaml` doesn't require `errors`.
* Add the `query.search.*` settings (`max-rows`, `max-bytes`, `ttl`, `storage-max-bytes`) to the README configuration table.

* Optional end-to-end integration test. Testcontainers would be a new dependency and needs sign-off.
* Update the README and config docs.
* Changes in other repos:
  * **CLI:** seed the standard `data_connect_table_type` rows and the `datasets` table row.
  * **Onboarding script:** create a `data_connect_table` row when a dataset is loaded. Table names must follow the naming rule: lowercase, `.`/`-` → `_`, no part starting with a digit.
* Spec clean-up: Task 1 and High-Level Tasks still say `/tables/{id}/...`. Update them to match the Data Connect spec's `/table/{table_name}/...`.
