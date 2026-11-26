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
* **A run's page** (`/my-runs/{platformId}/{runId}`):
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

### Task 3: The runs list

The page, its table, paging, and empty and error states (no approved platform, no runs, a platform that's unreachable). The sidebar entry.

**Decisions (Task 3):**

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
