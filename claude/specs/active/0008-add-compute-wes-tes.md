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

### Task 3: Connections config and the Compute Platform data model (D7, D9, D11, D14, D15, D20, D21)

Scope (narrowed 2026-11-11): connections config, `compute_platform`, the workflow allowlist, and the visa changeset. `compute_run` and `compute_task` are created in the tasks that first use them (6–8), so their shape comes from working code. Production can't be reset, so every later shape change would need another changeset.

* **Connections file (D9).** Connections are defined in one YAML file, `connections.yml`, read by **both** core-api and the admin CLI, so the CLI can validate platforms against them.
  * **Location:** the env var `REFCLOUD_COMPUTE_CONNECTIONS_FILE`. The local default is `deploy/local/compute/connections.yml`; in helm, a mounted ConfigMap (stage 2).
  * **core-api** loads it with `spring.config.import: optional:file:${REFCLOUD_COMPUTE_CONNECTIONS_FILE:../../deploy/local/compute/connections.yml}`. Keys nest under `ga4gh.refcloud.compute.connections.<key>` and bind to a `@ConfigurationProperties` record. `optional:` means a missing file gives no connections (compute off), which helm mode relies on in stage 1. A malformed connection or an unknown `type` fails startup.
  * **The admin CLI** reads the same file through the same env var, with the equivalent default path from `apps/admin-cli`.
  * **Defined only in the file:** no per-property env var overrides, which the CLI wouldn't see. Secrets are the exception: the file names the **environment variable** holding each credential, and only the app that needs it reads it.
  * **Relative paths** in the file (e.g. the storage root) resolve from the file's own directory, so both apps get the same absolute path.
  * **Each connection has:**
    * `type`: the platform type; `nextflow-funnel-docker` only in stage 1.
    * Engine settings: path to the `nextflow` executable, Nextflow and nf-ga4gh versions.
    * Executor settings: Funnel TES URL and, optionally, the env var name of its credential.
    * `storage`: `type: shared-filesystem` (the only value in stage 1, but the setting exists from the start, D15) and `root`.
    * Connection (pooled) limits from the D11 table: total concurrent runs, total tasks / CPU / memory in flight, the largest per-task CPU, RAM and disk, the longest run duration, and an optional monthly core-hours budget.
  * **Ship** `deploy/local/compute/connections.yml` with the local `nextflow-funnel-docker` connection.
