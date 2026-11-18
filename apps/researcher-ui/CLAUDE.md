# Project Guide: GA4GH Reference Cloud Researcher UI

## Project Overview & Architecture

This repo serves as the researcher UI for the refcloud.

* **Tech Stack:** Next JS, TypeScript, Tailwind

### Directory Layout
* `app/` - Next JS app router
* `components/` - reusable React components
* `public/` - public web/art assets
* `styles/` - Global CSS styles
* `utils/` - resuable functions

---

## Core Commands

### Development & Build

* Start local dev server: `npm run dev`

### Code Quality & Validation

All tools are pinned (`package-lock.json`, and the semgrep version in `package.json`), so local runs match CI (`.github/workflows/static-analysis-researcher-ui.yml`, which also runs weekly). Each command fails on any problem, warnings included.

* Check formatting (Prettier): `npm run format:check`
* Run linter (ESLint, `eslint-config-next` core-web-vitals + typescript): `npm run lint`
* Run type check: `npm run typecheck`
* Audit dependencies (fails at high severity and above, dev dependencies included): `npm run audit:deps`
* Run security rules (Semgrep, via `uvx`, so uv must be installed): `npm run semgrep`
* Fix formatting and auto-fixable lint errors: `npm run format && npm run lint:fix`
* TypeScript runs side by side: `tsc` is TypeScript 7 (aliased as `@typescript/native`), while the `typescript` package is aliased to `@typescript/typescript6`, because `typescript-eslint` (and `next build`'s type check) need the TS 6 API. Don't "fix" the alias until typescript-eslint supports TS 7.
* Prettier formats code only; Markdown and `tsconfig.json` are excluded (`.prettierignore`).
* Don't silence a rule (`eslint-disable`, `@ts-expect-error`) without a `--` reason; prefer a real fix.

### Testing

* TBD: Run all tests
* TBD: Run specific test file

## Things to Note:

* The Hydra admin API (port 4445) is called directly from server-side code and never goes through Caddy.
* Hydra redirect URIs must match the client registration in refcloud-setup.sh exactly.
* The /api/.ory proxy forwards to ORY_SDK_URL.

### GA4GH API proxy routes

The browser never calls core-api's GA4GH APIs directly: the passport lives in the `access_token` cookie, so requests go through Next.js API routes that forward to `REFCLOUD_API_BASE_URL`. Every proxy below sends `Authorization: Bearer <passport>` when the cookie is set and forwards **anonymously** otherwise (core-api then serves only platform-wide data and open datasets). Statuses and error bodies are passed through unchanged; the proxy returns 502 only when core-api is unreachable. An invalid or expired passport gets a 401 from core-api, even for open data.

| UI route | core-api endpoint |
|---|---|
| `/api/data-connect/table/[name]/data?page_token=` | Data Connect `GET /ga4gh/data-connect/v1/table/{name}/data` |
| `/api/ga4gh/drs/v1/objects/[id]` | DRS `GET /ga4gh/drs/v1/objects/{id}` |
| `/api/ga4gh/drs/v1/objects/[id]/manifest-content` | DRS manifest content (adds `Content-Disposition`, so it downloads as a file) |
| `/api/ga4gh/refget/v2/sequence/[id]?start=&end=` | refget sequence, streamed as plain text |
| `/api/ga4gh/refget/v2/sequence/[id]/metadata` | refget sequence metadata |
| `/api/datasets/[id]/manifests?page=&size=` | refcloud `GET /datasets/{id}/manifests` |

* The UI never follows Data Connect's `next_page_url`: it takes the `page_token` from it and calls its own proxy. `pagination` is omitted on the last page.
* Manifest downloads go through the manifest-content proxy (`components/drs/DrsObjectDownloadButton.tsx` rewrites refcloud manifest access URLs to it), because a plain browser link can't send the passport.

### Datasets and compute platforms (Kratos session)

`/datasets` and `/compute-platforms` call core-api's refcloud endpoints with the researcher's **Kratos session** (`X-Session-Token` from the `ory_kratos_session` cookie), not the passport: the server page fetches over `REFCLOUD_API_BASE_URL` (`utils/api/datasets.ts`, `utils/api/computePlatforms.ts`), and the browser goes through `/api/datasets`, `/api/compute-platforms` and their `[id]/request-access` routes.

* Request access returns the dataset or platform with the user's request, new or already existing, so a second click is harmless. Unknown IDs are `404`.
* Visa types, the access-status enum and the status pill classes are shared in `utils/passportVisa.ts`.
* **Browse Compute Platforms** (`components/pages/compute-platforms/`) lists the **enabled** platforms with their health (as in WES/TES service-info), platform type, WES and TES URLs, allowlisted workflows (the exact `workflow_url` to submit), the user's limits and their access status. Every platform has a visa (no open-access compute).
* `EnvProvider` renders the app only after fetching `/api/config/env` on the client, so pages are rendered in the browser, not in the server's HTML.

### Explore Data page (`/data-connect`)

* `components/pages/data-connect/DataConnectClient.tsx` holds the dataset and table dropdowns. Datasets and their readable tables come from `/api/datasets` (`tables`, `hasManifests`); the platform-wide `refcloud.platform.datasets` table is a fixed "Platform" entry. The selection is kept in the URL (`?dataset=<id>&table=<table type | drs-manifests>`).
* Data Connect tables render dynamically in `DataConnectTable.tsx`: columns come from `data_model.properties` in order, with Next/Previous paging only (Data Connect gives no total count).
* **Renderer registry** (`components/pages/data-connect/tableRenderers.tsx`): maps `table type → column → renderer`, deciding which cells become links (`drs_object`/`refget_sequence` `id` and `drs_uri` open the DRS or refget modal). A renderer returning `undefined`, or any unlisted type or column, falls back to the default rendering. Add a table type here to give it links.
* **"DRS IDs by Manifest"** (`DrsManifestTable.tsx`) is not backed by Data Connect: it uses core-api `GET /datasets/{id}/manifests` (via `/api/datasets/[id]/manifests`), because the Data Connect `drs_object` table has no manifest content. It is offered only when the dataset's `hasManifests` is `true` and keeps its own page/size pagination.
* Shared modals: `components/drs/DrsObjectModal.tsx` (with `DrsObjectDownloadButton.tsx`) and `components/refget/RefgetSequenceModal.tsx` (metadata plus start/end slicing).

<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
