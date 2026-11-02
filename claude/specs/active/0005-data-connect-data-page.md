# Feature Spec: Replace the `/drs` page with a Data Connect-backed `/data` page

**Branch:** TBD (after `0004-local-helm-deployment`)

**Linked GitHub Issue:** TBD

## Objective

Replace the researcher UI's `/drs` page with a `/data` page that browses any Data Connect table the researcher can access. Columns come from the table's `data_model` instead of being hardcoded, so the page renders every table type (`datasets`, `drs_object`, `refget_sequence`, and future types). Each row links out to the GA4GH API that suits its object type (DRS for DRS objects, refget for sequences).

## Context

* Current `/drs` page: `components/pages/drs/DrsClient.tsx` (dataset dropdown, approved datasets only) and `DrsManifestTable.tsx`. It calls core-api `GET /datasets/{id}/manifests` (page/size pagination) and has hardcoded manifest columns and a DRS object modal. The sidebar links to it as "DRS" (`components/layout/Sidebar.tsx`).
* Data Connect in core-api (see `claude/specs/archive/0001-implement-data-connect-v1.md`):
  * `GET /ga4gh/data-connect/v1/tables` lists the tables visible to the caller: anonymous callers see open tables, and a passport adds tables in datasets the user is approved for. Each entry includes its full filled-in `data_model`.
  * `GET /table/{table_name}/data` returns `data_model`, `data`, and `pagination.next_page_url` (forward-only, opaque `page_token`; no total count). Pages are 100 rows, ordered by `id`.
  * Table names: `refcloud.<dataset_id, with . and - replaced by _>.<table_type>`, plus the platform-wide `refcloud.platform.datasets`.
  * Table types and their columns are seeded by `apps/admin-cli` (`resources/seed/data-connect/table-types/`).
* `data_connect_table_type.data_model_template` is `jsonb`, which reorders object keys, so data model `properties` come back in Postgres key order, not the authored order (an accepted limitation in spec 0001).

## Engineering Tasks

### Task 1: Expose each dataset's tables for the two dropdowns

* The UI needs to know which tables belong to which dataset and what type each table is. Parsing table names isn't reliable: the dataset ID → table name mapping is lossy (`.`/`-` become `_`), and the type is only implied by the name suffix.
* Add a `tables` list to core-api's existing `GET /datasets` response (`DatasetResponseDTO`): `[{ "name": "refcloud.<...>.drs_object", "type": "drs_object" }]`, filtered with the same visibility rule as Data Connect `/tables` (`DataConnectTableRepository.VISIBLE_TO_USER_FROM_WHERE`), so a dataset only lists tables the caller can read.
* Data Connect responses stay spec-only; the dataset ↔ table relationship lives on the refcloud-specific API.

### Task 2: Preserve authored column order in data models

* Change `data_connect_table_type.data_model_template` from `jsonb` to `json` with a Liquibase changeset, so `properties` keep their authored order. `data_connect_search` already stores data models as `json` for the same reason.
* Converting in place keeps the already-reordered content, so re-run `refcloud seed` afterwards to restore the authored order, and say so in the changeset comment and the CLI/core-api docs.
* Verify `/info`, `/tables` and `/data` return `properties` in the order of each `data-model.json`.
* Row order is unaffected (see **Design Notes**).

### Task 3: Data Connect proxy routes in the UI

* Add Next.js API routes that forward to core-api Data Connect with the passport token from the `access_token` cookie (`Authorization: Bearer`), following the pattern of `app/api/datasets/[id]/manifests/route.ts`. Requests without a token are forwarded anonymously.
  * `GET /api/data-connect/table/[name]/data?page_token=...`
* The UI never follows `next_page_url` directly. It takes `page_token` from it and calls its own proxy, so the absolute URL's host doesn't matter to the browser.
* Fix `ga4gh.refcloud.data-connect.base-url` for native mode: it is currently `http://127.0.0.1:8080`, but it is the *public* base URL used in `$id` and `next_page_url`, so it should be `https://refcloud.ga4gh.localhost`. Check the Helm chart value too.

