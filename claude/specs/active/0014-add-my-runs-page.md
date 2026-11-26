# Feature Spec: My Runs: following compute runs in the researcher UI

**Branch:** `ja-iss-66-my-runs-page`

**Linked GitHub Issue:** [Issue 66](https://github.com/ga4gh/ga4gh-reference-cloud/issues/66)

## Objective

Give researchers a page in the researcher UI where they can follow and manage the compute runs they submitted through WES. This is item 4 of the [milestones](../../../docs/milestones/2026-12-18-Compute-Milestones.md). Today a researcher can see their runs only through the WES API (or the demo script).

**Done when** a researcher, signed in to the researcher UI with an approved compute platform visa, can:

1. **list their runs** across the compute platforms they have access to, with each run's platform, workflow, state, and start and end times;
2. **open a run** and see:
   * its state and timeline, refreshing while it's active;
   * its workflow and parameters;
   * its tasks, each with its state;
3. **read the logs:** the run's own (Nextflow's stdout and stderr) and each task's (its executors' stdout and stderr);
4. **download its outputs;**
5. **cancel it** while it's queued or running.

This works locally (both modes) and on staging, the static analysis passes, and the docs site describes the page.

**Out of scope: submitting runs from the UI** (decided 2026-11-13, milestones, Scope Decisions). Submission stays with WES: the API, the demo script and the docs' interactive demo. Each workflow takes its own inputs, and not every workflow describes them in a schema a form could use. See Future Directions.

## Context

**Source of truth:** the compute discussion doc (D1–D23) and specs 0008, 0011, 0012 and 0013.

**Found 2026-11-26** (reading the researcher UI and core-api):

* **WES already serves almost everything the page needs** (`WesRunController`, per platform, at `/ga4gh/wes/{platformId}/v1`):
  * `GET /runs` (paged): the caller's runs on that platform. Each has `run_id`, `state`, `start_time`, `end_time` and `tags`, but not its workflow;
  * `GET /runs/{id}`: the run's request (its `workflow_url`, parameters and tags), state, `run_log` (start and end, exit code, and `stdout`/`stderr` URLs), `task_logs_url`, and `outputs` (each file's path, size and download URL);
  * `GET /runs/{id}/status`;
  * `GET /runs/{id}/tasks` (paged): each task's ID, name, command, start and end, exit code and `tes_uri`. No state, and no stdout or stderr;
  * `GET /runs/{id}/logs/{stdout|stderr}`: the engine's console output, as plain text;
  * `GET /runs/{id}/outputs/{path}`: a file, as an attachment;
  * `POST /runs/{id}/cancel`.
* **WES's access rules:**
  * every path needs a passport carrying the platform's visa (`canAccessComputePlatform`), and only a run's owner sees it;
  * listing and submitting need the platform enabled. A run's own paths also work on a disabled platform, for its owner (spec 0008, option b);
  * so a researcher whose platform visa is revoked can't see their runs on it, and runs on a disabled platform can't be listed.
* **TES gives the rest, read-only** (`TesTaskController`): `GET /ga4gh/tes/{platformId}/v1/tasks/{id}` with `view=FULL` has the task's state and each executor's stdout, stderr and exit code (`TesTaskViews`; `BASIC` leaves the output out). The task's `tes_uri` in WES points there. Researchers can only read TES; they submit through WES (spec 0008).
* **The researcher UI already talks to GA4GH APIs with the researcher's passport:**
  * every page gets one in the background (`PassportTokenContext`: a hidden OAuth flow to Hydra), kept in an HttpOnly `access_token` cookie that lasts an hour and is renewed on page loads;
  * the browser never calls core-api directly: Next.js API routes forward GA4GH requests with `Authorization: Bearer <passport>` (`apps/researcher-ui/CLAUDE.md`, "GA4GH API proxy routes"). Data Connect, DRS and refget work this way;
  * downloads go through a proxy route too, since a plain link can't send the passport (the DRS manifest download does this).
* **The Compute Platforms page** (`/compute-platforms`) uses the researcher's Kratos session instead, through core-api's refcloud endpoint `GET /compute-platforms`. That lists the enabled platforms, with the researcher's access status. The sidebar's "Compute" section has only that page.
* **Logs' sizes:**
  * the engine's stdout and stderr are whole files, growing while the run lasts;
  * an executor's stdout and stderr in TES are Funnel's tail of them, so they stay small.

## Proposed design (settled in the tasks)

* **The UI as a WES and TES client**, through passport proxy routes like Data Connect's: no new core-api endpoints. That also shows a GA4GH API in use from a real client.
* **The runs list:**
  * the platforms where the researcher's access is approved, from `/api/compute-platforms`;
  * then each one's `GET /runs`, merged newest first, with the platform as a column.
* **A run's page** (`/workflow-runs/{platformId}/{runId}`):
  * the run log, refreshed every few seconds while the run is active;
  * its tasks, each with its state from TES;
  * the run's and each task's logs;
  * its outputs, each a download through a proxy route;
  * a cancel button with a confirmation.