* **Database tables** (Liquibase; all new timestamp columns `timestamptz`):
  * `compute_platform`:
    * `id`: lowercase letters, digits and hyphens, `^[a-z0-9][a-z0-9-]{0,62}$`, e.g. `nextflow-demo`. It's used in URL paths (`/ga4gh/wes/{platformId}/v1`) and service-info IDs.
    * name, description, `connection_key`, enabled.
    * Per-user limits from the D11 table, each nullable (null = no per-user cap, so only the connection ceiling applies): concurrent runs, per-task CPU (cores), RAM and disk (GB), max run duration (seconds), and a monthly **core-hours** quota (CPU cores × wall-clock hours, summed over a user's tasks).
  * **Every Compute Platform has a visa** (no open-access compute). Its visa ID is `ga4gh_visa:compute_platform:{platform_id}`.
  * `passport_visa` changes (D7), in **one new changeset** so the database never allows a visa that gates nothing:
    * drop `NOT NULL` from `passport_visa.dataset_id` (moved here from Task 2);
    * add `passport_visa.compute_platform_id` (FK to `compute_platform.id`, `ON DELETE CASCADE` like `dataset_id`, unique, nullable);
    * add a check constraint that exactly one of `dataset_id` and `compute_platform_id` is set.

    Verify it applies to an existing database with data. The admin CLI creates the platform's visa together with the platform. Extend `refcloud visa list` to show `compute platform: <id> (<name>)` in its GATES column.
  * `compute_platform_workflow`: the allowlist (D20, D21). One table with typed columns per source kind (not a JSON blob), plus check constraints that each kind has its required fields:
    * git: repository URL, pinned commit, main script path;
    * TRS: server URL, tool ID, version ID;
    * bundled: path.

    Every entry also has: the exact `workflow_url` a submission must match (unique per platform), a name and description, the workflow type and version, and its **engine settings** (D21), set by the admin: Nextflow profiles (a list) and engine environment variables (a map, e.g. `NXF_SYNTAX_PARSER=v1`). Researchers can't change them.
* **Ceiling rule (D11):** the admin CLI reads the connections file. When a platform is registered or changed, it rejects an unknown `connection_key` and any per-user limit above the connection's ceiling (Task 4). core-api still applies the lower of the two at runtime, in case the file changed after registration.
* A `compute_platform` whose `connection_key` isn't in the connections file is reported unhealthy (D10). It doesn't fail startup.

**Decisions (Task 3):**

* **Connections config (core-api, `compute/` package):**
  * `ComputeConfig` (`ga4gh.refcloud.compute`) is `@Validated`. It has `connectionsFile` and `connections`, a map keyed by connection key (same format as platform IDs). Each `Connection` has `type` (`ComputePlatformType`), `engine`, `executor` (`tesUrl`, optional `credentialEnv`), `storage` (`type`, `root`) and `limits` (`monthlyCoreHoursBudget` optional, the rest required and positive).
  * An unknown `type` or storage `type` fails binding, so startup fails. Spring's lenient enum binding maps `nextflow-funnel-docker` to `NEXTFLOW_FUNNEL_DOCKER`.
  * `ComputeConnections` looks connections up and resolves `storage.root` from the connections file's directory. It logs at startup which connections were loaded, or that compute is off.
  * `application.yml`: `spring.config.import: optional:file:${REFCLOUD_COMPUTE_CONNECTIONS_FILE:../../deploy/local/compute/connections.yml}`, plus `ga4gh.refcloud.compute.connections-file` with the same expression, so core-api knows the file's location for path resolution. The two must stay in sync; a comment says so.
* **`deploy/local/compute/connections.yml`** defines one connection, `nextflow-funnel-docker-local`: Nextflow 26.10.5 + nf-ga4gh 1.5.0, Funnel at `http://localhost:8000`, storage root `../../../compute/nextflow-funnel-docker` (that is, `<repo>/compute/nextflow-funnel-docker`), and laptop-sized pooled limits. The file's header documents its rules.
* **Liquibase:** four new changesets, all in raw SQL with rollbacks:
  * `create-table-compute-platform`:
    * a check on the ID format (`^[a-z0-9][a-z0-9-]{0,62}$`) and that set limits are positive;
    * a unique name;
    * the shared `set_updated_at` trigger.
  * `modify-tb-passport-visa-gates-exactly-one-dataset-or-compute-platform`:
    * `dataset_id` becomes nullable;
    * adds `compute_platform_id` (FK with cascade, unique) and `CHECK (num_nonnulls(dataset_id, compute_platform_id) = 1)`;
    * its rollback deletes compute-platform visas first, since they can't exist without the column.
  * `create-enum-compute-workflow-source-kind` (`git`, `trs`, `bundled`).
  * `create-table-compute-platform-workflow`:
    * per-kind source-field checks;
    * `workflow_url` unique per platform;
    * `engine_profiles TEXT[]` and `engine_env JSONB`, which must be an object.

    TRS entries carry both the TRS reference and the resolved git repository and commit (Task 1). A commit must be a full 40-character SHA.
* **NULL-passing check fixed during testing:** a Postgres `CHECK` that evaluates to `NULL` passes, so a git or TRS entry with a NULL `git_commit` slipped through the regex test. The source-fields check now uses explicit `num_nulls(...) = 0` and wraps the `CASE` in `COALESCE(..., FALSE)`. Any future check using a regex or comparison on a nullable column needs the same care.
* **Entities** (core-api reads them; the admin CLI writes them):
  * `ComputePlatform` and `ComputePlatformRepository`;
  * `ComputePlatformWorkflow` (`NAMED_ENUM` source kind, `ARRAY` profiles, `JSON` env) and `ComputePlatformWorkflowRepository`;
  * `ComputeWorkflowSourceKind`;
  * `PassportVisa.computePlatform` (`@OneToOne`).

  No existing code assumed every visa has a dataset (all lookups go dataset → visa), so nothing else changed.
* **Admin CLI:** `visa list` shows `compute platform: <id> (<name>)` in GATES. Reading `connections.yml` and validating platforms against it come with the platform commands in Task 4.
* **Tested:**
  * **Migration on existing data** (throwaway Postgres 18): applied the 49 earlier changesets, inserted a dataset visa with an approved request, then applied the 4 new ones. The existing visa and approval were untouched.
  * **Constraints:** every one was exercised: bad platform ID, non-positive limit, a visa gating neither or both, a second visa for one platform, git with a branch name, git or TRS with a NULL commit, TRS missing its version, bundled with git fields, duplicate `workflow_url`, non-object `engine_env`. The `updated_at` trigger fires.
  * **Rollback** of the 4 changesets removes the platform visa and its request, keeps the dataset visa, restores `NOT NULL` and drops the tables. Re-applying works.
  * **JPA mappings:** a temporary TestNG check (deleted after use) booted core-api against that database. It confirmed the connections import from the real file, the storage root resolution, every platform field, the visa ↔ platform link in both directions, the enum, `text[]`, `jsonb` and `Instant` mappings.
  * **Unit tests:** `ComputeConfigTest` (10 tests) binds the real local file and inline configs, and covers: validation of the real file; storage root resolution (relative and absolute); no connections; unknown platform or storage type failing to bind; a missing or non-positive limit; an invalid connection key.
  * **Admin CLI:** `visa list` and `visa approve` work on a platform visa.

  `./gradlew staticAnalysis` passes, 105 core-api tests pass, and ruff and pyright are clean.
* **Schema drift fixed** (found while testing Task 3; fixed at the user's request). Hibernate schema validation (`ddl-auto: validate`) found exactly two mismatches across the whole schema: `aws_s3_access_object.id` and `drs_object_checksum.id` were `INTEGER` identity columns, but both entities use `Long`.
  * New changeset `modify-type-identity-ids-bigint` widens both to `BIGINT`. Postgres widens their identity sequences with them. It has a rollback.
  * Tested on a database with existing rows: IDs are kept, new rows continue the sequence, and the **entire schema now passes `ddl-auto: validate`**. Rollback restores `INTEGER` with IDs intact, and re-applying works.
  * Like the timestamp migration, it rewrites the two tables, which briefly locks them during the upgrade.
  * To re-check the mapping at any time, run core-api once against a fully migrated database with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` (see `apps/core-api/CLAUDE.md`).

### Task 4: Admin CLI: register and manage Compute Platforms (D5, D9, D11, D20, D21)

* Add a `refcloud compute-platform` command group, modeled on `refcloud dataset`:
  * `init`: create a platform (ID, name, description, connection key, optional per-user limits) **and its visa** (`ga4gh_visa:compute_platform:{id}`) in one transaction. New platforms start **disabled**.
  * `list`: platforms with their connection (flagged if no longer in the connections file), enabled state, allowlist size, limits and visa.
  * `update`: name (renames the visa too), description, connection, per-user limits (`none` clears one).
  * `enable` / `disable`. Enabling needs the connection in the connections file and at least one allowlisted workflow, so a half-configured platform is never offered.
  * `delete`, with confirmation. It cascades to the visa, access requests and allowlist.
  * `allowlist add` / `allowlist list` / `allowlist remove`.
* **Validate against the connections file** (Task 3): reject an unknown connection key, and any per-user limit above the connection's ceiling, on `init` and `update`.
* **Allowlist sources (D20):** exactly one per entry.
  * **git:** `--git-repository` + `--ref`. The ref is a tag or a full commit SHA. Tags are resolved to the commit with `git ls-remote` when the entry is added; branches and short SHAs are refused. `workflow_url` = GitHub's URL for the main script at that commit, `https://github.com/{owner}/{repo}/blob/{sha}/{main_script}`.
  * **TRS:** `--trs-descriptor-url`. Parse the server, tool ID, version and type (it must match the platform's engine, `NFL`). Fetch the descriptor, take its `raw.githubusercontent.com` URL to find the GitHub repository, and resolve the version to a commit the same way. `workflow_url` = the canonical (consistently percent-encoded) descriptor URL.
  * **bundled:** `--bundled {name}`. `bundled_path` = `compute/workflows/nextflow/{name}`, and `workflow_url` = `refcloud:bundled/{name}`.
  * **Only GitHub-hosted repositories** are supported for git and TRS. Anything else gets a clear error saying other repository systems aren't supported yet.
* **Engine settings (D21):** `--profile` (repeatable) and `--engine-env KEY=VALUE` (repeatable). The workflow type and version come from the connection's platform type.
* Access requests are approved with the generic `refcloud visa` commands from Task 2.
* Document the commands in `apps/admin-cli/README.md` and `apps/admin-cli/CLAUDE.md`.
* There's no automated test setup in the admin CLI yet ("Testing: TBD"), so test by hand against a throwaway database.

**Decisions (Task 4):**

* **Code:**
  * `config/compute_connections.py` reads the connections file (type and limits only, kebab-case keys; no file = no connections, like core-api). It adds **PyYAML** (`pyyaml>=6.0.3`) to the admin CLI's dependencies.
  * `handlers/compute_platform/`: `__init__.py` (shared helpers; `fail()` is typed `NoReturn`), `manage.py` (init, list, update, enable, disable, delete), `allowlist.py`, and `sources.py` (git/TRS resolution).
  * New `DatabaseEngine` methods for platforms and allowlist entries. The platform + visa insert, and a rename + visa rename, are single transactions.
  * Names are pre-checked for uniqueness so the error is readable.
* **git in the image:** `git ls-remote` (option (a)) needs git. The admin CLI runner image installs it with `apt-get install --no-install-recommends git`, and `.hadolint.yaml` ignores DL3008 (unpinned apt versions) for the same reason it ignores DL3018 for apk. `git` runs with a fixed argument list, no shell, and `GIT_TERMINAL_PROMPT=0`. The repository URL is validated as `https://github.com/...` first, so it can never be read as an option or a non-https transport.
* **TRS fetches** use the standard library (`urllib`), https only.
* **git is a host dependency** in native mode (the admin CLI runs `git ls-remote`), so the root `README.md` requirements table now has a git row. Nextflow is added to that table in Task 10.
* **Tested by hand** against a throwaway Postgres 18 with every changeset, using the real `deploy/local/compute/connections.yml`:
  * **init:** succeeds and creates the visa. Refused: bad ID, unknown connection, limit above ceiling (64 > 4 GB), zero, non-integer cores, `none` on init, duplicate ID, duplicate name.
  * **allowlist add, all four kinds:**
    * git by tag (`nf-core/demo` `1.0.2` → `db7f526…`, the same commit as the spike);
    * TRS via Dockstore (→ the same commit);
    * git by full SHA (with a `.git` suffix, normalized);
    * bundled (warns that `drs-checksum` doesn't exist yet; Task 9 adds it).
  * **allowlist add, refused:** branch (`master`), short SHA, unknown tag, GitLab repository, missing `--ref`, duplicate `workflow_url`, Dockstore-hosted (non-GitHub) TRS tool `#workflow/dockstore.org/grbot/flagstat`, WDL descriptor on a Nextflow platform, non-TRS URL, TRS version that's a branch (`dev`), bad bundled name, zero or two sources, bad `--engine-env`, `--ref` without git, unknown platform; `allowlist remove` of an unknown entry.
  * **enable / update:** `enable` refused with an empty allowlist and with a missing connection. `update` clears and changes limits and is refused above the ceiling. A rename renames the visa. A name collision, no options, and an unknown connection are all refused.
  * **Connections file:** `list` flags `MISSING` connections. Unknown type and missing limit in the file are clear errors.
  * **delete:** the confirmation prompt aborts on "n". With `-y` it removes the platform, visa, pending request and allowlist and leaves other platforms alone. Deleting again is refused.
  * **Docker image:** builds, `git ls-remote` works inside it as uid 10001, and hadolint and trivy-config pass.

  ruff and pyright are clean.
* **Helm:** no chart change is needed now. The admin-cli pod gets the connections file with the stage 2 chart wiring (Future Directions).

### Task 5: WES and TES service-info, health, and a dynamic service registry (D6, D10)

* **Only enabled platforms are visible** (decided 2026-11-12). A disabled platform has been deliberately withheld by the admin, which is different from a temporary outage. It returns `404` on every WES/TES endpoint, including service-info, and isn't listed in the service registry (or, later, the researcher UI). It stays visible through the admin CLI. Once enabled, it shows up everywhere.
* For each enabled Compute Platform, serve `GET /ga4gh/wes/{platformId}/v1/service-info` and `GET /ga4gh/tes/{platformId}/v1/service-info`.
  * **IDs:** `{prefix}.{wes|tes}.{platformId}.{suffix}`, e.g. `org.ga4gh.refcloud.wes.nextflow-demo.local`. The prefix and suffix are configurable.
  * **Shared fields** come from a new config block, `ga4gh.refcloud.compute.service-info`: prefix, suffix, organization, contact URL, documentation URL, `auth_instructions_url`, environment, version.
  * **Per-platform fields:** name `"{platform name} (WES)"` / `"(TES)"`, the platform's description, and `createdAt`/`updatedAt` from the platform record.
  * **WES 1.1.0 required fields:**

    | Field | Value |
    |---|---|
    | `workflow_type_versions` | from the platform's allowlist, e.g. `{"NFL": {"workflow_type_version": ["DSL2"]}}` |
    | `supported_wes_versions` | `["1.1.0"]` |
    | `supported_filesystem_protocols` | `["drs"]` (inputs are `drs://` only, D17) |
    | `workflow_engine_versions` | from the connection, e.g. `{"nextflow": {"workflow_engine_version": ["26.10.5"]}}` |
    | `default_workflow_engine_parameters` | `[]` (engine parameters are rejected, D21) |
    | `system_state_counts` | `{}` until runs exist (Task 8) |
    | `auth_instructions_url` | a docs page on requesting access to a compute platform and minting a passport token, both done in the researcher UI. The page itself is written in Task 12. |
    | `tags` | `{"platform_type": "<type>"}`. The connection key is internal and isn't exposed. |

  * **TES:** leave out `storage` (shared-filesystem paths are internal). `tesResources_backend_parameters` is `[]`.
  * **Health extension property:** `"health": { "status": "healthy" | "unhealthy", "checkedAt": "<ISO 8601>" }`.
* **Health checker:** a `@Scheduled` job checks every connection that backs a platform, every 60 s (configurable). The first check runs at startup, and platforms count as unhealthy until it completes. A connection is healthy only if all three checks pass:
  * Funnel's TES `service-info` responds (5 s timeout);
  * `NXF_VER=<pinned> nextflow -version` reports the pinned version. The `nextflow` on the PATH is only a launcher; `NXF_VER` makes it fetch and run exactly the pinned version, which needs internet the first time. Runs will use the same `NXF_VER` (Task 7), so version drift can't happen;
  * a test file can be written to the storage root. A missing root counts as unhealthy and isn't created; creating it is setup's job (Task 10).

  Platforms on the same connection share its result, and a connection missing from the connections file is unhealthy. Failure reasons go to the logs; service-info shows only `{status, checkedAt}`. Connection health stays out of `/actuator/health/liveness` and `/readiness`.
* **404/503 rule:** a filter on `/ga4gh/{wes,tes}/{platformId}/v1/**`, so later tasks inherit it:
  * unknown or disabled platform → `404`;
  * unhealthy platform → `503`, except `service-info`.

  Error bodies use the WES `ErrorResponse` shape (`{msg, status_code}`) for both WES and TES, since TES defines no error format.
* **Service registry:** `ServiceRegistryService` becomes dynamic. It lists the existing static services plus a WES and a TES service for each **enabled** platform, whatever their health. It reads platforms from the database on each request, so changes appear without a restart. `/services/{serviceId}` and `/services/types` include them.
* **Ingress:** add `/ga4gh/wes/*` and `/ga4gh/tes/*` handle blocks to `deploy/local/Caddyfile`, and the same paths to the Helm ingress (`deploy/helm/charts/app/templates/common/ingress.yaml`). Without them, browser and client traffic falls through to Next.js and gets a 404 (see `deploy/local/CLAUDE.md`).
* **Security:** add the new paths to `SecurityConfig`. `service-info` is public, like the other services. Everything else needs a Hydra JWT (or the run-scoped credential for TES, Task 6), with a `@PreAuthorize` check that the caller holds the platform's visa.

**Decisions (Task 5):**

* **Code (core-api):**
  * Config: `ComputeServiceInfoConfig` (`ga4gh.refcloud.compute.service-info`: id prefix and suffix, organization, contact and documentation URLs, auth instructions URL, environment, version) and `ComputeHealthConfig` (`ga4gh.refcloud.compute.health`: check interval 60s, executor timeout 5s, engine timeout 5m, since the first `nextflow -version` downloads Nextflow). Both are in `application.yml`; these are deployment settings, not connections.
  * `health/`: `ComputeHealth` (record + `Status`), `ConnectionChecker` (the three checks), `ComputeHealthService` (`@Scheduled`, `initialDelay = 0`; results in memory per instance; logs each change once, not every round). `ComputeSchedulingConfig` adds `@EnableScheduling`.
  * `serviceinfo/`: `WesServiceInfo`, `TesServiceInfo` (records implementing `ServiceInfo`, spec fields via `@JsonProperty`), `ComputeServiceInfoService` (`@Transactional(readOnly = true)`).
  * `wes/WesServiceInfoController`, `tes/TesServiceInfoController`.
  * `error/ComputeErrorResponse` and `ComputeExceptionHandler`, scoped to the compute package and ordered ahead of `GlobalExceptionHandler`, like the service registry's.
  * `ComputePlatformAvailabilityFilter`, registered by `ComputeFilterConfig`.
  * Repository methods `findByIdAndEnabledTrue`, `findAllByEnabledTrueOrderByIdAsc` and `findWorkflowTypes`. `ComputePlatformType.engine()` is the engine name WES reports (`nextflow`).
* **Every configured connection is checked,** not only ones with a platform. It's simpler, gives deployers feedback in the logs before any platform exists, and warms up Nextflow's download. Platforms read their connection's latest result, and a connection missing from the file is unhealthy.
* **The availability filter runs before Spring Security** (`FilterRegistrationBean`, order `SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1`; URL patterns `/ga4gh/wes/*`, `/ga4gh/tes/*`). Availability isn't secret: health is public through service-info, and unknown and disabled platforms both return `404`. So any client learns a platform is missing or down before being asked to authenticate. A healthy platform's requests then go through normal authentication.
* **`503` responses** carry `Retry-After` = the check interval in seconds.
* **A missing connection** gives empty `workflow_engine_versions` and `tags` in WES service-info rather than guessed values.
* **Fixed during end-to-end testing:**
  * Jackson serialized `ComputeHealth.isHealthy()` as an extra `"healthy"` property. It's now `@JsonIgnore`, and the JSON test asserts the health object has exactly `checkedAt` and `status`.
  * A refused connection logged `unreachable: null`; the exception type is now logged when there's no message.
* **Build change:** `testCompileOnly 'com.github.spotbugs:spotbugs-annotations:4.10.5'` (same artifact and version as `compileOnly`; lockfile refreshed). Tests that load a class carrying `@SuppressFBWarnings` (here `ConnectionChecker`) otherwise fail `-Werror` with "Cannot find annotation method". Only `COMMAND_INJECTION` on the `nextflow` launch needed a suppression; SpotBugs flagged the path-traversal ones as unused.
* **Unit tests (23 new; core-api total 128):**
  * `ConnectionCheckerTest`: a local HTTP server as the executor (OK, trailing slash, wrong credential → 401, unset credential variable, unreachable); fake `nextflow` launcher scripts (pinned version, other version, failing, missing); storage (writable leaves no files, missing isn't created, read-only); all three failures reported.
  * `ComputeHealthServiceTest`: unhealthy until the first check, healthy, failing then recovering, missing connection.
  * `ComputeServiceInfoJsonTest`: WES/TES spec field names, the health shape (exact keys; no `checkedAt` before the first check), registry listing keeps the WES fields plus `url`, the `{msg, status_code}` error shape, the service ID format.
  * Controller/filter slice tests (`@WebMvcTest`) wait for Task 12, which adds Spring's test support. The behaviour is covered end to end below.
* **End-to-end check** against a throwaway Postgres, a real Nextflow 26.10.5 launcher (`NXF_HOME` in scratch), a fake TES executor (Python) and a 5 s check interval; the platform was managed with the admin CLI:
  * **Startup:** core-api starts and reports the connection healthy. The launcher downloaded and reported the pinned version.
  * **Disabled platform:** not in the registry, and both service-infos return `404` with `{msg, status_code}`.
  * **After allowlist + enable:**
    * the registry lists WES and TES (`org.ga4gh.refcloud.{wes,tes}.nextflow-demo.local`, URLs `…/ga4gh/{wes,tes}/nextflow-demo/v1`);
    * `/services/{id}` and `/services/types` include them;
    * WES service-info has every required field with the agreed values, and TES has `tesResources_backend_parameters` and no `storage`;
    * an unknown platform returns `404`, and other paths reach authentication (`401`).
  * **Executor stopped:** the next check logs unhealthy. WES/TES paths return `503` + `Retry-After: 5`, service-info still returns `200` showing `unhealthy`, and the registry still lists the platform as unhealthy.
  * **Executor restarted:** healthy again, back to `401`.
  * **Connection key missing from the file:** unhealthy, empty engine versions and tags, `503`.
  * **Disabled again:** `404` everywhere, gone from the registry and its types.
  * **Throughout:** `/actuator/health/readiness` stayed `UP`.
* **Checks:** `./gradlew staticAnalysis` passes and 128 tests pass. Caddy validation and Helm lint, kubeconform and trivy-config pass.
* The **auth instructions page** (`https://docs.refcloud.ga4gh.localhost/compute/access` locally) is written in Task 12: requesting access to a compute platform and minting a passport token, both in the researcher UI.


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
  * the **auth instructions page** that WES service-info's `auth_instructions_url` points to (`/compute/access` on the docs site): how to request access to a compute platform and mint a passport token, both in the researcher UI;
  * a **workflow compatibility table** per platform type, listing which workflows and versions are known to work (seeded from the Task 1 table: the bundled `drs-checksum` demo and nf-core/demo 1.0.2 work; nf-core/demo 1.2.0 and other workflows using `env`/`eval` outputs need the nf-ga4gh fix, D22), with a link to the upstream nf-ga4gh issue/PR and its status;
  * how to run the demo.

**Decisions (Task 12):**

## Future Directions

* Stage 2: Nextflow + Funnel (Kubernetes) in helm mode with a `hostPath` volume, including discussion doc spike items 3 and 7 (D16).
  * **Helm chart wiring for connections:**
    * a `compute.connections` value (default empty);
    * a ConfigMap template that renders `connections.yml` from it (only when non-empty);
    * mounting it into the core-api and admin-cli Deployments, with `REFCLOUD_COMPUTE_CONNECTIONS_FILE` pointing at it;
    * environment variables from Kubernetes Secrets for connection credentials (created like the existing secrets in `deploy/helm/README.md`).
    * the compute service-info settings (`ga4gh.refcloud.compute.service-info`, e.g. a non-`local` ID suffix and the public auth instructions URL) as values → environment variables, as the chart already does for DRS and the other services.

    Production connections go in the deployer's own values file, passed with `-f`. For the GA4GH refcloud, that's the existing private values repo. This repo carries only the chart wiring and a commented example.
* Stage 3: MinIO/S3 storage as a connection setting for both platform types (D15, D16).
* A researcher UI page listing the user's runs, their states and output downloads. Stage 1 exposes these through WES only.
* Dataset visa IDs: consider a typed format such as `ga4gh:dataset:{dataset_id}` (today `ga4gh_visa:{dataset_id}`), alongside `ga4gh_visa:compute_platform:{platform_id}`. Tabled for a future ticket; it needs a data migration for existing visas and their assertions.
* Run Hibernate schema validation in CI: a job with a Postgres service that applies the changelog and boots core-api with `ddl-auto: validate`, so entity/column drift fails the build. The whole schema validates as of Task 3.
* Allowlist sources hosted outside GitHub (GitLab, Bitbucket, Dockstore-hosted tools): each needs its own repository URL parsing and `workflow_url` format. Today they get a clear "not supported yet" error (Task 4).
* All other Future Directions in the discussion doc: Cromwell, TESK, DRS write-back, inputs from other DRS servers, retention and storage caps, serving last-known run state while a platform is unhealthy, and cloud deployment on AWS.
