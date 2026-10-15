# Data Connect Seed Data

Startup records for the GA4GH Reference Cloud's Data Connect implementation. These files are loaded into the `refcloud-api` database by the CLI (`refcloud seed`). The `refcloud-api` app only reads these tables; it never writes to them.

## Layout

```
data-connect/
├── table-types/                 # one row per directory in data_connect_table_type
│   └── <type id>/
│       ├── table-type.json      # id, description
│       ├── data-model.json      # → data_model_template (jsonb)
│       └── internal-query.sql   # → internal_query (text)
└── tables/                      # one row per file in data_connect_table (platform-wide tables only)
    └── datasets.json
```

Per-dataset tables (`drs_object`, `refget_sequence`) are **not** seeded here; the onboarding script creates a `data_connect_table` row for them when a dataset is loaded.

## Loading

Seed **table types first**: `data_connect_table.table_type` is a foreign key to `data_connect_table_type.id`.

Use an insert-or-update so `refcloud seed` can safely run again after a file changes. The `updated_at` trigger sets the timestamp, and `created_at` is kept:

```sql
INSERT INTO data_connect_table_type (id, description, data_model_template, internal_query)
VALUES ($1, $2, $3::jsonb, $4)
ON CONFLICT (id) DO UPDATE SET
    description = EXCLUDED.description,
    data_model_template = EXCLUDED.data_model_template,
    internal_query = EXCLUDED.internal_query;

INSERT INTO data_connect_table (table_name, dataset_id, table_type, description)
VALUES ($1, $2, $3, $4)
ON CONFLICT (table_name) DO UPDATE SET
    dataset_id = EXCLUDED.dataset_id,
    table_type = EXCLUDED.table_type,
    description = EXCLUDED.description;
```

Load `data-model.json` and `internal-query.sql` verbatim. Do **not** substitute placeholders or parameters; `refcloud-api` does that at runtime.

## Contract with `refcloud-api`

### Data model placeholders (`data-model.json`)

`refcloud-api` replaces these when serving `/table/{table_name}/info` and table data:

| Placeholder | Replaced with |
|---|---|
| `{{refcloud_base_url}}` | Public base URL of the refcloud-api server, e.g. `https://refcloud.example.org` (no trailing slash) |
| `{{table_name}}` | The `data_connect_table.table_name` being served |

### Query bind parameters (`internal-query.sql`)

The query engine binds these named parameters. They are never concatenated into the SQL string:

| Parameter | Bound to | Used by |
|---|---|---|
| `:dataset_id` | `data_connect_table.dataset_id` of the table being queried | `drs_object`, `refget_sequence` |
| `:drs_host_domain` | `ga4gh.refcloud.drs.host-domain` from `application.yml` | `drs_object`, `refget_sequence` (builds `drs_uri`) |

The `datasets` type takes no parameters.

### Query rules

* Each query is a single self-contained `SELECT` with no `ORDER BY` or `LIMIT`. Sorting and paging are applied outside it, using the researcher's query.
* The query's output column names must exactly match the `properties` in its `data-model.json`.
* Arrays and nested objects are returned as `jsonb` and sorted inside the query, so results are stable.

### Data model conventions

* JSON Schema draft-07, matching the Data Connect spec's examples.
* `format` holds the column's SQL type name (`varchar`, `bigint`, `boolean`, `timestamp`, `json`), as in the Data Connect spec's examples.
* Following the Data Connect SQL-to-JSON type mapping, `bigint` columns (`size`, `length`) are declared as `"type": "string"`, and `timestamp` columns are ISO 8601 strings. `refcloud-api` converts values to match.

## Table types

| Type | Scope | Columns |
|---|---|---|
| `datasets` | platform-wide | `id`, `name`, `description`, `tags`, `requires_visa` |
| `drs_object` | per dataset | `id`, `drs_uri`, `name`, `description`, `size`, `mime_type`, `checksums`, `aliases`, `created_time`, `updated_time`, `version`, `is_manifest` |
| `refget_sequence` | per dataset | `id`, `md5`, `trunc512`, `length`, `circular`, `aliases`, `drs_uri` |

Refget sequences are also DRS objects, so they appear in both tables of their dataset. Join them with `refget_sequence.drs_uri = drs_object.drs_uri`.