* **The sidebar:** "My Runs" in the Compute section.
* **The docs site:** a page in its Compute section.

## Questions (to settle before Task 1)

See the conversation; answers are recorded under each task's Decisions.

## Engineering Tasks

### Task 1: The API path and any core-api changes

* How the UI reaches runs (the proposed design, or refcloud endpoints with the Kratos session).
* What the runs list shows, given that WES's run summaries carry no workflow.
* Where tasks' state and logs come from (TES, or new WES fields).
* Runs on disabled platforms, and after a visa is revoked.
* Whatever core-api changes the answers need, with tests.

**Decisions (Task 1):**

Agreed 2026-11-26:
1. **The UI is a WES and TES client,** with the researcher's passport, through Next.js proxy routes, as Data Connect and DRS are. No refcloud endpoints of its own for runs. The list asks each platform the researcher can use, and merges the results.
2. **WES's run summaries gain `workflow_url`** (`GET /runs`), read from the run's record, so the list shows each run's workflow without a request per run. That's one field beyond WES's schema, which clients ignore. Fetching each run's full log instead would cost a request per row, each listing the run's outputs in storage.
3. **Tasks' state and output come from TES's FULL view,** through a TES proxy route: no core-api change. Researchers can already read TES, and Funnel keeps only the tail of each executor's output.
4. **Runs the researcher can't reach any more aren't listed:** those on disabled platforms, and those after the researcher's platform visa is revoked. That's acceptable: platforms are rarely disabled, and revoking a visa is meant to cut access. (A run's own WES paths still work on a disabled platform, for its owner, with the visa.)

Settled without a question, unless the user objects:
* an active run's state refreshes every 5 seconds; its logs when they're opened, or on request;
* a run's own logs show their last part, with a download of the whole file.

Built 2026-11-26:

5. **`GET /runs` returns each run's `workflow_url`.** It's read from the run's stored request (`WesViews.RunSummary`, `WesRunService.runSummary`), and is left out when a run has none.
   * A unit test checks a summary's workflow and tags, and that a run without a request has neither.
   * core-api's 330 tests and its static analysis pass.
   * The docs site's compute overview lists it under "Non-Standard Extensions".

### Task 2: Proxy routes

Next.js API routes forwarding WES's and TES's paths with the passport, as the existing GA4GH proxies do: statuses and error bodies passed through, `502` only when core-api is unreachable, and downloads streamed with their `Content-Disposition`. Documented in `apps/researcher-ui/CLAUDE.md`.

**Decisions (Task 2):**

Built 2026-11-26:
1. **Eight routes,** under `/api/ga4gh/wes/[platformId]/v1/runs/…` and `/api/ga4gh/tes/[platformId]/v1/tasks/[id]`:
   * WES: list runs, a run's log, its status, its tasks, cancel (the only POST), the engine's stdout and stderr, and output files;
   * TES: one task, with `view` limited to `MINIMAL`, `BASIC` or `FULL`.

   There's no route for `POST /runs`, since the UI doesn't submit runs.
2. **A shared helper, `utils/api/ga4ghProxy.ts`,** which the existing GA4GH proxies don't have (each repeats its own forwarding):
   * `forwardToCoreApi` adds the passport from the `access_token` cookie, passes only the query parameters a route names, and passes statuses and JSON bodies through. It streams logs and outputs with their `Content-Type` and `Content-Disposition`, and answers `502` when core-api can't be reached;
   * `encodeSegments` encodes each path segment and refuses empty, `.` and `..` ones, which a URL would resolve away (`400`);
   * the logs route takes only `stdout` and `stderr`. With `?download=1` it names the file `<run id>-<stream>.log`.
3. **Cancel and CSRF:** the passport cookie is `SameSite=Lax`, so another site's form can't post here with it.
4. **Checked:**
   * against a stand-in core-api on localhost, with `next dev` (both stopped afterwards):
     * paths, the passport header and the allowed query parameters reach core-api, and other parameters don't;
     * cancel is a POST;
     * logs stream as text, and download under their name with `download=1`;
     * an output keeps core-api's file name;
     * a `404` passes through with its body;
     * an unknown log stream or view gets `400`; Next.js itself refuses a `..` segment;
     * with no cookie the request goes without credentials;
     * with core-api down, `502`;
   * the UI's static analysis passes: Prettier, ESLint, types, and Semgrep with 0 findings;
   * `apps/researcher-ui/CLAUDE.md` lists the routes.

### Task 3: The runs list

The page, its table, paging, and empty and error states (no approved platform, no runs, a platform that's unreachable). The sidebar entry.

**Decisions (Task 3):**

Agreed 2026-11-26 (the user):
1. **The page is `/workflow-runs`, labelled "View Workflow Runs"** in the sidebar's Compute section, below "Browse Compute Platforms". (This supersedes the proposed `/my-runs`; a run's page follows it, at `/workflow-runs/{platformId}/{runId}`.)
2. **One table, not one per platform,** with these columns in this order:
   * the run (a short ID, linking to its page);
   * the workflow, by its allowlist name (the `workflow_url` when there's none);
   * the platform's name;
   * the state, as a coloured pill;
   * when it started and ended, or how long it's been running.

   Runs from every platform interleave by time, not grouped by platform.
3. **Paging:** each platform's latest 20, merged, with "Load more" while any platform has more.
4. **Refreshing:** every 10 seconds while any listed run isn't finished: `QUEUED`, `INITIALIZING`, `RUNNING` or `CANCELING`. WES has no `PENDING`. Finished runs are `COMPLETE`, `EXECUTOR_ERROR`, `SYSTEM_ERROR` and `CANCELED`.
5. **When there's nothing to show:**
   * no approved platform: a link to Browse Compute Platforms to request access;
   * no runs: a pointer to the docs on submitting through WES;
   * a platform that doesn't answer: a notice for it, with the other platforms' runs still listed.
6. **No filters in this task** (Future Directions).
7. **WES's run summaries also gain `submitted_time`,** which is what each platform's `GET /runs` is ordered by (newest first). `start_time` can't serve: it's empty while a run is queued, and set when it starts. So lists from several platforms merge in the same order, and the list shows "Submitted …" for a queued run (agreed 2026-11-26).

Built 2026-11-26:

8. **core-api:** `submitted_time`, from the run's `created_at`, next to `workflow_url`.
   * The summary's unit test covers it.
   * core-api's 330 tests and its static analysis pass.
   * The docs site's compute overview lists both fields under "Non-Standard Extensions".
9. **The page:** `app/workflow-runs/page.tsx`. It checks the Kratos session and fetches the platforms on the server, as Browse Compute Platforms does, and hands them to `components/pages/workflow-runs/WorkflowRunsClient.tsx`.
   * **Fetching:** once the passport is in place (`usePassportToken`), it fetches each approved platform's first 20 runs through the WES proxy.
   * **Merging** (`utils/workflowRuns.ts`, `mergeRuns`): newest first, by submission time compared as a time (timestamps' fractional seconds vary in length, so their strings don't sort), then by run ID. While a platform has more pages, only runs at least as new as its oldest loaded run are shown, the newest such limit among those platforms, so "Load more" never adds a run above one already shown. A platform that failed doesn't hold the others back.
   * **Load more** fetches the next page from every platform that has more.
   * **Refresh:** every 10 seconds while any listed run isn't finished, each platform's newest page is folded in. Updated runs replace their old copies, new runs join, and the paging position stays, since WES's page tokens point at a run. A platform that had failed starts over from its fresh page.
   * **The table:**
     * the run's short ID, linking to `/workflow-runs/{platformId}/{runId}` (Task 4);
     * the workflow, by its allowlist name, or its URL;
     * the platform's name;
     * the state, as a pill: in progress amber, complete green, errors red, cancelled grey;
     * the time: "Submitted …" while queued; once started, "Started …" and "Ended … (duration)", or "Running for …".
   * **Notices:**
     * no approved platform: a link to Browse Compute Platforms;
     * no runs: a link to the docs' "Run a Workflow Via WES";
     * a platform that failed: a warning naming it, with the rest still listed;
     * the platforms themselves failing: an error.
   * **The sidebar:** "View Workflow Runs", below "Browse Compute Platforms".
10. **Checked:**
    * the UI's lint, types and formatting pass;
    * the merge, time and state helpers pass a scratch check with sample data (run with Node from the TypeScript source): interleaving, the paging limit, a failed platform, fractional seconds, durations, finished states.

    The rendered page is checked in Task 5, with the run's page: a headless browser pass, and the demo followed locally and on staging.

### Task 4: A run's page

* the state, timeline, workflow and parameters;
* the tasks;
* the logs: the run's, and each task's, with a download for the run's;
* the outputs;
* cancel;
* refreshing while the run is active.

**Decisions (Task 4):**

### Task 5: Docs and checks

* **The docs site:** a My Runs page in the Compute section.
* **The READMEs and `CLAUDE.md` files.**
* **The checks:**
  * the UI's static analysis;
  * a headless browser pass over the pages;
  * the demo run followed from the page, locally in both modes and on staging: list, details, logs, outputs and cancel.

**Decisions (Task 5):**

## Future Directions

* **Submitting runs from the UI** (decided out of scope 2026-11-13; revisit later). Each workflow takes its own inputs, and not every workflow describes them in a schema a form could use. A middle ground: offer a form only for allowlisted workflows that ship a parameter schema (e.g. nf-core's `nextflow_schema.json`), starting with the bundled `drs-checksum`, which takes a single DRS input. Every workflow a platform runs is admin-approved (D20), and admins fix how it runs (D21), so the form only needs the parameters.
* **Filters on the workflow runs list** (Task 3, decision 6): by state, platform or workflow, when researchers have enough runs to need them.
* **Runs the researcher can't reach any more** (Task 1, decision 4): those on disabled platforms, or after their platform visa is revoked, aren't listed. If owners should always see their own runs, core-api would let them list and read those runs without the visa.