### Task 4: `/data` page: dataset and table selection

* New route `app/data/page.tsx`, sidebar entry "Data" replacing "DRS".
* Dropdown 1 lists the datasets from `/api/datasets` that have at least one visible table (open datasets and datasets the user is approved for), plus a "Platform" entry for the platform-wide `refcloud.platform.datasets` table.
* Dropdown 2 lists the selected dataset's tables by type (e.g. "DRS Objects", "Refget Sequences"). If there is only one table, select it automatically.
* Changing either dropdown resets paging.

### Task 5: Dynamic table rendering

* Columns come from `data_model.properties`, in order. Header = property name; the property's `description` is shown as a tooltip.
* Cell rendering by value:
  * scalars as text (`bigint` values arrive as strings, per the Data Connect type mapping);
  * `null` as an empty cell;
  * arrays/objects as a collapsed JSON preview that expands in place.
* Paging: Next/Previous only. The client keeps a stack of page tokens for Previous, and there is no page count or "last page" button.
* Loading, empty and error states; an error must not crash the page (same guard as `DatasetsClient` in spec 0003).

### Task 6: Type-specific data access links

* A per-table-type "renderer" registry, keyed by the table `type` from Task 1, decides which columns become links and what they open. Unknown types fall back to plain rendering.
  * **`drs_object`:** `id` opens the existing DRS object modal (`GET /ga4gh/drs/v1/objects/{id}` via the UI's DRS proxy), including the download button (`DrsObjectDownloadButton.tsx`). For rows with `is_manifest = true`, the modal also shows the manifest content (replacing the manifest sub-file columns of the old table).
  * **`refget_sequence`:** `id` (`SQ.<…>`) opens a refget modal showing `GET /ga4gh/refget/v2/sequence/{id}/metadata`, with an action to fetch the sequence (`/sequence/{id}`, with a `start`/`end` range, since sequences can be large). `drs_uri` also opens the DRS modal.
  * **`datasets`:** no links.
* Add a UI proxy route for refget if the API requires a passport for non-open sequences (it allows anonymous access to open sequences).

### Task 7: Remove the old `/drs` page

* Delete `app/drs`, `components/pages/drs/DrsClient.tsx` and `DrsManifestTable.tsx`, plus the `app/api/datasets/[id]/manifests` route, moving anything reused (DRS modal, download button) to shared components.
* Remove core-api's `GET /datasets/{id}/manifests` and `getDrsObjectManifestsForDataset` once nothing else uses them (check `apps/admin-cli` and docs first).

### Task 8: Docs

* Update `apps/researcher-ui/CLAUDE.md` with the Data Connect proxy routes and the renderer registry.
* Note the `jsonb` → `json` change and the need to re-seed in `apps/admin-cli` and `apps/core-api` docs.

## Design Notes

**Column order vs row order (answers "will order be consistent across pages and calls?")**

* **Columns:** Task 2 makes column order match the authored `data-model.json` and stay the same on every call.
* **Rows:** already deterministic. `/table/{name}/data` runs `ORDER BY id` with OFFSET paging, so the same page token returns the same rows, and pages neither overlap nor skip rows. Rows can only shift if the dataset is re-onboarded or changed between page requests, because OFFSET counts rows at request time. That is acceptable for a browse page.

## Open Questions

Defaults in **bold** will be used if not answered.

1. Should Task 1 extend `GET /datasets` with `tables`, or add a separate endpoint (e.g. `GET /datasets/{id}/tables`)? **Extend `/datasets`: the page already loads it, and it saves a request per dataset.**
2. Should the "Platform" entry (the `datasets` table) be in the first dropdown? **Yes, listed first.**
3. Open datasets appear on `/data` because Data Connect already serves them, which partly overlaps the future open-access ticket. Is that OK? **Yes.**
4. Is a refget sequence fetch without a range allowed in the UI? **No: the UI always requests a range (default the first 1,000 bases), with an option to download the full sequence.**
