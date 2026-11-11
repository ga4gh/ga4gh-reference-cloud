# Feature Spec: Add compute (WES/TES) to the refcloud, stage 1: Nextflow + Funnel (Docker)

**Branch:** `ja-iss-34-add-compute-wes-tes`

**Linked GitHub Issue:** [Issue 34](https://github.com/ga4gh/ga4gh-reference-cloud/issues/34)

## Objective

Add the GA4GH compute standards, the Workflow Execution Service (WES) and the Task Execution Service (TES), to the refcloud. This spec covers stage 1 of the plan in [`claude/discussions/0000-about-adding-compute.md`](../../discussions/0000-about-adding-compute.md): the **Nextflow + Funnel (Docker)** compute platform type (`nextflow-funnel-docker`), running entirely on the developer's machine in **native mode**, with a shared host directory for storage.

The discussion doc is the source of truth for the design. Decisions are referenced below as D1–D20. If a task finds that a decision doesn't hold, record why under that task's **Decisions** and update the discussion doc.

In summary:

* core-api implements its own WES and TES APIs (D1, D4). The request path is `client → refcloud WES → Nextflow → refcloud TES → Funnel → Docker` (D3, D12).
* Admins register **Compute Platforms** (D8). Each one is a catalog and policy record in the database that points at a **connection** defined in deployment config (D9). Each platform is gated by a Passport visa and requested like a dataset (D5, D7).
* Each Compute Platform appears as one WES and one TES service in the service registry, with health in their service-info (D6, D10).
* Connection and per-user limits are enforced at submission (WES) and at task creation (TES) (D11).
* WES stages `drs://` inputs at submission (D17). Outputs stay with the run and are downloaded through a non-standard WES endpoint (D18, D19). There is no write-back to DRS.
* Workflows come only from allowlisted pinned git sources, TRS versions, or bundled demos (D20).

**Done when:** on a fresh native-mode setup (`./scripts/refcloud-setup.sh` then `./scripts/refcloud-run-native.sh`), a researcher with an approved visa for the default platform can:

1. submit the bundled demo workflow through WES with a 1000 Genomes `.bai` DRS object as input;
2. watch it run through the refcloud TES on Funnel;
3. download its report through the output download endpoint.

All of it must use only local resources.

## Context

* [Discussion: adding compute (WES/TES) to the refcloud](../../discussions/0000-about-adding-compute.md), with design decisions D1–D20
* [WES 1.1.0 spec repo](https://github.com/ga4gh/workflow-execution-service-schemas/tree/1.1.0) and [OpenAPI YAML](https://raw.githubusercontent.com/ga4gh/workflow-execution-service-schemas/1.1.0/openapi/workflow_execution_service.openapi.yaml)
* [TES 1.1 spec repo](https://github.com/ga4gh/task-execution-schemas/tree/v1.1) and [OpenAPI YAML](https://raw.githubusercontent.com/ga4gh/task-execution-schemas/v1.1/openapi/task_execution_service.openapi.yaml)
* [TRS 2.0.2 spec repo](https://github.com/ga4gh/tool-registry-service-schemas/tree/2.0.2) and [OpenAPI YAML](https://raw.githubusercontent.com/ga4gh/tool-registry-service-schemas/2.0.2/openapi/openapi.yaml), consumed as a client only (D20)
* [Service-info spec](https://github.com/ga4gh-discovery/ga4gh-service-info), for the health extension property (D10)
* [Nextflow](https://www.nextflow.io/docs/latest/) and its [nf-ga4gh plugin](https://github.com/nextflow-io/nf-ga4gh) (TES executor)
* [Funnel](https://github.com/calypr/funnel) (TES executor; local compute backend)
* Existing code to follow:
  * Auth: `apps/core-api/.../security/SecurityConfig.java`, `GA4GHPassportTokenEvaluator.java`
  * Visas: `apps/core-api/.../passport/`
  * DRS S3 access: `apps/core-api/.../drs/`
  * Service registry: `apps/core-api/.../serviceregistry/`
  * Admin CLI datasets: `apps/admin-cli/src/refcloud/handlers/dataset/`
  * Researcher UI datasets page: `apps/researcher-ui/app/datasets/`

**Request path (stage 1, native mode):**

```
client (researcher, curl, WES client)
   │  WES 1.1 over HTTPS, Hydra JWT
   ▼
core-api: refcloud WES  (/ga4gh/wes/{platformId}/v1)
   │  checks visa, allowlist, limits; stages drs:// inputs; then starts a process on the host
   ▼
nextflow  ◄── nf-ga4gh plugin loaded inside this process (adds the `tes` executor)
   │  TES 1.1 over HTTP (create task, poll status, cancel), run-scoped bearer token
   ▼
core-api: refcloud TES  (/ga4gh/tes/{platformId}/v1)
   │  checks credential and limits, records usage; forwards over TES
   ▼
Funnel  (Docker Compose container, local compute backend)
   │  docker run, through the host's Docker socket
   ▼
task containers  (sibling containers on the host's Docker)
```

* **Nextflow** and its **nf-ga4gh** plugin run **natively on the host**, one process per WES run. Nextflow has no built-in TES support; nf-ga4gh is Nextflow's official GA4GH plugin and adds the `tes` executor, which turns each workflow step into a TES task. Without it, Nextflow would run tasks directly with Docker and bypass TES (D3).
* **Funnel** runs in a **Docker container** and doesn't need nf-ga4gh: it's a TES server that accepts TES requests from any client.
* Files move through the shared storage root (`compute/nextflow-funnel-docker/`): Nextflow's work directory is there, and Funnel copies task inputs and outputs in and out of it.

**WES 1.1.0 endpoints** (each under `/ga4gh/wes/{platformId}/v1`; the platform ID comes before the version, D6):

* `GET /service-info`
* `GET /runs` and `POST /runs` (`multipart/form-data`)
* `GET /runs/{run_id}`
* `GET /runs/{run_id}/status`
* `GET /runs/{run_id}/tasks` and `GET /runs/{run_id}/tasks/{task_id}`
* `POST /runs/{run_id}/cancel`
* Non-standard (D19): `GET /runs/{run_id}/outputs/{path}`

**TES 1.1 endpoints** (each under `/ga4gh/tes/{platformId}/v1`; the platform ID comes before the version, D6):

* `GET /service-info`
* `GET /tasks` and `POST /tasks`
* `GET /tasks/{id}`
* `POST /tasks/{id}:cancel`

## Engineering Tasks

### Task 1: Spike: verify the stage 1 stack

Prove the risky parts of the stack before building on them. The spike is throwaway: keep scripts and config in a scratch location, not in the repo. Record findings under **Decisions (Task 1)** and in the discussion doc's "To Verify (spike)" list. If a finding changes a decision, update the discussion doc and the affected tasks below before starting Task 2.

Verify (discussion doc items 1, 2, 4, 5, 6; items 3 and 7 belong to stage 2):

* **Run-scoped TES credential (D12):** nf-ga4gh can call a TES endpoint with a bearer token (or other credential) supplied per `nextflow run`. Confirm the exact config keys and how the token reaches the request. If per-run credentials aren't possible, stop and revisit D12 before continuing.
* **Funnel in Docker Compose (D12):** Funnel runs as a Compose service using its local compute backend, launching task containers through the mounted Docker socket. The task work directory must be mounted at the **same absolute path** inside the Funnel container as on the host. Confirm this works under Docker Desktop file sharing on macOS with a path inside the repo (`compute/nextflow-funnel-docker/`).
* **File staging (D12, D13):** how Nextflow's TES executor passes inputs and outputs to Funnel through the shared work directory (e.g. `file://` URLs), and which Funnel storage settings (e.g. allowed directories) must be set.
* **arm64:** pinned Funnel and Nextflow versions run on arm64 Macs, natively or under emulation. Note the versions to pin.
* **TRS resolution (D20):** a pinned Dockstore Nextflow workflow version can be turned into something `nextflow run` can execute. Compare fetching descriptor files through TRS with resolving the version to its source repository and commit. Pick one approach.
* **End-to-end smoke test:** a hand-written `main.nf` that checksums one file, run with `nextflow run` against Funnel's TES directly (no refcloud code), completes and writes its output to the shared directory.

**Decisions (Task 1):**

Spike run on 2026-11-11 on an arm64 Mac (macOS 26.7, Docker Desktop 29.4, Java 25). Scripts and config were kept in the session scratch directory. Test data is under `compute/nextflow-funnel-docker/` (git-ignored by `compute/.gitignore`, created early so spike data can't be committed).

* **Versions to pin.** All three run on arm64.
  * Nextflow **26.10.5**, which runs on Java 25.
  * nf-ga4gh **1.5.0**.
  * Funnel **0.11.14**, image `quay.io/ohsu-comp-bio/funnel:0.11.14`, multi-arch including arm64. v0.12.3 (released 2026-11-09) wasn't needed.
* **Funnel image.** The official image has no `docker` CLI, but the local compute backend shells out to it (`Worker.Container.DriverCommand: docker`). Compose builds a one-line derived image: `FROM quay.io/ohsu-comp-bio/funnel:0.11.14` plus `RUN apk add --no-cache docker-cli`.
* **Funnel in Compose works.**
  * Setup: the Docker socket is mounted, and the storage root is mounted at the **same absolute path** inside the container as on the host. Docker Desktop's default file sharing covers the repo path.
  * Config: `Compute: local`, BoltDB at `<root>/funnel/db/funnel.db`, `Worker.WorkDir: <root>/funnel/work`, `LocalStorage.AllowedDirs: [<root>]`. HTTP on 8000, RPC on 9090.
  * `AllowedDirs` is enforced: an input of `file:///etc/hosts` fails with `SYSTEM_ERROR`.
  * Output files on the host are owned by the host user.
* **Writable `/tmp`.** Funnel's default `RunCommand` runs tasks with `docker run --read-only`, so FastQC (Java) failed trying to write `/tmp`. Override `Worker.Container.RunCommand` with Funnel's default template plus `--tmpfs /tmp:rw,exec,size=1g`. The root filesystem stays read-only.
* **File staging.**
  * nf-ga4gh sends TES inputs and outputs as **plain absolute host paths** (no `file://`) under the work directory. Funnel's local storage **copies** them into and out of its own task directory; it doesn't bind-mount the work directory.
  * Only declared outputs come back: `.command.out`, `.command.err`, `.command.log`, `.exitcode`, and the process's `path` outputs.
  * Nextflow downloads `https://` inputs itself into `work/stage-*` on the host before sending the task.
* **Task images need `bash`.** Nextflow's task wrapper runs `bash .command.run`, and `alpine` failed with exit 127. The bundled demo (Task 9) uses `ubuntu:24.04`.
* **Run-scoped credential works.**
  * Setting `tes.oauthToken = System.getenv('REFCLOUD_TES_TOKEN')` in the run's config sends `Authorization: Bearer <token>` on **every** TES request: create, each status poll, and cancel. The token is never written to disk.
  * Two concurrent runs kept separate tokens.
  * `tes.tags` is passed through in the task body (e.g. `refcloud_run`).
* **Endpoint path (affects D6).** nf-ga4gh appends `/v1/tasks` to `tes.endpoint`. With `tes.endpoint = http://host/ga4gh/tes/demo-platform`, it requests `/ga4gh/tes/demo-platform/v1/tasks`. So the platform ID must come **before** the version. The refcloud TES has to be at `/ga4gh/tes/{platformId}/v1/...`, not `/ga4gh/tes/v1/{platformId}/...`. **Applied:** D6 now puts the platform ID before the version for both WES and TES (`/ga4gh/wes/{platformId}/v1`, `/ga4gh/tes/{platformId}/v1`), and the tasks below are updated.
* **Cancel.** `SIGTERM` to the `nextflow` process makes it send `POST /v1/tasks/{id}:cancel` (with the run token) for its running tasks. Funnel then cancels them and removes the containers. So WES cancel can signal Nextflow, as long as the run token is revoked only **after** Nextflow exits.
* **Plugin loading.**
  * Declaring `plugins { id 'nf-ga4gh@1.5.0' }` in a `-c` config, or passing `-plugins nf-ga4gh@1.5.0`, **replaces** the pipeline's own plugins. nf-core/demo lost `nf-schema` and failed.
  * `NXF_PLUGINS_DEFAULT=nf-ga4gh@1.5.0` **adds** the plugin to the pipeline's plugins (`resolved requirement=[nf-schema@2.7.2, nf-ga4gh@1.5.0]`). Use that.
  * `process.executor = 'tes'` alone doesn't load nf-ga4gh.
* **TRS resolution: resolve to git, not descriptor files.**
  * Dockstore's TRS file list for Nextflow workflows is incomplete. For nf-core/demo 1.2.0, `/files` (and `?format=zip`) has 20 of the repo's 121 files, and the `subworkflows/` that `main.nf` includes are missing. Another entry lists `../../` paths. So the descriptor-file approach is rejected.
  * Git approach:
    1. The version's `/NFL/descriptor` response has a `url` like `https://raw.githubusercontent.com/nf-core/demo/1.2.0//nextflow.config`, which gives the repository and tag.
    2. When the allowlist entry is added, resolve the tag to a commit with `git ls-remote` (`1.2.0` → `32893af…`).
    3. At run time, shallow-clone the tag into the run directory, check that `HEAD` matches the pinned commit, then `nextflow run <clone>`.
  * For Nextflow entries on Dockstore, the primary descriptor is `nextflow.config`, not `main.nf`.
  * Dockstore's TRS `offset` is a page number (see the `next_page` header).
* **nf-ga4gh gap: `env`/`eval` outputs break.**
  * nf-ga4gh 1.5.0 doesn't declare `.command.env` as a TES output (`TesTaskHandler.groovy`), so Nextflow `env`/`eval` outputs never come back from Funnel.
  * Current nf-core modules report tool versions with `eval` (topic channels), so **nf-core/demo 1.2.0 fails** (`No such file or directory: .../.command.env`).
  * **nf-core/demo 1.0.2**, which reports versions with `versions.yml` files, **runs end to end**: 7 TES tasks, about 60 s, with FastQC, seqtk, MultiQC and a MultiQC report.
  * `.command.trace` isn't returned either, so per-task resource metrics are missing from Nextflow's trace.
* **Fix verified for upstream (D22).** A four-line patch to `TesTaskHandler.groovy` adds `.command.env` to the TES outputs whenever the step declares `env` or `eval` outputs. With the patched plugin, **nf-core/demo 1.2.0 runs end to end**: 8 TES tasks, about 56 s, and the `eval`-captured versions (`seqtk: 1.4-r122`, `fastqc: 0.12.1`, `cowpy: 1.1.5`) appear in its versions report. All 58 of nf-ga4gh's existing tests pass. We'll submit it upstream (issue + PR to `nextflow-io/nf-ga4gh`) rather than ship a fork. The PR should also add a unit test for the new output. Patch:

  ```diff
  --- a/src/main/groovy/nextflow/ga4gh/tes/executor/TesTaskHandler.groovy
  +++ b/src/main/groovy/nextflow/ga4gh/tes/executor/TesTaskHandler.groovy
  @@ -249,6 +249,10 @@ class TesTaskHandler extends TaskHandler {
           body.addOutputsItem(outItem(errorFile.name, 'FILE'))
           body.addOutputsItem(outItem(logFile.name, 'FILE'))
           body.addOutputsItem(outItem(exitFile.name, 'FILE'))
  +        // `env` and `eval` outputs are written by the task wrapper to .command.env, which
  +        // must be copied back as well, otherwise Nextflow cannot collect them
  +        if( task.getOutputEnvNames() || task.getOutputEvals() )
  +            body.addOutputsItem(outItem(TaskRun.CMD_ENV, 'FILE'))
  
           // set requested resources
           body.setResources(getResources(task.config))
  ```

* **Workflow compatibility** with nf-ga4gh 1.5.0 + Funnel 0.11.14 (`nextflow-funnel-docker`), tested 2026-11-11:

  | Workflow | Source | Uses `env`/`eval` outputs | Engine settings needed | Result |
  |---|---|---|---|---|
  | Spike `drs-checksum` prototype (the bundled demo in Task 9) | bundled | No | none | ✅ Works |
  | nf-core/demo 1.0.2 | Dockstore TRS → git `db7f526` | No (`versions.yml` files) | `-profile test`, `NXF_SYNTAX_PARSER=v1` | ✅ Works (7 tasks, ~60 s) |
  | nf-core/demo 1.2.0 | Dockstore TRS → git `32893af` | Yes (`eval` for tool versions) | `-profile test` | ❌ Fails on released nf-ga4gh 1.5.0; ✅ works with the D22 patch |
* **Nextflow 26 config parser.** Nextflow 26.10's strict config parser rejects nf-core/demo 1.0.2's config (``validation` is not defined``). It runs with `NXF_SYNTAX_PARSER=v1`. Some engine settings (syntax parser, profile, e.g. `-profile test`) therefore depend on the workflow version.
* **Emulation.** The amd64-only biocontainer images (FastQC, seqtk, MultiQC) ran under Docker Desktop emulation on arm64 without problems.
* **Not verified (left to Task 7):** a natively running Nextflow calling the refcloud TES through Caddy (`https://refcloud.ga4gh.localhost`) needs the JVM to trust Caddy's local CA. The likely simpler choice is for Nextflow to call core-api's internal HTTP port directly in native mode.

### Task 2: Let visas gate Compute Platforms, and add visa admin commands (D7)

D7 (revised 2026-11-11): a visa points at the one resource it gates. It belongs to **exactly one** dataset or Compute Platform, never neither and never both. The database enforces this with a check constraint. Existing production data must be preserved, so every schema change is a **new** Liquibase changeset that builds on the existing ones (no edits to applied changesets, no reliance on `--reset`).

* ~~Add a new Liquibase changeset that drops `NOT NULL` from `passport_visa.dataset_id`.~~ Moved to Task 3 (see Decisions below).
* ~~Update the `PassportVisa` entity for the nullable column.~~ No change needed (see Decisions below). Dataset access checks (`DatasetService`, `DrsObjectService`, `RefgetSequenceService`, the `DataConnectTableRepository` visibility queries, the request-access flow) keep joining on `passport_visa.dataset_id`, and their behavior must not change.
* Keep today's lifecycle: the admin CLI creates a visa together with its resource and deletes it with the resource.
* **`refcloud visa` command group** in the admin CLI. Today requests are approved by hand with a raw SQL `UPDATE`; these commands replace that. They work on any visa, so they cover datasets now and Compute Platforms after Task 3.
  * `list`: every visa in the refcloud, independent of access requests. Shows ID, name, description, what it gates (the dataset, later the Compute Platform), and a count of requests by status.
  * `list-requests`: access requests (`passport_user_visa_assertion` rows) with user ID, visa ID and name, status and status time. Filterable by status (default `Requested`) and by visa.
  * `approve`, `deny`, `revoke`: set a user's assertion status for a visa and update `current_status_at`. Reject transitions that make no sense (e.g. revoking a request that was never approved) with a clear message.
  * Users are shown by their user ID (the token `sub`), not by personal details.
  * No expiry for now; the `Expired` status stays unused.
  * Document the commands in `apps/admin-cli/README.md` and `apps/admin-cli/CLAUDE.md`.
* Verify: the migration applies cleanly on a database built **before** this change (not only after `--reset`), and the datasets page, DRS, refget and Data Connect access checks behave as before.

**Decisions (Task 2):**

* **The migration moved to Task 3.** Dropping `NOT NULL` from `passport_visa.dataset_id` now happens in Task 3's changeset, together with adding `compute_platform_id` and the "exactly one of `dataset_id` / `compute_platform_id`" check constraint. Doing it here would have left the database, for a commit, in a state where a visa could gate nothing. Task 2 has no schema change.
* **No core-api change.** `PassportVisa.dataset` is a `@OneToOne` with a default (optional) `@JoinColumn`, so it already maps a nullable `dataset_id`. Dataset access checks keep joining on `passport_visa.dataset_id` and are untouched.
* **`refcloud visa` commands** (`apps/admin-cli/src/refcloud/handlers/visa/`):
  * `list`: every visa with ID, name, description, what it gates (`dataset: <id> (<name>)`; Task 3 adds Compute Platforms), and request counts for Requested / Approved / Denied / Revoked.
  * `list-requests`: user ID, visa ID and name, status, and status time, oldest first. `-s` filters by status (default `Requested`; `all` for every status) and `-v` by visa. An unknown visa is an error.
  * `approve` / `deny` / `revoke` (`-u USER_ID -v VISA_ID`). Allowed transitions:
    * approve: from Requested, Denied or Revoked → Approved. Researchers can't re-request after a denial or revocation (core-api ignores a repeat request), so approve is how access is restored.
    * deny: from Requested → Denied.
    * revoke: from Approved → Revoked.

    Anything else is refused with a message naming the current status, and nothing changes. A missing request or unknown visa is an error.
  * The status update is a single conditional `UPDATE ... WHERE current_status IN (...)`, so two admins acting at once can't both apply a transition.
  * `NotRequested` and `Expired` are never set. There's no expiry (as agreed).
  * Users are shown by user ID only.
* **Timestamps are UTC with a time zone everywhere** (done in this task at the user's request, replacing the earlier local-time approach):
  * **Migration:** one new changeset (`modify-type-all-timestamp-columns-timestamptz-utc`) converts all nine timestamp columns to `TIMESTAMP WITH TIME ZONE`: `drs_object.created_time`/`updated_time`, `passport_user_visa_assertion.current_status_at`, `data_connect_table_type.created_at`/`updated_at`, `data_connect_table.created_at`/`updated_at`, `data_connect_search.created_at`/`expires_at`. Existing values are read as UTC (`USING col AT TIME ZONE 'UTC'`), the zone of the deployed apps and database. Data written in another local zone (e.g. a laptop in EST) shifts by that zone's offset; locally, `--reset` rebuilds it. It's a single changeset, so Postgres applies all columns or none, and it has a matching rollback.
  * **core-api:** these entity fields, their DTOs and `ErrorDetails.timestamp` are `Instant` instead of `LocalDateTime`. JSON output is now RFC 3339 UTC (e.g. DRS `"created_time": "2026-01-15T12:00:00Z"`). Before, it had no offset, which the DRS spec's `date-time` fields don't allow. The Data Connect value converter and search data model already handled `timestamptz`.
  * **admin CLI:** database sessions run in UTC (`-c timezone=UTC`). Onboarding stores S3 `LastModified` as the timezone-aware datetime boto3 returns, instead of a zone-less string. `seed`, `onboard` and the refget manifest use `datetime.now(datetime.UTC)`. Visa status changes use `now()`, and `list-requests` prints times as `YYYY-MM-DD HH:MM:SS UTC`.
  * **Data Connect seed:** the `drs_object` data model declares `created_time`/`updated_time` as `"format": "timestamp with time zone"`, matching what `/search` reports. **After deploying, run `refcloud seed`** so existing table types pick up the new data model.
  * **Verified** on a throwaway Postgres whose server zone was America/Toronto (to catch zone mistakes):
    * the first 48 changesets applied; pre-change rows inserted; the new changeset applied (values kept, read as UTC); rollback and re-apply both round-trip;
    * the admin CLI `seed` and `visa` commands write and print UTC;
    * core-api (run against it on port 18080) returned UTC `Z` timestamps for DRS objects, error bodies, Data Connect table data and `/search`, with an `updated_time` entered as `17:30-05` correctly returned as `22:30Z`.

    `./gradlew staticAnalysis` passes (the SpotBugs `CorsConfig` detector crash is pre-existing), and all 95 core-api tests pass.
* **README typo fixed:** the admin CLI README's `dataset delete` example showed `dataset reset`.
* **Prod rollout notes (parked, not yet acted on):**
  * Prod currently has only DRS and Passport data, so the migration affects existing rows only in `drs_object` and `passport_user_visa_assertion`.
  * `drs_object` times come from S3 `LastModified` (UTC), so they're kept exactly.
  * `current_status_at` is exact only if it was written in UTC. Before upgrading, check `SHOW timezone;` and look at the visa rows: approvals were made with raw SQL, possibly with hand-typed local times. Fix any outliers with a one-off `UPDATE`; the shift is cosmetic either way.
  * Applying it: DB snapshot → `helm upgrade` (the `refcloud-api-db-migration` pre-upgrade hook runs the changeset before the new pods start) → `kubectl exec deploy/refcloud-admin-cli -- refcloud seed`.
  * Not yet checked: whether old core-api pods (`LocalDateTime`) can read `timestamptz` columns during the rollout window. Until it's checked, treat the upgrade as a short maintenance window.
* **Tested** against a throwaway Postgres 18 container (port 55432) with every changeset applied and fake users and visas. Not run against the local refcloud database, so real local data was untouched. Checked:
  * `list` and `list-requests`, including the filters and an unknown visa;
  * every allowed transition and every refused one (already approved, deny an approved request, revoke a denied request, no request, unknown visa, missing option).

  `ruff format`, `ruff check` and `pyright` are clean.
* **Docs:** `apps/admin-cli/README.md` ("Visa Commands") and `apps/admin-cli/CLAUDE.md`.

### Task 3: Connections config and the Compute Platform data model (D9, D11, D14, D15, D20)

* **Connections** live in core-api config (`ga4gh.refcloud.compute.connections.<key>`), loaded into a `@ConfigurationProperties` record and overridable through environment variables like other config. Each connection has:
  * `type`: the platform type, `nextflow-funnel-docker` only in stage 1. An unknown type fails startup, since that's a deployer error.
  * Engine launch settings: path to the `nextflow` executable, Nextflow and nf-ga4gh versions.
  * Executor settings: Funnel TES URL and its service credential, if any.
  * `storage`: `type: shared-filesystem` (the only value in stage 1, but the setting exists from the start, D15) and `root` (default for local: `<repo>/compute/nextflow-funnel-docker`).
  * Connection (pooled) limits from the D11 table: total concurrent runs, total tasks / CPU / memory in flight, the largest per-task CPU, RAM and disk, the longest run duration, and an optional monthly compute-hours budget.
* **Database tables** (Liquibase), with names to be confirmed during the task:
  * `compute_platform`: id, name, description, `connection_key`, enabled, and the per-user limits from the D11 table (concurrent runs, per-task CPU/RAM/disk, max run duration, monthly compute-hours quota).
  * `passport_visa` changes (D7), in **one new changeset** so the database never allows a visa that gates nothing:
    * drop `NOT NULL` from `passport_visa.dataset_id` (moved here from Task 2);
    * add `passport_visa.compute_platform_id` (FK to `compute_platform.id`, `ON DELETE CASCADE` like `dataset_id`, unique, nullable);
    * add a check constraint that exactly one of `dataset_id` and `compute_platform_id` is set.

    Verify it applies to an existing database with data. The admin CLI creates the platform's visa together with the platform. Extend `refcloud visa list` to show `compute platform: <id> (<name>)` in its GATES column.
  * `compute_platform_workflow`: the allowlist (D20). Each entry has a source kind (`git`, `trs`, `bundled`), the exact `workflow_url` a submission must match, a name and description, the workflow type and version, and the resolution data needed to run it (repo plus pinned revision plus main script path; TRS server, tool ID and version; or bundled path).
    * Each entry also holds its **engine settings** (D21), set by the admin: e.g. Nextflow profiles (`-profile test`) and engine environment variables such as `NXF_SYNTAX_PARSER=v1`. Researchers can't change them.
  * `compute_run`: run ID, platform, user (`sub`), state, the WES request, start and end times, a hash of the run-scoped TES credential (D12), and the run directory.
  * `compute_task`: a TES task in the refcloud linked to the run (nullable for direct TES clients), the user, the Funnel task ID, state, requested resources, start and end times, and computed compute-hours.
* **Ceiling rule (D11):** a platform's per-user limits can't exceed its connection's ceilings. The admin CLI writes to the database directly and can't see core-api's config, so core-api always applies the lower of the two at runtime. Pick how the CLI validates and record it here. Options: the CLI reads the same connection config, or core-api reports out-of-range limits through health/logs and clamps them.
* A `compute_platform` whose `connection_key` isn't in config is reported unhealthy (D10). It doesn't fail startup.

**Decisions (Task 3):**

### Task 4: Admin CLI: register and manage Compute Platforms (D5, D9, D20)

* Add a `refcloud compute-platform` command group, modeled on `refcloud dataset`:
  * `init`: create a platform with name, description, connection key, per-user limits, and a new visa.
  * `delete` and `list`.
  * `enable` / `disable`.
  * `allowlist add` / `allowlist remove`, for git, TRS and bundled entries.
* Validate limit values and allowlist entries: git entries must be pinned to a tag or commit, not a branch; TRS entries must name a version.
* Access requests are approved with the generic `refcloud visa` commands from Task 2.
* Document the commands in `apps/admin-cli/README.md` and `apps/admin-cli/CLAUDE.md`.

**Decisions (Task 4):**

### Task 5: WES and TES service-info, health, and a dynamic service registry (D6, D10)

* For each Compute Platform, serve `GET /ga4gh/wes/{platformId}/v1/service-info` and `GET /ga4gh/tes/{platformId}/v1/service-info`:
  * Follow the respective specs: WES `workflow_type_versions`, `workflow_engine_versions` and so on; TES `storage` and so on.
  * Build them from the platform record plus its connection.
  * Add the health extension property: `"health": { "status": "healthy" | "unhealthy", "checkedAt": "<ISO 8601>" }`.
* **Health checker:** a `@Scheduled` job checks every connection that backs a platform:
  * Funnel's TES `service-info` is reachable;
  * the `nextflow` executable is present and runs;
  * the storage root is writable.

  Platforms on the same connection share its result. Connection health stays out of `/actuator/health/liveness` and `/readiness`.
* **503 rule:** every WES/TES request to an unhealthy or disabled platform returns `503` with a GA4GH error body, except `service-info`. Unknown platform IDs return `404`.
* **Service registry:** `ServiceRegistryService` becomes dynamic. It lists the existing static services plus a WES and a TES service for each registered platform, whatever their health. `/services/{serviceId}` and `/services/types` include them.
* **Security:** add the new paths to `SecurityConfig`. `service-info` is public, like the other services. Everything else needs a Hydra JWT (or the run-scoped credential for TES, Task 6), with a `@PreAuthorize` check that the caller holds the platform's visa.

**Decisions (Task 5):**

### Task 6: Refcloud TES API: proxy to Funnel (D3, D11, D12)

* Implement the TES 1.1 endpoints under `/ga4gh/tes/{platformId}/v1`, forwarding to the connection's Funnel TES (TES calling TES; D12 executor pluggability).
* **Two kinds of caller:**
  * Researchers calling directly with a Hydra JWT, who must hold the platform's visa.
  * Nextflow processes using a run-scoped credential (D12), which identifies the run and so the user. The credential is valid only while its run is active.
* Record every task in `compute_task`.
* Each caller sees only their own tasks in `GET /tasks` and `GET /tasks/{id}`. Task IDs returned to clients are the refcloud's, not Funnel's.
* **Limits at task creation (D11):**
  * Reject per-task resources above the per-user limit or the connection ceiling.
  * Queue (`QUEUED`) when the connection's total tasks / CPU / memory in flight is full, and forward the task to Funnel when capacity frees up.
* **Usage:** when a task finishes, compute its compute-hours (CPU cores × wall time from the TES task logs) and store them for quota accounting.

**Decisions (Task 6):**

### Task 7: Workflow engine adapter and the Nextflow implementation (D12, D18)

* Define a `WorkflowEngine` interface with operations to submit, get status, cancel, read logs and list outputs. Keep it free of Nextflow specifics so a Cromwell adapter can be added later.
* Implement `NextflowEngine` for `nextflow-funnel-docker`:
  * For each run, launch `nextflow run` as a host subprocess with the run directory layout from D18 (`runs/<run_id>/{inputs,work,outputs}`): work dir set to `work/`, outputs published to `outputs/`.
  * Configure the nf-ga4gh TES executor to point at the **refcloud** TES for the platform (not Funnel directly), with the run-scoped credential.
  * Map Nextflow's state to WES states. Capture stdout and stderr for the run log.
* **Run lifecycle:** cancel runs that exceed the max run duration (the lower of the per-user and connection values). When a run ends (complete, failed or cancelled), delete `inputs/` and `work/`, keep `outputs/`, and revoke the run-scoped credential.
* **Restarts:** if core-api restarts while runs are active, choose how those runs are reconciled and record it here. For example, mark them `SYSTEM_ERROR`, or reattach if that's feasible.
* Use a named executor bean for any `@Async` work, per `apps/core-api/CLAUDE.md`.

**Decisions (Task 7):**

### Task 8: Refcloud WES API (D11, D17, D19, D20)

* Implement the WES 1.1.0 endpoints under `/ga4gh/wes/{platformId}/v1`. Each user sees only their own runs.
* **`POST /runs`** runs these checks in order and rejects with a clear message at the first failure:
  1. **Auth and visa:** a valid token, and the caller holds the platform's visa.
  2. **Allowlist (D20, D21):** `workflow_url` exactly matches an allowlist entry. `workflow_attachment` and `workflow_engine_parameters` are rejected; the engine runs only with the entry's own engine settings.
  3. **Concurrent runs:** reject at the per-user limit (D11).
  4. **Monthly quota:** reject when the per-user quota or the connection's pooled budget is used up. The message says which quota was hit and when it resets (the start of the next calendar month, UTC).
  5. **Inputs (D17):** stage the `drs://` inputs.
  6. **Pooled capacity:** queue the run (`QUEUED`) when the connection's total concurrent runs are at their limit. Otherwise launch it through the `WorkflowEngine`.
* **Workflow resolution (D20):**
  * git entries: run the pinned revision.
  * TRS entries: resolve with the approach chosen in Task 1.
  * Bundled entries: run from the bundled path.
* **DRS input staging (D17):**
  1. Find `drs://` URIs in `workflow_params`.
  2. Accept only the refcloud's own DRS objects with an `aws_s3_access_object`; reject anything else (other DRS servers, manifests) with a clear message.
  3. Check the caller's access with the same check the DRS endpoints use (`DrsObjectService`).
  4. Download each object into `inputs/` and rewrite the parameter to the local path.
* **Outputs (D18, D19):**
  * `RunLog.outputs` lists each file under `outputs/` with its relative path, size and download URL.
  * `GET /runs/{run_id}/outputs/{path}` streams the file, for the run's owner only.
  * Resolve `{path}` strictly inside the run's `outputs/` directory: reject `..`, absolute paths, and symlinks that point outside it.
* **`GET /runs/{run_id}/tasks`** lists the run's TES tasks from `compute_task`.
* **Errors** use the WES `ErrorResponse` format, through an exception handler scoped to the WES package, like `ServiceRegistryExceptionHandler`.

**Decisions (Task 8):**

### Task 9: Bundled demo workflow (D20)

* Add one bundled Nextflow workflow, `drs-checksum`. It takes one input file and writes a small report with the file name, size, MD5 and SHA-256.
* Location: `compute/workflows/nextflow/drs-checksum/`, tracked in git (see Task 10 for the ignore rules).
* Find an existing 1000 Genomes `.bai` DRS object for the demo input, and note its ID in the run script's output and in the docs. Don't create new datasets.

**Decisions (Task 9):**

### Task 10: Local native-mode setup (D13, D16, D18)

* **Funnel:** add a pinned Funnel service to `deploy/local/docker-compose.yml`, following the Task 1 findings: Docker socket, the shared directory mounted at the same path as on the host, and local storage settings. Note that the socket mount is for local development only.
* **Nextflow is a prerequisite, not something setup installs.** Like every other local tool, the developer installs Nextflow before running setup; `./scripts/refcloud-setup.sh` has no install commands.
  * Add a Nextflow row (minimum version, suggested macOS install command) to the requirements table in the root `README.md` ("Getting Started").
  * Setup only checks that `nextflow` is on the `PATH` and meets the minimum version. If it doesn't, setup warns and skips compute setup, like `kubernetes_dependencies_installed` does for helm mode. Put the check in `scripts/lib/common.sh`.
  * The nf-ga4gh plugin isn't installed separately. Its version is pinned in core-api's connection config (passed as `NXF_PLUGINS_DEFAULT`), and Nextflow downloads it into `~/.nextflow/plugins` the first time a run needs it. That needs internet access; note it in the README.
* **Storage directory:** create `compute/` with a `.gitignore`:
  * Ignored: per-platform-type storage (`compute/nextflow-funnel-docker/` and later types).
  * Tracked: `compute/workflows/` and the `.gitignore` itself.
  * `--reset` clears the storage directories.
* **Default connection and platform:**
  * Native mode defines a default `nextflow-funnel-docker` connection in core-api config.
  * Setup registers a default Compute Platform on it, using the admin CLI, with small per-user limits and the bundled demo on its allowlist (D9).
* **Run script:** `./scripts/refcloud-run-native.sh` lists the platform's WES and TES endpoints alongside the other GA4GH services.
* **Helm mode:** stays working with compute **off**. No connections are configured, so no platforms are registered and nothing compute-related is deployed (stage 2 adds it). Run `./scripts/refcloud-static-analysis-helm.sh` and `./scripts/refcloud-static-analysis-deploy-local.sh`.
* **Docs:** add a `compute/` entry to the root `CLAUDE.md` repository layout. Also document native-mode compute in `deploy/local/README.md`, keeping the root `README.md` to a pointer.
* **System dependencies:** the root `README.md` requirements table lists every new host dependency with its minimum version, and the docs say what runs where:
  * Nextflow and the Java version it needs run **natively on the host**: core-api launches `nextflow run` as a subprocess.
  * Funnel runs **in a Docker container** (Compose) and starts task containers through the host's Docker socket, so it adds no host dependency beyond Docker.

**Decisions (Task 10):**

### Task 11: Researcher UI: Browse Compute Platforms (D5, D10)

* Add a "Browse Compute Platforms" page modeled on "Browse Datasets". It lists each platform with:
  * its name and description;
  * its platform type;
  * its health ("healthy" / "unhealthy", from service-info);
  * its WES and TES base URLs;
  * the user's visa status, with a request-access action.
* Add the core-api endpoints the page needs (list platforms, request access), mirroring the datasets endpoints and their Kratos-session security.
* Add the page to the sidebar navigation.

**Decisions (Task 11):**

### Task 12: Tests, docs, and the end-to-end check

* **Tests:**
  * `@WebMvcTest` slices for the WES, TES, service-info and service registry controllers. Cover auth, the visa check, the `503` rule, allowlist rejection, limit and quota rejections, and output path containment.
  * `@DataJpaTest` for the new repositories, including the migrated visa queries.
* **Static analysis:** `./gradlew staticAnalysis`, the admin CLI and researcher UI checks, the deploy and helm static analysis scripts, and `./scripts/refcloud-static-analysis-secrets.sh staged` before committing.
* **End-to-end check:** run the "Done when" scenario from the Objective on a fresh `--reset` setup and record the exact commands (curl or a script) in `deploy/local/README.md`.
* **Docs site (`apps/docs`):** add a compute page with:
  * the terminology table;
  * the supported platform types (D14; stage 1 has `nextflow-funnel-docker` only);
  * the non-standard extensions: health in service-info (D10) and output downloads (D19);
  * a **workflow compatibility table** per platform type, listing which workflows and versions are known to work (seeded from the Task 1 table: the bundled `drs-checksum` demo and nf-core/demo 1.0.2 work; nf-core/demo 1.2.0 and other workflows using `env`/`eval` outputs need the nf-ga4gh fix, D22), with a link to the upstream nf-ga4gh issue/PR and its status;
  * how to run the demo.

**Decisions (Task 12):**

## Future Directions

* Stage 2: Nextflow + Funnel (Kubernetes) in helm mode with a `hostPath` volume, including discussion doc spike items 3 and 7 (D16).
* Stage 3: MinIO/S3 storage as a connection setting for both platform types (D15, D16).
* A researcher UI page listing the user's runs, their states and output downloads. Stage 1 exposes these through WES only.
* All other Future Directions in the discussion doc: Cromwell, TESK, DRS write-back, inputs from other DRS servers, retention and storage caps, serving last-known run state while a platform is unhealthy, and cloud deployment on AWS.
