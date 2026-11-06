# Feature Spec: Replace the `/drs` page with a Data Connect-backed `/data-connect` page

**Branch:** `ja-iss-39-data-connect-page`

**Linked GitHub Issue:** [Issue 39](https://github.com/ga4gh/ga4gh-reference-cloud/issues/39)

## Objective

Replace the researcher UI's `/drs` page with a `/data-connect` page that browses any Data Connect table the researcher can access. Columns come from the table's `data_model` instead of being hardcoded, so the page renders every table type (`datasets`, `drs_object`, `refget_sequence`, and future types). Each row links out to the GA4GH API that suits its object type (DRS for DRS objects, refget for sequences).

## Context

* Current `/drs` page: `components/pages/drs/DrsClient.tsx` (dataset dropdown, approved datasets only) and `DrsManifestTable.tsx`. It calls core-api `GET /datasets/{id}/manifests` (page/size pagination) and has hardcoded manifest columns and a DRS object modal. The sidebar links to it as "DRS" (`components/layout/Sidebar.tsx`).
* Data Connect in core-api (see `claude/specs/archive/0001-implement-data-connect-v1.md`):
  * `GET /ga4gh/data-connect/v1/tables` lists the tables visible to the caller: anonymous callers see open tables, and a passport adds tables in datasets the user is approved for. Each entry includes its full filled-in `data_model`.
  * `GET /table/{table_name}/data` returns `data_model`, `data`, and `pagination.next_page_url` (forward-only, opaque `page_token`; no total count). Pages are 100 rows, ordered by `id`.
  * Table names: `refcloud.<dataset_id, with . and - replaced by _>.<table_type>`, plus the platform-wide `refcloud.platform.datasets`.
  * Table types and their columns are seeded by `apps/admin-cli` (`resources/seed/data-connect/table-types/`).
* `data_connect_table_type.data_model_template` was `jsonb`, which reorders object keys, so data model `properties` came back in Postgres key order, not the authored order (an accepted limitation in spec 0001). Task 4 changes it to `json`.

## Engineering Tasks

### Task 1: UI cleanup

* Currently, the sidebar UI lists greyed out links for other GA4GH APIs like "Beacon," "htsget," and "refget." These should be removed.
* The current "GA4GH APIs" section in the sidebar should be removed. The "DRS" linked should be moved to the "Data Access" section and renamed to "Explore Data." Instead of going to `/drs`, the link goes to `/data-connect`.
* Move the existing `/drs` page to `/data-connect` as-is (`app/drs` → `app/data-connect`, `components/pages/drs` → `components/pages/data-connect`). Later tasks build the Data Connect functionality on top of it.
* Remove `/drs` from the hydra OAuth client's (`ga4gh-reference-cloud-researcher-client`) redirect URIs in `scripts/refcloud-setup.sh`. `/passport/callback` is the only callback the UI uses, so `/data-connect` is not added. Existing installs pick this up by running setup with `--reset`.
* Keep the disabled "OpenAPI Reference" and "Manage Profile" sidebar items; those pages come later.
* Docs references to `/drs` are handled in the Docs task.

The rest of this spec will focus on implementing this `/data-connect` page.

### Task 2: Expose each dataset's tables for the two dropdowns

* The UI needs to know which tables belong to which dataset and what type each table is. Parsing table names isn't reliable: the dataset ID → table name mapping is lossy (`.`/`-` become `_`), and the type is only implied by the name suffix.
* Add a `tables` list to core-api's existing `GET /datasets` response (`DatasetResponseDTO`): `[{ "name": "refcloud.<...>.drs_object", "type": "drs_object" }]`, filtered with the same visibility rule as Data Connect `/tables` (`DataConnectTableRepository.VISIBLE_TO_USER_FROM_WHERE`), so a dataset only lists tables the caller can read.
* Add a `hasManifests` boolean to each dataset in the same response: `true` if the dataset has at least one `drs_object` with `is_manifest = true`. It is shown regardless of the caller's access. The UI uses it to decide whether to offer the "DRS IDs by Manifest" view.
* Both fields are returned by `GET /datasets` and `GET /datasets/{id}` only. They are omitted from `POST /datasets/{id}/request-access`, which shares the DTO.
* Field names are camelCase to match the rest of `DatasetResponseDTO`.
* Data Connect responses stay spec-only; the dataset ↔ table relationship lives on the refcloud-specific API.

### Task 3: Open datasets readable anonymously via the manifests endpoint

* `GET /datasets/{id}/manifests` currently requires an approved passport (`GA4GHPassportTokenEvaluator.canAccessDataset`), while Data Connect also serves open datasets to anonymous callers. Align the manifests endpoint with Data Connect: open datasets are readable anonymously, and other datasets still require approval.
* `canAccessDataset` is only used by this endpoint, so it can be changed in place (follow the anonymous-allowed pattern of the refget/Data Connect evaluator methods).
* Responses: unknown dataset → 404; restricted dataset → 401 for anonymous callers, 403 for signed-in callers without approval.
* Update the UI proxy (`app/api/datasets/[id]/manifests/route.ts`) to forward requests without an `access_token` cookie anonymously instead of returning 500.

### Task 4: Preserve authored column order in data models

* Change `data_connect_table_type.data_model_template` from `jsonb` to `json` with a Liquibase changeset, so `properties` keep their authored order. `data_connect_search` already stores data models as `json` for the same reason.
* Converting in place keeps the already-reordered content, so existing local databases need `./scripts/refcloud-setup.sh --reset` (which migrates and re-seeds) to restore the authored order. Note this in the CLI seed README and `apps/core-api/CLAUDE.md` (no changeset comment needed).
* Verify `/info`, `/tables` and `/data` return `properties` in the order of each `data-model.json`.
* Row order is unaffected (see **Design Notes**).

### Task 5: Data Connect proxy routes in the UI

* Add Next.js API routes that forward to core-api Data Connect with the passport token from the `access_token` cookie (`Authorization: Bearer`), following the pattern of `app/api/datasets/[id]/manifests/route.ts`. Requests without a token are forwarded anonymously.
  * `GET /api/data-connect/table/[name]/data?page_token=...`
* The UI never follows `next_page_url` directly. It takes `page_token` from it and calls its own proxy, so the absolute URL's host doesn't matter to the browser.
* Only the `/data` route is needed: Task 2 supplies each dataset's tables, and `/data` returns the `data_model` with every page. Statuses and Data Connect `ErrorResponse` bodies are passed through unchanged (502 if core-api is unreachable).
* An invalid or expired passport cookie gets a 401 from core-api, even for open tables. Pass it through and show the error; a more graceful fallback is left for a later branch.
* `pagination` is omitted entirely when there is no next page, so the client must treat a missing `pagination` as the last page.
* Fix the public URLs in `application.yml` for native mode (Helm already sets both from `baseURL`):
  * `ga4gh.refcloud.data-connect.base-url`: `http://127.0.0.1:8080` → `https://refcloud.ga4gh.localhost` (used in `$id` and `next_page_url`).
  * `ga4gh.refcloud.drs.scheme` / `host-domain`: `http` / `127.0.0.1:8080` → `https` / `refcloud.ga4gh.localhost` (used in `drs_uri` and manifest access URLs).
* Allow anonymous access to DRS objects in open datasets, matching refget and Data Connect: `canAccessDrsObject` accepts anonymous callers (restricted objects still require approval), `GET /ga4gh/drs/v1/objects/{id}/manifest-content` joins the `@PreAuthorize` chain, and the UI DRS proxy (`app/api/ga4gh/drs/v1/objects/[id]/route.ts`) forwards anonymously instead of returning 500.

### Task 6: `/data-connect` page: dataset and table selection

* Route `app/data-connect/page.tsx` (moved from `/drs` in Task 1; sidebar entry "Explore Data").
* Dropdown 1 lists the datasets from `/api/datasets` that have at least one visible table (open datasets and datasets the user is approved for), plus a "Platform" entry for the platform-wide `refcloud.platform.datasets` table.
* Dropdown 2 lists the selected dataset's tables by type (e.g. "DRS Objects", "Refget Sequences"). If there is only one option, select it automatically.
* For a dataset with a `drs_object` table, dropdown 2 has two DRS options:
  1. **DRS Objects**: the Data Connect `drs_object` table, rendered dynamically (see Task 7).
  2. **DRS IDs by Manifest**: the existing `DrsManifestTable` (one row per manifest, one column per sub-file DRS ID), backed by core-api `GET /datasets/{id}/manifests` rather than Data Connect. Shown only when the dataset's `hasManifests` is `true`. It keeps its own page/size pagination.
* Changing either dropdown resets paging.

### Task 7: Dynamic table rendering

* Columns come from `data_model.properties`, in order. Header = property name; the property's `description` is shown as a tooltip.
* Cell rendering by value:
  * scalars as text (`bigint` values arrive as strings, per the Data Connect type mapping);
  * `null` as an empty cell;
  * arrays/objects as a collapsed JSON preview that expands in place.
* Paging: Next/Previous only. The client keeps a stack of page tokens for Previous, and there is no page count or "last page" button.
* Loading, empty and error states; an error must not crash the page (same guard as `DatasetsClient` in spec 0003).
* Presentation:
  * Reuse the manifest table's styling: the table CSS (renamed `ga4gh-drs-table` → `ga4gh-data-table`, shared by both tables), sticky header and row-number column, stacked-card mobile layout (`data-label`), and the `ga4gh-pagination` arrows.
  * Row numbers start at 1 and continue across pages (page 2 starts at 101). The DRS manifest table is changed to match (it numbered each page from 0).
  * Column descriptions use the native `title` tooltip plus an info icon, since daisyUI tooltips are clipped by the scrolling table.
  * Arrays/objects show a one-line JSON preview truncated to 40 characters; clicking expands pretty-printed JSON in the cell. Empty `[]`/`{}` show as plain text. Booleans show as `true`/`false`; timestamps as returned (ISO 8601).
* Replaces the interim raw JSON view from Task 6.

### Task 8: Type-specific data access links

* A per-table-type "renderer" registry, keyed by the table `type` from the dataset `tables` list (Task 2), decides which columns become links and what they open. Unknown types fall back to plain rendering.
  * **`drs_object`:** `id` opens the existing DRS object modal (`GET /ga4gh/drs/v1/objects/{id}` via the UI's DRS proxy), including the download button (`DrsObjectDownloadButton.tsx`). Manifest contents are browsed via the "DRS IDs by Manifest" option, so the modal doesn't need to show them separately.
  * **`refget_sequence`:** `id` (`SQ.<…>`) opens a refget modal showing `GET /ga4gh/refget/v2/sequence/{id}/metadata`, with an action to fetch the sequence (`/sequence/{id}`, with a `start`/`end` range, since sequences can be large). `drs_uri` also opens the DRS modal.
  * **`datasets`:** no links.
* Add a UI proxy route for refget if the API requires a passport for non-open sequences (it allows anonymous access to open sequences).
* Decisions:
  * The registry lives in `components/pages/data-connect/tableRenderers.tsx`: `table type → column → renderer(value, row, actions)`. A renderer returning `undefined` falls back to the default cell rendering.
  * `drs_uri` opens the DRS modal in both the `drs_object` and `refget_sequence` tables (the ID is the path after `drs://<host>/`).
  * The DRS object modal and download button move to shared components now (`components/drs/`), since both tables and the manifest view use them. The refget modal is `components/refget/RefgetSequenceModal.tsx`.
  * The refget modal shows the metadata plus Start/End inputs (0-based, end-exclusive, validated against the sequence length) and a Fetch button; the first 1,000 bases are fetched when it opens. There is no full-sequence download.
  * UI proxy routes (forward the passport if present, otherwise anonymous): `/api/ga4gh/refget/v2/sequence/[id]` (streams the sequence, passes `start`/`end`), `/api/ga4gh/refget/v2/sequence/[id]/metadata`, and `/api/ga4gh/drs/v1/objects/[id]/manifest-content`.
  * Manifest downloads go through the manifest-content proxy (with a `Content-Disposition` filename of the manifest's DRS ID), because the manifest access URL requires a passport that a plain browser link can't send.

### Task 9: Clean up the old DRS page code

* Delete `components/pages/data-connect/DrsClient.tsx` (its approved-only dataset dropdown is replaced by the new dataset/table dropdowns).
* Keep `DrsManifestTable.tsx`, the `app/api/datasets/[id]/manifests` proxy route, and core-api's `GET /datasets/{id}/manifests` / `getDrsObjectManifestsForDataset`: they back the "DRS IDs by Manifest" option.
* The DRS object modal and download button were already moved into shared components (`components/drs/`) in Task 8.

### Task 10: Docs

* Update `apps/researcher-ui/CLAUDE.md` with the Data Connect proxy routes, the renderer registry, and the "DRS IDs by Manifest" view (and that it uses the manifests endpoint, not Data Connect).
* Update references to the old `/drs` page (e.g. `apps/docs/docs/interactive-demos/explore-1000-genomes-data-via-drs-api.mdx`) to `/data-connect`.
* Note the `jsonb` → `json` change and the need to re-seed in `apps/admin-cli` and `apps/core-api` docs (done in Task 4).
* The 1000 Genomes demo links to `/data-connect?dataset=aws_opendata_registry_1000_genomes&table=drs-manifests` and tells readers to choose **DRS IDs by Manifest**. Its screenshot (`static/img/refcloud-1000-genomes-drs-object-table.jpg`) still shows the old `/drs` page and will be replaced separately.

## Design Notes

**Column order vs row order (answers "will order be consistent across pages and calls?")**

* **Columns:** Task 4 makes column order match the authored `data-model.json` and stay the same on every call.
* **Rows:** already deterministic. `/table/{name}/data` runs `ORDER BY id` with OFFSET paging, so the same page token returns the same rows, and pages neither overlap nor skip rows. Rows can only shift if the dataset is re-onboarded or changed between page requests, because OFFSET counts rows at request time. That is acceptable for a browse page.

## Open Questions

Defaults in **bold** will be used if not answered.

1. Should Task 2 extend `GET /datasets` with `tables`, or add a separate endpoint (e.g. `GET /datasets/{id}/tables`)? **Extend `/datasets`: the page already loads it, and it saves a request per dataset.**
2. Should the "Platform" entry (the `datasets` table) be in the first dropdown? **Yes, listed first.**
3. Open datasets appear on `/data-connect` because Data Connect already serves them, which partly overlaps the future open-access ticket. Is that OK? **Yes.**
4. Is a refget sequence fetch without a range allowed in the UI? **No: the UI always requests a range (default the first 1,000 bases), with an option to download the full sequence.**
