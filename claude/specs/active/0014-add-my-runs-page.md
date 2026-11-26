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

Agreed 2026-11-26 (the user):
1. **One page with sections,** in this order:
   * **Overview:** the run ID in full (copyable), the state, platform and workflow (name and URL), when it was submitted, started and ended, with the duration; then its parameters and tags;
   * **Outputs:** each file's path and size, with a download;
   * **Tasks:** each task's name, state (from TES), start, end and exit code. Expanding one shows its executors' stdout and stderr, fetched then;
   * **Logs:** the run's stdout and stderr;
   * **Cancel,** while the run is queued, initializing or running, with a confirmation.

   Refreshing: the state and tasks every 5 seconds while the run is unfinished; logs on request.
2. **WES's run log also gains `submitted_time`,** as the run summaries did (Task 3, decision 7). WES's run log has only start and end times, so a queued run would otherwise show none.
3. **Logs show their last 500 lines,** and the page says so when a log is longer. A Download button saves the whole file.

Built 2026-11-26:

4. **core-api:** the run log's `submitted_time`, from the run's `created_at` (`WesViews.RunLog`, `WesRunService.getRunLog`).
   * core-api's 330 tests and its static analysis pass.
   * The docs site's compute overview lists it under "Non-Standard Extensions".
5. **The page:** `app/workflow-runs/[platformId]/[runId]/page.tsx` checks the Kratos session and fetches the platforms on the server, as the list does. It hands them to `components/pages/workflow-runs/WorkflowRunClient.tsx`, which fetches the run log through the WES proxy once the passport is in place. The platforms only name things: a run on a platform that isn't listed (e.g. disabled) shows the platform's ID and the workflow's URL.
   * **Sections:**
     * a link back to the list, then the overview in a card:
       * the workflow's allowlist name (or URL) as its title;
       * the state pill, and the engine's exit code once there is one;
       * the run ID (copyable) and the platform;
       * when it was submitted, started and ended, and its duration ("… so far" while running);
       * the `workflow_url` (copyable) with its type and version;
       * the parameters as formatted JSON ("None: the workflow's defaults" when empty);
       * the tags, when there are any;
     * then Outputs, Tasks (`RunTasksSection.tsx`) and Logs (`RunLogsSection.tsx`).
   * **Cancel** (`CancelRunButton.tsx`) sits in the overview's title row while the run is `QUEUED`, `INITIALIZING` or `RUNNING`. It opens a dialog ("Cancel this run? Its tasks are stopped, and the run ends as Cancelled…") with "Keep running" and "Cancel run". Once the platform accepts, the run log is fetched again, showing Cancelling. A refusal shows in the dialog with core-api's message.
   * **Refreshing:** while the run isn't finished, its status every 5 seconds, and the whole run log again when the state changes. Outputs, which come with the run log, therefore appear when the run ends, not as each file is written. Durations count up between checks.
   * **Errors:**
     * not found (`404`): "No such run…: it may not exist, or it may not be one of yours";
     * `401`: the passport couldn't be verified (reload);
     * `403`: no access to the platform any more;
     * unreachable: try again later.

     A refresh that fails keeps the run shown, with a warning.
6. **Outputs:** each file's path, size (in kB, MB, GB) and a Download link. The link goes through this app's outputs proxy (`outputDownloadPath`, each path segment encoded), since core-api's own `url` needs the passport, which a plain link can't send. The empty state says "No outputs yet: they're listed here when the run ends", or "This run has no outputs" once it's finished.
7. **Tasks:**
   * **The table:** WES's task list, 50 at a time and newest first, with "Load more tasks". Its columns are the task's name, its state from TES (`MINIMAL` view), its start and end (or "Running for …"), its exit code, and a "Show output" toggle.
   * **An opened task** fetches TES's `FULL` view and shows:
     * its TES task ID and URL (copyable, for use with the TES API);
     * for each attempt's executors, the exit code, stdout and stderr. Each shows its last 500 lines, and says so when there were more. The page notes that TES keeps only the end of each executor's output.
   * **Refreshing:** every 5 seconds while the run, or any task shown, isn't finished. Each refresh folds in the newest page and fetches TES's copy of only the unfinished tasks: the `FULL` view of opened ones, the state of the rest.
