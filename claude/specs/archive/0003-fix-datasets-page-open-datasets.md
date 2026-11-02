# Feature Spec: Fix the researcher UI `/datasets` page for open datasets

**Branch:** ja-iss-36-fix-datasets-page

**Linked GitHub Issue:** [Issue 36](https://github.com/ga4gh/ga4gh-reference-cloud/issues/36)

## Objective

The `/datasets` page in `apps/researcher-ui` errors since the data model changes made in the Data Connect work. Make the page render correctly for both controlled-access datasets (with a visa) and open datasets (no visa).

## Context

* core-api `GET /datasets` returns `List<DatasetResponseDTO>` (`apps/core-api/.../core/dataset/DatasetService.java`, `convertToResponseDto`).
* Since the Data Connect changes, a dataset may have no passport visa ("open" dataset, e.g. `ga4gh_refget_sequences_v2_conformance`). For these, core-api returns `"visa": null`:

  ```json
  { "id": "...", "name": "...", "description": "...", "tags": ["..."], "visa": null }
  ```

  For controlled datasets, `visa` is an object and `visa.assertion` is `null` until the user requests access.
* The UI type in `components/pages/datasets/DatasetsClient.tsx` declares `visa` as always present, and `getValidAssertionStatus` reads `dataset.visa.assertion` unconditionally. Any open dataset throws `TypeError: Cannot read properties of null (reading 'assertion')`, which breaks the whole page.

### Related config bug (native mode)

* `apps/researcher-ui/.env.development` sets `REFCLOUD_API_BASE_URL=https://refcloud.ga4gh.localhost`. The UI's server-side routes call `${REFCLOUD_API_BASE_URL}/datasets`, but on that host `/datasets` is the UI's own page, not core-api (the Caddyfile only routes `/ga4gh/*` and `/webhook/kratos/*` to core-api). The response is HTML, `result.json()` yields `null`, and the client then fails on `result.map`.
* The Helm chart already does this correctly: `REFCLOUD_API_BASE_URL=http://refcloud-api:8080`.

## Engineering Tasks

### Task 1: Point the UI's server-side API calls directly at core-api in native mode

* Set `REFCLOUD_API_BASE_URL=http://127.0.0.1:8080` in `apps/researcher-ui/.env.development`, matching how `HYDRA_ADMIN_API_BASE_URL` points at Hydra admin directly.
* Do not add a `/datasets` route to the Caddyfile: it would collide with the UI page of the same path.
* Verify `/api/datasets`, `/api/datasets/[id]/request-access`, `/api/datasets/[id]/manifests` and `/api/ga4gh/drs/v1/objects/[id]` all reach core-api.

### Task 2: Update the UI `Dataset` type to match the API

* Make `visa` nullable (`visa: {...} | null`) and `visa.assertion` nullable (`assertion: {...} | null`) in `DatasetsClient.tsx`.
* Fix every place that dereferences `visa` so TypeScript catches any remaining unsafe access.

### Task 3: Render open datasets

* `getValidAssertionStatus` (or its replacement) handles three cases: open dataset, controlled with no assertion (`NotRequested`), controlled with an assertion (its `currentStatus`).
* Open datasets show an "Open Access" badge instead of an access status, and no "Request Access" button.
* Controlled datasets render exactly as they do today.

### Task 4: Harden the datasets fetch

* In `DatasetsClient.tsx`, only replace the dataset map when the response is an array; otherwise log the error and keep the current state, so a bad backend response doesn't crash the page.

### Task 5: Fix passport token validation in core-api (native mode)

* Found while testing the `/drs` page: loading manifests failed because core-api rejected every passport token.
* Hydra's issuer is `https://refcloud.ga4gh.localhost` (`deploy/local/ory/hydra/hydra.yml`), but `apps/core-api/src/main/resources/application.yml` expected `http://127.0.0.1:4444`, so the `iss` check failed.
* Set `issuer-uri` to the public Hydra URL and add `jwk-set-uri: http://127.0.0.1:4444/.well-known/jwks.json`, matching the Helm chart (public issuer, internal JWKS). With `jwk-set-uri` set, Spring validates `iss` without calling discovery on the public URL, so core-api doesn't need to trust Caddy's local CA.

## Open Questions

Defaults in **bold** will be used if not answered.

1. How should open datasets be labelled? **An "Open Access" badge styled like the `Approved` badge, in place of the access status.**
2. Should "Open Access" be added to the `PassportVisaAssertionStatus` enum, or kept as a separate UI-only state? **Separate UI-only state: the enum mirrors the backend's assertion statuses, and open datasets have no assertion.**
3. Is anything else on the page affected by the data model changes (e.g. the manifests view for open datasets, where core-api's `canAccessDataset` check applies)? **Out of scope for this spec unless you've seen it fail.**