8. **Logs:** stdout and stderr as tabs, with Refresh and "Download the whole log" (the logs proxy's `?download=1`).
   * Only the last 500 lines are shown (`lastLines`), and the view starts scrolled to the end.
   * Above a longer log, in bold: "Showing only the last 500 of N lines. Download the whole log to see the rest." Otherwise: "All N lines".
   * Beyond "on request" (decision 1), the shown log is also fetched again when the run's state changes, so it's complete when the run ends.
   * The whole log still reaches the browser, which keeps its last 500 lines. Engine logs are small, but a very large one would be slow to load (Future Directions).
9. **Smaller points:**
   * **The fetching pattern:** the effects fetch, then set state in their callbacks. React's `set-state-in-effect` lint rule refuses effects that call state-setting helpers directly, and it's satisfied without suppressing it.
   * **A fix on the list page:** its "Load more" button now wraps its text in `btn-text`, as the app's other `ga4gh-btn-dark` buttons do. Without it, the text was the same colour as the hover background.
10. **Checked:**
    * the UI's Prettier, ESLint and types pass, and Semgrep finds nothing (0 findings);
    * `next build` passes, with both pages and all the proxy routes;
    * the log tail, size, output path and cancellable-state helpers pass a scratch check with sample data (18 checks). These include a 1,234-line log showing lines 735 to 1,234, exactly 500 lines not counted as cut, and a path with a space and `#`.

    The rendered page is checked in Task 5, with the list.

### Task 5: Docs and checks

* **The docs site:** a My Runs page in the Compute section.
* **The READMEs and `CLAUDE.md` files.**
* **The checks:**
  * the UI's static analysis;
  * a headless browser pass over the pages;
  * the demo run followed from the page, locally in both modes and on staging: list, details, logs, outputs and cancel.

**Decisions (Task 5):**

Agreed 2026-11-26 (the user):
1. **The docs page:** "Following Your Workflow Runs" (`compute/workflow-runs.mdx`), in the Compute section after "Accessing Compute Platforms". It covers:
   * the list and a run's page, section by section;
   * the 500-line limit and the downloads;
   * cancelling;
   * which WES or TES call each part makes.

   Links to it go in the WES demo ("Following the Run", the Conclusion and Additional Reading), "Accessing Compute Platforms" and the Compute Overview. The overview's "hosted instance doesn't offer compute yet" note stays until the production rollout.
2. **No screenshots for now:** they come with a full docs review in a later task.
3. **The headless check** uses a stand-in stack in the session's scratchpad, never the local refcloud or its database:
   * a fake Kratos, and a fake Hydra so the passport loads;
   * a stand-in core-api with canned WES and TES answers;
   * the UI from `next build` and `next start`, with headless Chrome.

   It covers the list's and the run page's states, the cancel dialog, an unknown run, and a phone-width layout.
4. **The real demo, followed from the pages, by the user:** in local native mode, then local helm mode, then staging, with a checklist:
   * list, details, a task's output, the log tail and download, an output download;
   * cancelling a second run.

   Staging gets this branch's images (core-api's `submitted_time` and the new UI) before the PR, as in spec 0013. The values change is shown before it's written, and the user runs the helm upgrade.
5. **The demo script prints the run's page** (`refcloud-compute-demo.sh`): "follow it in the researcher UI: https://…/workflow-runs/<platform>/<run id>", from the same base URL, so it's right on staging too. Each deploy README's "Running the demo" says the same.
6. **Closing:** once the spec is ready, the git commands to archive it, and the commands to update links to it, are shown to the user to run.

Built 2026-11-26:

7. **The docs site:**
   * **The new page** (`compute/workflow-runs.mdx`, in the sidebar after "Accessing Compute Platforms") covers:
     * the list;
     * a table of run states;
     * each section of a run's page, with the WES and TES requests behind it;
     * the 500-line limit and the downloads;
     * cancelling;
     * what to look at when a run fails.
   * **Links to it:**
     * the WES demo: a tip under "Following the Run", a line in the Conclusion, and Additional Reading;
     * "Accessing Compute Platforms": the WES bullet. It says the UI follows and cancels runs, but doesn't submit them;
     * the Compute Overview: a "Following runs" bullet, and Additional Reading.
   * `npm run build` passes; the site fails on any broken link. Prettier, ESLint and types pass too.
8. **The demo script** prints `follow it in the researcher UI: <base URL>/workflow-runs/<platform>/<run id>` twice: once the run is submitted, and when the demo completes. A failed run also points to its page. The researcher UI shares the deployment's host with the APIs, locally and in the chart's ingress. `deploy/local/README.md` and `deploy/helm/README.md` (both the local demo and the AWS one) mention the page.
9. **Found and fixed during the task:**
   * **The run's reason, `run_log.system_logs`** (e.g. "the engine could not be started…", "canceled by its owner"), now shows in the overview as **Reason**. Task 4 had left it out, so a system error showed no reason.
   * **A task's executor output** now starts scrolled to its end, like the run's logs.
10. **The headless check** ran against stand-ins in the session's scratchpad, never the local refcloud or its database:
    * **The stand-ins:**
      * a fake core-api (compute platforms, WES and TES), whose canned runs include fractional-second timestamps;
      * a fake Kratos (whoami, and the logout flow the layout asks for);
      * a fake Hydra, so the background passport flow completes and sets the `access_token` cookie;
      * a proxy adding the Kratos session cookie.
    * **The driver:** headless Chrome over the DevTools protocol, from Node's built-in WebSocket (nothing installed), against `next build` and `next start`.
    * **The list (all 63 checks pass, with no browser errors):**
      * the passport flow, and each platform's first page;
      * newest first with the platforms interleaved, the older run held back until Load more;
      * names, URLs, states and times;
      * the unapproved platform never asked;
      * Load more;
      * a failing platform's warning, no runs, no access;
      * no sideways scrolling at 390px.
    * **The run's page:**
      * complete: the overview, outputs downloading with segments encoded, the log notice ("Showing only the last 500 of 1,234 lines"), lines 735–1,234 scrolled to the end, the whole log downloading as `<run id>-stdout.log`, a task's TES output with its own 500-line notice, the stderr tab;
      * running: tags, "so far", task states newest first;
      * cancel: the dialog, nothing sent before confirming, one `POST` with the passport, Cancelling then Cancelled with its reason, the task refreshed to Cancelled;
      * queued, system error (its reason), executor error (the task's stderr, the engine's stderr), and an unknown run;
      * 390px wide.
    * **A separate dialog check** (5 checks, all passing): "Keep running" has the initial focus, closes the dialog, and sends nothing.
    * The screenshots were read for layout, at full width and at 390px.

    Every WES and TES call carried the passport, and Semgrep finds nothing (0 findings).
11. **The real demo, followed from the pages** (the user, with the checklist in decision 4):
    * local native mode: passed 2026-11-26;
    * local helm mode: pending;
    * staging: pending.

## Future Directions

* **Submitting runs from the UI** (decided out of scope 2026-11-13; revisit later). Each workflow takes its own inputs, and not every workflow describes them in a schema a form could use. A middle ground: offer a form only for allowlisted workflows that ship a parameter schema (e.g. nf-core's `nextflow_schema.json`), starting with the bundled `drs-checksum`, which takes a single DRS input. Every workflow a platform runs is admin-approved (D20), and admins fix how it runs (D21), so the form only needs the parameters.
* **Filters on the workflow runs list** (Task 3, decision 6): by state, platform or workflow, when researchers have enough runs to need them.
* **Large run logs** (Task 4, decision 8): the run's page fetches a whole log to show its last 500 lines. If engine logs grow large, core-api's log endpoint could take a `tail` parameter (or answer `Range` requests), so the page fetches only what it shows.
* **Runs the researcher can't reach any more** (Task 1, decision 4): those on disabled platforms, or after their platform visa is revoked, aren't listed. If owners should always see their own runs, core-api would let them list and read those runs without the visa.
