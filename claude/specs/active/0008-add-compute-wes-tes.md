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

**Done when:** on a fresh native-mode setup (`./scripts/refcloud-setup.sh` then `./scripts/refcloud-run-native.sh`), a researcher with approved visas for the default platform **and** the 1000 Genomes dataset (it's visa-gated, Task 9) can:

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

Researchers submit work **only through WES** and inspect it through WES and TES (decided 2026-11-12). TES task creation exists for the workflow engine, not for researchers.

* Implement the TES 1.1 endpoints under `/ga4gh/tes/{platformId}/v1`, forwarding to the connection's Funnel TES (TES calling TES; D12 executor pluggability). All of them inherit the Task 5 availability filter (`404` unknown/disabled, `503` unhealthy).
* **Two kinds of caller:**
  * **Researchers** (Hydra-issued passport JWT, validated like every other API, plus an approved visa for the platform, checked in the database). They get **read-only TES**: `GET /tasks`, `GET /tasks/{id}` and `POST /tasks/{id}:cancel` on their own tasks. `POST /tasks` returns `403` with "submit work through this platform's WES". Direct submission is a Future Direction.
  * **Workflow engines** (Nextflow), using a **run-scoped token**. They can create, read and cancel tasks for their own run. The token is minted and wired up in Task 7, when runs exist. Task 6 builds TES around a caller abstraction (a researcher, or a run acting for its user) so Task 7 only adds the run-token authentication. Its design (decided 2026-11-12):
    * **Minted by core-api (WES)** once per run when it launches Nextflow: an **opaque** random token (32 bytes from `SecureRandom`, base64url, prefix `rcrun_`), not a JWT. A JWT would need key management and still couldn't be revoked without a database lookup, and Hydra can't easily issue per-run claims.
    * **Only its SHA-256 hash is stored** (on `compute_run`). The plaintext reaches that run's Nextflow process only as an environment variable (`REFCLOUD_TES_TOKEN` → `tes.oauthToken`, Task 1) and is never written to disk.
    * **Validation:** for TES paths, Spring Security routes a bearer token by its shape: `rcrun_…` to a run-token provider, anything else to normal Hydra JWT validation. The provider hashes the token and finds the run. It accepts only if the run is **active** and on the **same platform** as the path, then authenticates as "this run, for its user". The token stops working when the run ends, and the hash is cleared.
    * The caller's user is the one used for task visibility, limits and usage.
    * The Funnel request **never** carries a researcher's JWT or the run token. core-api calls Funnel with the connection's service credential (`credential-env`; none locally).
* **Task records** (`compute_task`, Liquibase; timestamps `timestamptz`):
  * refcloud task ID (returned to clients; Funnel's ID is never exposed), Funnel task ID (null until forwarded);
  * platform, user (`sub`), nullable `run_id` (its foreign key arrives with `compute_run` in Task 7);
  * state;
  * requested resources (CPU cores, RAM, disk);
  * the original request (so queued tasks can be forwarded later), and the latest TES document from Funnel;
  * created, started and ended times, and core-hours.
* **Reads are served from `compute_task`, not live from Funnel.** A reconciler (`@Scheduled`, about every 10 s):
  * polls Funnel for every forwarded task still in flight, and also refreshes a task on `GET`;
  * updates its state, document, times and core-hours;
  * releases pooled capacity and forwards queued tasks, oldest first.

  `GET /tasks` and `GET /tasks/{id}` support the MINIMAL, BASIC and FULL views, built from the stored document with the refcloud task ID substituted.
* **Visibility:** a researcher sees **every task created on their behalf**, which means every task from their WES runs. A run token sees only its own run's tasks.
* **`GET /tasks`:** the TES filters (`name_prefix`, `state`, `tag_key`/`tag_value`) and `page_size`/`page_token`. The page token is opaque, based on task creation order.
* **Tags:** client tags pass through. The refcloud's internal tags (`refcloud_task_id`, `refcloud_user`, `refcloud_run`) are added on the way to Funnel and stripped from every response.
* **Files:** a run-token task's `file://` inputs and outputs must be inside **that run's own directory** under the storage root (the storage root is shared by all users, and Funnel's `AllowedDirs` covers all of it). Anything else is rejected. Runs arrive in Task 7, so this check is built here and exercised end to end there.
* **Limits at task creation (D11):**
  * **Defaults:** a task with no resource request is treated as requesting the connection's new `default-task-cpu-cores` / `default-task-memory-gb` (`connections.yml`, e.g. 1 core, 1 GB). These are also written onto the task forwarded to Funnel.
  * **Reject** per-task resources above the per-user limit or the connection ceiling.
  * **Queue** (`QUEUED`, not forwarded) when the connection's total tasks / CPU / memory in flight is full; the reconciler forwards it when capacity frees up.
  * **If Funnel errors on forward,** the task stays `QUEUED` and the reconciler retries. A longer outage turns the platform unhealthy, and the filter returns `503`.
  * **Quota:** checked at WES submission (Task 8). Tasks from a running workflow are never rejected for quota (D11: a run that crosses its quota finishes).
  * **Docker limitation:** Funnel 0.11.14's Docker template receives no resource fields, and the code that would pass task tags into it is commented out (`worker/worker.go:269`). So on `nextflow-funnel-docker`, CPU and memory are enforced **at admission and for accounting only**; a container can use more than it requested. Funnel's Kubernetes backend is different (stage 2).
* **Cancel:** a queued task is marked `CANCELED` locally; a forwarded one is also cancelled in Funnel.
* **Usage:** when a task finishes, its core-hours are CPU cores (requested, or the default) × wall time from the TES task log (`start_time` → `end_time`), stored for quota accounting.
* **Testing:** task creation has no HTTP caller until run tokens arrive (Task 7). Test the service layer with a test caller against a real Funnel and a throwaway database: forwarding, reconciling, queueing under tiny limits, usage, cancel, and the file-path rule. Test the researcher read endpoints over HTTP. Task 7 tests the full engine → TES path.

**Decisions (Task 6):**

* **Code (core-api):**
  * `ComputeTaskConfig` (`ga4gh.refcloud.compute.tasks`: reconcile interval 10s, executor timeout 30s, page sizes 256 default / 2048 max).
  * `ComputeCaller` (a researcher, or a run with its user and run directory) and `ComputeCallerResolver`. Today the resolver maps a Hydra `Jwt` to a researcher; Task 7 adds the run principal.
  * `task/`: `ComputeTask`, `ComputeTaskRepository` (in-flight and waiting per connection; a native, keyset-paged list query using `jsonb_exists` rather than the `?` operator, which JDBC reads as a parameter), `ComputeTaskStates`.
  * `tes/`:
    * `TesTaskRules`: shape, reserved tags, file locations, resources, the accepted and forwarded documents. Pure, no Spring.
    * `TesTaskViews`: MINIMAL, BASIC and FULL rendering.
    * `TesPageToken`.
    * `TesExecutorClient` (JDK `HttpClient`) and `ExecutorException`.
    * `TesTaskService`: create, forward, reconcile, read, cancel, usage.
    * `TesTaskController`.
  * `platform/ComputePlatformAccessService` and `ComputePlatformRepository.hasVisaAssertion`, behind `GA4GHPassportTokenEvaluator.canAccessComputePlatform`, which the controller applies at class level with `@PreAuthorize`.
  * `ComputeExceptionHandler` now maps `BadRequestException` → 400, `ForbiddenException` and `AccessDeniedException` → 403, and `ExecutorException` → 503.
* **`compute_task`** (changeset `create-table-compute-task`):
  * the TES state values are enforced by a check constraint, and resources must be positive;
  * three indexes: the user's tasks newest first (paging), a run's tasks, and active tasks (a partial index);
  * the `set_updated_at` trigger.

  `run_id` has no foreign key yet; it gets one with `compute_run` in Task 7.
* **Connection config:** `limits.default-task-cpu-cores` / `default-task-memory-gb` were added (required; `connections.yml` sets 1 / 1). An `@AssertTrue` rejects defaults above the per-task ceilings.
* **Forwarding:**
  * Waiting tasks are forwarded **in strict creation order**: a task that doesn't fit holds back the ones behind it, so a large task can't be starved by smaller ones.
  * A task larger than the connection's **total** capacity is rejected at creation, since it could never run.
  * Forwarding and cancel are serialized per connection with an in-process lock. That's enough for one core-api instance (stage 1); several replicas would need a database lock.
* **Executor system logs are never exposed.** End-to-end testing found Funnel's task ID leaking into the FULL view through `logs[].system_logs`, Funnel's internal log lines (its `docker run` command, host paths, container IDs). Every view now drops `system_logs`. FULL keeps the user's executor stdout, stderr and exit codes, and a unit test pins it.
* **Usage:** core-hours = requested (or default) CPU cores × (end − start) of the **latest** attempt in the TES log (6 decimal places). It's recorded once, when the task first reaches a final state. A task cancelled before it started records 0. A task the executor no longer knows (404) becomes `SYSTEM_ERROR`.
* **Unit tests (48 new; core-api total 176):**
  * `TesTaskRulesTest`:
    * shape, reserved tags;
    * allowed locations (nf-ga4gh's plain paths, `file://`, `https` inputs, inline content);
    * refused locations: another run, `run-10` vs `run-1`, `..` traversal, `/etc/passwd`, https output, `s3://`, relative paths;
    * defaults; per-task ceiling vs per-user limit; non-integer and non-positive values; the accepted and forwarded documents.
  * `TesTaskViewsTest`: each view, internal tags and the executor ID hidden, no `system_logs`, a queued task rendered from its request.
  * `TesTaskServiceTest`: state and times from the executor document, core-hours (latest attempt, recorded once, 0 if never started), unknown state, page token round trip and rejection.
  * `ComputeConfigTest`: defaults above the ceiling.
* **End-to-end check** against a real Funnel 0.11.14 (Compose, Docker socket, same-path storage), a throwaway Postgres, the platform's real health checks (scratch Nextflow launcher), and a temporary harness (deleted after). The harness ran a local key server standing in for Hydra and signed test JWTs with the real issuer, so the researcher paths went through real Spring Security. Run tokens don't exist yet, so tasks were created through the service as a workflow run. Results:
  * **Queue:** with `max-tasks-in-flight: 1`, A was forwarded and B stayed `QUEUED` (not forwarded) until A finished. Both completed, and both output files landed in the run's `outputs/` directory.
  * **Usage:** A recorded 0.00165 core-hours (1 core × ~6 s).
  * **Refused:** an input from another run's directory and a 3-core task (ceiling 2), with clear messages.
  * **Researcher `POST /tasks`:** `403` "submit work through this platform's WES".
  * **Cancel:** a queued task → `CANCELED` locally (0 core-hours, never forwarded). A running one → `CANCELING`, then `CANCELED` after reconcile (core-hours for the time it ran).
  * **`GET /tasks`:** all 4 of the researcher's tasks (from "their" run); paging 3 + 1 with no further token; `state`, `name_prefix` and tag filters.
  * **FULL / BASIC:** the refcloud ID, the client tag only, the exit code; Funnel's ID nowhere in the body. BASIC has no stdout and no `system_logs`.
  * **Another approved researcher:** no tasks, `404` on get and cancel.
  * **An unapproved researcher:** `403`. **No token:** `401`. **Unknown task:** `404`. **Bad view or page token:** `400`.
  * **Service-info** is still public.
* **Checks:** `./gradlew staticAnalysis` passes, and 176 tests pass.
* **For Task 7:**
  * the run-token authentication: provider, principal, `ComputeCallerResolver` branch, and an evaluator branch accepting a run token only for its own platform;
  * `compute_run` and the foreign key from `compute_task.run_id`;
  * an end-to-end check of Nextflow → refcloud TES → Funnel, including the file rule with real nf-ga4gh paths.


### Task 7: Runs: the run lifecycle, run-scoped tokens, and the Nextflow engine (D3, D11, D12, D18)

**Scope (agreed 2026-11-12):**
* **Task 7 owns the run itself:**
  * `compute_run`;
  * minting the run-scoped token and authenticating it at TES;
  * the engine adapter: launch, monitor, cancel, clean up;
  * the max-duration timeout and recovery after a core-api restart;
  * the **run queue**: starting waiting runs, oldest first, while the connection's `max-concurrent-runs` allows. This moved here from Task 8.
* **Task 8 owns the WES API:** request checks, the allowlist, the per-user concurrent-run limit, the quota, putting the workflow into the run directory, DRS input staging, the run log, outputs and downloads. It hands a prepared run to Task 7's service.

**Engine:**
* Define a `WorkflowEngine` interface (launch, poll, terminate, clean up), free of Nextflow specifics so a Cromwell adapter can be added later. Implement `NextflowEngine` for `nextflow-funnel-docker`:
  * Each run is launched as a host subprocess in its run directory (D18 layout `runs/<run_id>/{inputs,work,outputs}`).
  * The `nextflow run` command line: `-work-dir work/`, `-params-file params.json` (the researcher's `workflow_params`), `--outdir <run>/outputs`, the allowlist entry's `-profile`s, and `-c refcloud.config`.
  * `refcloud.config` is generated for the run: `process.executor = 'tes'`, `tes.endpoint = <engine-callback-url>/ga4gh/tes/<platformId>`, `tes.oauthToken = System.getenv('REFCLOUD_TES_TOKEN')`.
  * Environment: `NXF_VER` and `NXF_PLUGINS_DEFAULT=nf-ga4gh@<version>` from the connection (Task 1), plus the run token, plus the allowlist entry's engine variables. Those can't override the reserved names (`NXF_VER`, `NXF_PLUGINS_DEFAULT`, `NXF_HOME`, `REFCLOUD_*`).
* **Outputs convention:** allowlisted workflows publish their results through `params.outdir`, the nf-core convention. core-api always passes `--outdir <run>/outputs`, overriding any value the researcher sends. A workflow that publishes elsewhere completes, but has no outputs to list (D19). This is a rule for admins when they allowlist a workflow.
* **How Nextflow reaches the refcloud TES:** through core-api's own **internal** address (service to service), not through Caddy. A natively running Nextflow would otherwise need its JVM to trust Caddy's local CA. The new setting is `ga4gh.refcloud.compute.runs.engine-callback-url` in `application.yml`: `http://localhost:8080` locally, `http://refcloud-api:8080` in helm. It describes core-api, not a connection.
* **Run states (WES):**

  | State | Meaning |
  |---|---|
  | `QUEUED` | waiting for pooled capacity |
  | `INITIALIZING` | launching |
  | `RUNNING` | the engine is alive |
  | `COMPLETE` | exit 0 |
  | `EXECUTOR_ERROR` | non-zero exit (the workflow failed) |
  | `SYSTEM_ERROR` | couldn't launch, or the run was lost |
  | `CANCELING` → `CANCELED` | cancel requested, then finished |

**Run-scoped token** (design agreed in Task 6):
* `rcrun_` + 32 random bytes, base64url, minted when the run is **launched**. Only its SHA-256 hash is stored, and the plaintext reaches the engine only as `REFCLOUD_TES_TOKEN`.
* A dedicated security chain on `/ga4gh/tes/{platformId}/v1/**` routes `rcrun_…` bearer tokens to a run-token provider and everything else to Hydra JWT validation. A run token is accepted only while its run is active, and only on its own platform. `ComputeCallerResolver` and `canAccessComputePlatform` gain the run branch.
* The hash is cleared when the run ends.

**Run lifecycle:**
* **Queue:** runs start oldest first while the connection's running count is below `max-concurrent-runs`.
* **Max run duration:** the lower of the per-user and connection values, counted from when the run **starts** (not while queued). An overdue run is cancelled and ends `CANCELED`, with the reason recorded.
* **Cancel:** `SIGTERM` to the engine, which cancels its own TES tasks (Task 1). After a 2-minute grace period, `SIGKILL`, and the run's remaining TES tasks are cancelled directly.
* **When a run ends** (complete, failed, cancelled or lost):
  * delete `inputs/`, `work/` and Nextflow's `.nextflow/` cache directory;
  * keep `outputs/` and the logs (`nextflow.log`, stdout, stderr);
  * clear the token hash;
  * cancel any of the run's TES tasks still in flight.
* **Disabled platforms (decided 2026-11-12, option a):** disabling a platform stops new work, and runs already active finish. The availability filter lets a run token for an **active run on that platform** through, even when the platform is disabled. Researchers and new submissions still get `404`. Likewise, a visa revoked mid-run doesn't stop the run: it acts for its user, and was approved when it started (the same spirit as D11's "a run that crosses its quota finishes").
* **Restarts: reattach.** Runs are tracked through files in the run directory, not an in-memory process handle:
  * a small launcher script starts the engine, records its PID, and writes the exit code when it finishes;
  * a scheduled poller checks the process and that file.

  After a restart, core-api reattaches to runs whose engine is still alive. To confirm the PID is still the same process, it checks the process start time against the recorded one, and the command line where the OS provides it. It finishes runs whose exit code is waiting, and marks the rest `SYSTEM_ERROR`, cancelling their leftover TES tasks. (Ctrl-C in the terminal running the native-mode script also signals the engines, so those runs end `SYSTEM_ERROR` rather than reattaching. In helm mode, a Job per run, reattaching is natural.)
* There's no `@Async` and no thread per run: the poller is `@Scheduled`.
* `compute_task.run_id` gets its foreign key to `compute_run`.
* **Testing:** a temporary harness does what Task 8 will do (puts a small workflow into a run directory) and submits through the service. core-api launches a real Nextflow, which calls the refcloud TES with a real run token over HTTP, which forwards to Funnel. That's the first end-to-end run of D3's full path.

**Decisions (Task 7):**

* **Code (core-api):**
  * `ComputeRunConfig` (`ga4gh.refcloud.compute.runs`): `engine-callback-url` `http://localhost:8080`, `poll-interval` 5s, `cancel-grace-period` 2m, `launch-timeout` 30s.
  * `run/`:
    * `ComputeRun`, `ComputeRunRepository`, `ComputeRunStates`;
    * `RunTokens` (mint, SHA-256 hash, `Authorization` header parsing);
    * `RunPrincipal` (serializable; the run directory is held as a string), `RunTokenAuthentication`, `RunTokenService`, `RunTokenAuthenticationProvider`;
    * `ComputeRunService`: allocate a run directory, submit, cancel, the `@Scheduled` poller (check active runs, then start queued ones), launch, finish.
  * `engine/`:
    * `WorkflowEngine` (launch, status, terminate, clean up, reserved environment variables);
    * `EngineStatus` (`Alive` / `Exited` / `Lost`), `EngineProcess`;
    * `HostProcessRunner`: the generic host-process launcher (pid and exit-code files, identity, signals);
    * `NextflowEngine`.
  * The launcher script is a classpath resource (`compute/run-launcher.sh`), copied into each run directory.
  * **Changeset `create-table-compute-run`:** WES state check; unique `token_hash` and `run_directory`; JSON object checks; indexes (a user's runs, active runs); the `set_updated_at` trigger; and the foreign key from `compute_task.run_id` (cascade).
  * **Security:** a new chain (order 5, before the fallback, now order 6) on `/ga4gh/tes/{platformId}/v1/**`. Its `authenticationManagerResolver` sends `Bearer rcrun_…` to the run-token provider and everything else to a JWT provider. Both are `ProviderManager`s with no parent. `canAccessComputePlatform` and `ComputeCallerResolver` gained the run branch.
  * **Option (a), disabled platforms:**
    * `ComputePlatformAvailabilityFilter` lets a valid token for an **active run on that platform** straight through, past both the disabled (`404`) and unhealthy (`503`) checks. The TES layer copes with an executor outage itself: creates queue, and reads come from our records.
    * `TesTaskController` looks up the platform regardless of its enabled state for run callers (`ComputeServiceInfoService.findPlatform`).
  * `TesTaskService.cancelRunTasks(runId)` cancels a run's leftover tasks when its engine is gone.
* **Run directory:**
  * **Files written:** `.refcloud-launcher.sh`, `refcloud.config` (no secret: the token is read from the environment), `params.json`, `engine.pid`, `engine.exitcode`, `engine.stdout.log` / `engine.stderr.log`, `launcher.log`, `nextflow.log`.
  * **When a run ends:** `inputs/`, `work/` and `.nextflow/` are removed.
  * **On Linux,** files written by Funnel's containers may be root-owned and not deletable by core-api; that's logged, not fatal. On macOS (Docker Desktop) they're owned by the user.
* **Outcomes:**
  * Exit 0 → `COMPLETE`. Any other exit → `EXECUTOR_ERROR`, except **126/127** (not executable / command not found) → `SYSTEM_ERROR` "the engine could not be started".
  * Any run with a cancel requested → `CANCELED`, however it exited (Nextflow exits non-zero on `SIGTERM`).
  * Engine gone with no exit code → `SYSTEM_ERROR` "the engine process was lost…", or `CANCELED` if a cancel was requested.
  * Launched but no pid recorded within 2× the launch timeout (core-api stopped mid-launch) → `SYSTEM_ERROR`.
* **Process identity:** the engine's **start time** is the identity, compared to the recorded one within 1 s. The command line is used only when the OS doesn't report a start time. Requiring the command line to contain the run directory was too strict (it isn't there for every process) and fragile on macOS temp paths.
* **Security bug found and fixed** in the end-to-end check: a valid run token was accepted as an **authenticated identity on every path** (DRS, WES, `/datasets`). No data was exposed (those checks look for a Hydra `Jwt` and treated it as anonymous), but it passed "must be authenticated" rules everywhere.
  * **Cause:** `RunTokenAuthenticationProvider` was a `@Component`. Spring Security adds a lone `AuthenticationProvider` bean to the **global** `AuthenticationManager`, which every chain falls back to when its own provider fails.
  * **Fix:** it's no longer a bean; `SecurityConfig` constructs it for the TES chain only.
  * Re-verified: a valid run token gets `401` on DRS, WES and `/datasets`, `200` on its own TES and `403` on another platform's TES. A guardrail was added to `apps/core-api/CLAUDE.md`.
* **nf-ga4gh doesn't survive a TES outage (D23).** In the restart test, core-api reattached correctly, but Nextflow had already aborted. nf-ga4gh's `checkIfRunning()` / `checkIfCompleted()` call `client.getTask(...)` with no error handling, so the first failed status request (`ConnectException` while core-api was down) aborted the whole workflow. Nextflow exited 1, and the run ended `EXECUTOR_ERROR`. **Decision (option 1): fix it upstream**, like D22.
  * A patch, verified here, makes status requests tolerate transient errors (no response, or `5xx`): each is logged and retried on the next poll, and only errors lasting longer than the new `tes.pollErrorTimeout` (default `10m`) fail the task.
  * The patch also declares the option in `TesConfig` and adds Spock tests (transient vs permanent errors; retry until the timeout; no retry on `404`). All 68 nf-ga4gh tests pass.
  * **Verified end to end with the patched plugin:** with core-api down for ~20 s mid-run, Nextflow logged 4 retries, carried on when core-api returned, and the run completed with its output.
  * **Until the fix is released:** restarting core-api (more than a few seconds of downtime) fails the workflows in flight. The poller still records their end, cancels leftover tasks and cleans up.
  * Patch (`src/main`; the tests are in the upstream PR):

    ```diff
    diff --git a/src/main/groovy/nextflow/ga4gh/tes/config/TesConfig.groovy b/src/main/groovy/nextflow/ga4gh/tes/config/TesConfig.groovy
    index ef546b1..3032ec4 100644
    --- a/src/main/groovy/nextflow/ga4gh/tes/config/TesConfig.groovy
    +++ b/src/main/groovy/nextflow/ga4gh/tes/config/TesConfig.groovy
    @@ -62,6 +62,14 @@ class TesConfig implements ConfigScope {
         ''')
         String pollInterval
     
    +    @ConfigOption
    +    @Description('''
    +    How long task status requests may keep failing with transient errors (the TES server unreachable, or answering
    +    with a 5xx status) before the task is failed, for example `10m` (default). Until then, each failed request is
    +    logged and retried on the next poll, so a TES server restart doesn't abort the workflow.
    +    ''')
    +    String pollErrorTimeout
    +
         @ConfigOption
         @Description('''
         API key parameter mode: `query` or `header`.
    diff --git a/src/main/groovy/nextflow/ga4gh/tes/executor/TesExecutor.groovy b/src/main/groovy/nextflow/ga4gh/tes/executor/TesExecutor.groovy
    index 4504939..4417d2d 100644
    --- a/src/main/groovy/nextflow/ga4gh/tes/executor/TesExecutor.groovy
    +++ b/src/main/groovy/nextflow/ga4gh/tes/executor/TesExecutor.groovy
    @@ -201,6 +201,14 @@ class TesExecutor extends Executor implements ExtensionPoint {
             return tpm;
         }
     
    +    /**
    +     * How long task status requests may keep failing with transient errors before the task is failed
    +     * (config `tes.pollErrorTimeout`, default 10 minutes)
    +     */
    +    Duration getPollErrorTimeout() {
    +        return Duration.of(session.config.navigate('tes.pollErrorTimeout', '10m') as String)
    +    }
    +
         /*
          * Prepare and launch the task in the underlying execution platform
          */
    diff --git a/src/main/groovy/nextflow/ga4gh/tes/executor/TesTaskHandler.groovy b/src/main/groovy/nextflow/ga4gh/tes/executor/TesTaskHandler.groovy
    index fae1136..e640b6f 100644
    --- a/src/main/groovy/nextflow/ga4gh/tes/executor/TesTaskHandler.groovy
    +++ b/src/main/groovy/nextflow/ga4gh/tes/executor/TesTaskHandler.groovy
    @@ -25,6 +25,8 @@ import static nextflow.processor.TaskStatus.RUNNING
     
     import java.nio.file.Path
     
    +import java.io.IOException
    +
     import groovy.transform.CompileStatic
     import groovy.util.logging.Slf4j
     import nextflow.exception.ProcessUnrecoverableException
    @@ -103,11 +105,62 @@ class TesTaskHandler extends TaskHandler {
     
         protected String getRequestId() { requestId }
     
    +    /**
    +     * When the current run of consecutive transient status-request failures began, or {@code null} if the last
    +     * status request worked
    +     */
    +    private Long pollErrorsSinceMillis
    +
    +    /**
    +     * Gets the task's status from the TES server. A transient failure (the server unreachable, or answering with a
    +     * 5xx status) is logged and returns {@code null}, meaning "no change yet", so the next poll tries again; only
    +     * failures lasting longer than {@code tes.pollErrorTimeout} fail the task. Without this, a TES server restart
    +     * aborted the whole workflow on the first failed poll
    +     */
    +    protected TesTask getTaskStatus() {
    +        try {
    +            final response = client.getTask(requestId, null)
    +            pollErrorsSinceMillis = null
    +            return response
    +        }
    +        catch( Exception e ) {
    +            if( !isTransientError(e) )
    +                throw e
    +            final now = System.currentTimeMillis()
    +            if( pollErrorsSinceMillis == null )
    +                pollErrorsSinceMillis = now
    +            final timeout = executor.getPollErrorTimeout()
    +            if( now - pollErrorsSinceMillis > timeout.toMillis() ) {
    +                log.warn "[TES] Status requests for task > $task.name have failed for longer than $timeout; giving up"
    +                throw e
    +            }
    +            log.warn "[TES] Could not get the status of task > $task.name; retrying on the next poll -- cause: ${e.message ?: e.class.simpleName}"
    +            return null
    +        }
    +    }
    +
    +    /**
    +     * A failure worth retrying: no response at all (connection refused, timeout) or a 5xx status
    +     */
    +    static boolean isTransientError(Throwable error) {
    +        for( Throwable cause = error; cause != null; cause = cause.cause ) {
    +            if( cause instanceof ApiException ) {
    +                final code = ((ApiException) cause).code
    +                return code == 0 || code >= 500
    +            }
    +            if( cause instanceof IOException )
    +                return true
    +        }
    +        return false
    +    }
    +
         @Override
         boolean checkIfRunning() {
     
             if( requestId && isSubmitted() ) {
    -            final response = client.getTask(requestId, null)
    +            final response = getTaskStatus()
    +            if( response == null )
    +                return false
                 final started = response.state in STARTED_STATUSES
                 if( started ) {
                     log.trace "[TES] Task started > $task.name"
    @@ -125,7 +178,9 @@ class TesTaskHandler extends TaskHandler {
                 return false
             }
     
    -        final response = client.getTask(requestId, null)
    +        final response = getTaskStatus()
    +        if( response == null )
    +            return false
             normalizeTaskUrls(response)
             if( response.state in COMPLETE_STATUSES ) {
                 // finalize the task
    ```
* **Unit tests (20 new; core-api total 194):**
  * `RunTokensTest`: format and randomness, SHA-256, header parsing.
  * `NextflowEngineTest`: the config text, including Groovy escaping, and no secret in it; the exact command line, with `--outdir` last; the environment, where reserved variables always win; reserved-variable detection.
  * `HostProcessRunnerTest`, using real processes through the launcher: exit code, stdout/stderr and environment; `SIGTERM` (143); `SIGKILL` of a process trapping `TERM` (137); lost; a reused pid never matched or signalled; a missing executable (127).
  * `ComputeRunServiceTest`: outcomes.
* **End-to-end check** against a real Funnel 0.11.14, a throwaway Postgres, the real health checks, a real Nextflow 26.10.5 + nf-ga4gh 1.5.0 launched by core-api, and a temporary harness (deleted after) that wrote a small workflow into each run directory, as WES will. The connection allowed 1 concurrent run. Results:
  1. **A normal run:** Nextflow → refcloud TES (run token over HTTP) → Funnel. `COMPLETE`, output published to `outputs/`, one task `COMPLETE` with core-hours. `work/`, `inputs/` and `.nextflow/` removed; logs kept; token hash cleared.
  2. **Queue and cancel:** run 3 stayed `QUEUED` while run 2 ran. Cancelling run 2 (`CANCELING` → `CANCELED`) cancelled its TES task, and run 3 then ran and completed.
  3. **Run tokens:**
     * own platform: `POST /tasks` `200`, `GET` `200`;
     * other platform: `403`;
     * an unknown token: `401`;
     * platform disabled mid-run: the run token still gets `200`, a researcher gets `404`;
     * after the run ends: `401`;
     * outside TES: `401` (after the fix above).
  4. **Max duration** (per-user 20 s): the run was cancelled about 24 s after it started, ending `CANCELED` with "exceeded the max run duration of PT20S".
  5. **Restart:** reattach worked. With the released nf-ga4gh the run then failed (above); with the patched plugin and ~20 s downtime, it completed.
  6. **Lost** (engine and launcher killed while core-api was down): after restart, `SYSTEM_ERROR` with the lost reason; its task still running in Funnel was cancelled; `work/` removed; token cleared.
* **Checks:** `./gradlew staticAnalysis` passes; 194 tests pass.
* **Runs that lost contact with the refcloud end `SYSTEM_ERROR`** (done at the user's request, after the main Task 7 work):
  * `WorkflowEngine.refcloudFailure(run)`, which defaults to none, is consulted only when an engine exits non-zero without a cancel. `NextflowEngine` reads the last 256 KB of the run's own `nextflow.log` and finds the **last** `Session aborted -- Cause: …` line. If the cause names a request to **this platform's** refcloud TES path (`/ga4gh/tes/<platformId>/v1/`; the host isn't compared, in case the callback URL changed), the run ends `SYSTEM_ERROR` with "the workflow engine lost contact with the refcloud TES: <cause>" instead of `EXECUTOR_ERROR`.
  * With the D23 upstream fix this only happens after an outage longer than `tes.pollErrorTimeout`.
  * **Tests:**
    * `NextflowEngineTest`, with fixtures: an excerpt of the real log from the failed restart run (`src/test/resources/compute/nextflow-lost-refcloud-tes.log`), and a failed-workflow log. Cases: the real abort → refcloud failure; the same log for another platform → none; a workflow failure → none; no log → none; the last abort cause wins.
    * `ComputeRunServiceTest`: a lost-contact failure → `SYSTEM_ERROR` with the reason; a cancel still → `CANCELED`; exit 0 still → `COMPLETE`.
  * `staticAnalysis` passes and 200 core-api tests pass.


### Task 8: Refcloud WES API (D11, D17, D19, D20)

* Implement the WES 1.1.0 endpoints under `/ga4gh/wes/{platformId}/v1`. Each user sees only their own runs.
* **`POST /runs` returns quickly; preparation happens in the background** (decided 2026-11-12).
  * **Quick checks** (all database work, no network), in this order. The first failure rejects with a clear message:
    1. **Auth and visa:** a valid token, and the caller holds the platform's visa.
    2. **Allowlist (D20, D21):** `workflow_url` exactly matches an allowlist entry. `workflow_type` / `workflow_type_version`, and `workflow_engine` / `workflow_engine_version` if given, must match the entry and connection. `workflow_attachment` and `workflow_engine_parameters` are rejected; the engine runs only with the entry's own engine settings.
    3. **Concurrent runs:** the user's queued **and** running runs on the platform, against the per-user limit (D11).
    4. **Monthly quota:** the user's core-hours on this platform from tasks that **finished this calendar month (UTC)**, against the per-user quota; and the same across every platform on the connection, against the pooled budget. The message says which quota was hit and when it resets (the start of the next month, UTC).
    5. **DRS inputs (D17):** every `drs://` URI in `workflow_params` must be this refcloud's (host = `ga4gh.refcloud.drs.host-domain`), S3-backed (not a manifest), and accessible to the caller (`DrsObjectService.validateUserIsAuthorizedForDrsObject`). Their total size (from DRS) must fit the connection's new **`max-run-inputs-gb`** limit (e.g. 10 GB locally; a cloud deployment sets its own in its connections).
  * Then the run is recorded and its `run_id` returned. The run shows **`QUEUED`** until it's prepared and pooled capacity allows.
  * **Background preparation:**
    * put the workflow into the run directory (below);
    * download each DRS input into `inputs/<object id>/<name>` and rewrite its parameter to that path (strings anywhere in `workflow_params`, however nested).

    Only prepared runs are launched (a small change to Task 7's run service). A preparation failure ends the run `SYSTEM_ERROR` with the reason.
* **Workflow resolution (D20):**
  * **git and TRS entries:** fetch exactly the pinned commit into `workflow/` (`git init` + `git fetch --depth 1 <repo> <sha>` + checkout; GitHub allows fetching a commit by SHA). core-api needs `git` on the host natively; the core-api image needs it in stage 2.
  * **Bundled entries:** copied from the entry's `bundled_path` (as the admin CLI stores it, e.g. `compute/workflows/nextflow/drs-checksum`), resolved against the new setting `ga4gh.refcloud.compute.wes.bundled-workflows-base-dir`. It defaults to `../..`, the repo root when core-api runs from `apps/core-api`.
  * **A main script other than `main.nf`** is passed to Nextflow as `-main-script` (a small addition to Task 7's engine).
  * The workflow is always inside the run directory, because of TES's file rule (Task 6).
* **Outputs and logs (D18, D19):**
  * `RunLog.outputs` is `{"files": [{"path", "size", "url"}]}` for every file under `outputs/`.
  * `GET /runs/{run_id}/outputs/{path}` streams a file, owner only. `{path}` resolves strictly inside `outputs/`: `..`, absolute paths and symlinks pointing outside are rejected.
  * `run_log.stdout` / `stderr` are URLs to a small non-standard endpoint, `GET /runs/{run_id}/logs/{stdout|stderr}` (owner only; the engine's console output), like D19.
* **`GET /runs/{run_id}/tasks`** lists the run's TES tasks from `compute_task`, each with its refcloud TES URL (`tes_uri`).
* **service-info:** `system_state_counts` now has real per-state run counts for the platform.
* **Disabled platforms (decided 2026-11-12, option b):** a run's **owner** keeps read access to their own runs on a disabled platform: `GET /runs/{id}`, `/status`, `/tasks`, `/outputs/...`, `/logs/...`, and `POST /runs/{id}/cancel`. They can't submit runs, list the platform's runs, or see its service-info, and the platform stays out of the service registry and UI. The availability filter runs before authentication, so it can't check ownership. It lets per-run paths (`/runs/{run_id}` and below) on a disabled platform through to normal authentication and the visa check, and the WES service serves a run only to its owner (anyone else gets `404`). The platform's other paths stay `404`.
* **Errors** use the WES `ErrorResponse` format, through an exception handler scoped to the WES package.

**Decisions (Task 8):**

* **Code (core-api):**
  * `ComputeWesConfig` (`ga4gh.refcloud.compute.wes`): `bundled-workflows-base-dir` `../..`, `preparation-sweep-interval` 30s, `git-timeout` 5m, `download-timeout` 30m, `default-page-size` 256, `max-page-size` 2048.
  * `ComputeAsyncConfig`: preparation runs on its own pool (`computePreparationExecutor`, 2–4 threads), so slow downloads never hold up the scheduled jobs. `spring.task.scheduling.pool.size` is now 4.
  * `wes/`:
    * `WesRunController`;
    * `WesRunService`: the quick checks, list, `RunLog`, status, tasks, cancel, outputs and logs;
    * `RunPreparationService`: background preparation plus the sweep;
    * `DrsInputs`: find, check and rewrite `drs://` inputs;
    * `WesViews`: the WES 1.1.0 response records.
  * `ComputePageToken` (was `tes/TesPageToken`) is shared by TES `GET /tasks` and WES `GET /runs` and `GET /runs/{run_id}/tasks`.
  * `DrsObjectService.getStagingInfo` returns a `DrsStagingInfo`: the DRS object's name, size, manifest flag and S3 https URLs.
  * **New connection limit `max-run-inputs-gb`:** `10` in `deploy/local/compute/connections.yml`.
  * **Changeset `modify-tb-compute-run-add-preparation`:** adds `prepared_at`, `main_script`, `compute_platform_workflow_id` (FK, `ON DELETE SET NULL`), `workflow_source` (a snapshot of the allowlist entry's source) and `drs_inputs` (a JSON array).
  * **Changes to Task 7's code:**
    * `RunSubmission` gains the WES fields and `prepared`.
    * Only prepared runs launch. A prepared run doesn't wait behind one still being prepared, so runs can start in a different order than they were submitted.
    * `ComputeRunService` gains `markPrepared` and `failPreparation`.
    * `NextflowEngine` passes `-main-script` when it isn't `main.nf`. It now also removes `workflow/` when a run ends: the run records where the workflow came from.
* **Security bug found and fixed in the end-to-end check:** WES paths fell to the fallback filter chain, whose CSRF check refused every `POST /runs` with `403`, even with a valid token.
  * **Fix:** WES has its own chain (order 6; the fallback is now order 7). It accepts Hydra JWTs only, is stateless, and has no CSRF.
  * Run tokens get `401` on WES.
* **Request checks as built:**
  * **Missing required field:** `400`, with Spring's "Required parameter '…' is not present."
  * **Rejected outright (`400`):** `workflow_attachment` (a file part or a plain field), and non-empty `workflow_engine_parameters`. `{}` counts as none.
  * **Engine fields:** `workflow_engine` is compared to the connection's engine case-insensitively; `workflow_engine_version` must match exactly.
  * **Limits (`403`):** the concurrent-run limit and the quotas. The message says what to wait for, and the quota message gives the reset time.
  * **DRS:** an object the caller can't access is `403`. An unknown object, another host, a manifest, an object with no S3 copy, or inputs over the size limit are `400`.
  * **Body format:** `multipart/form-data` (or url-encoded). A JSON body is `415`.
  * **Known race:** the concurrent-run check isn't atomic with recording the run, so two simultaneous submissions can both pass. Accepted for a reference implementation.
* **Workflow resolution:**
  * **git and TRS entries:**
    * the repository must be `https://github.com/{owner}/{repo}` and the commit a full 40-character SHA; both are checked again at preparation;
    * `git init`, `git fetch --depth 1 <repo> <sha>`, `git checkout --detach FETCH_HEAD`, then `git rev-parse HEAD` must equal the pinned SHA;
    * git runs with a fixed argument list (no shell) and `GIT_TERMINAL_PROMPT=0`, and is killed at the timeout or when core-api shuts down.
  * **Bundled entries:** the path must stay inside the base directory. Only regular files are copied; symlinks are skipped.
* **Preparation:**
  * **Downloads:**
    * `https://` URLs only;
    * each goes to a `.part` file, is checked against the size DRS reports, then moved into place;
    * the target is `inputs/<object id>/<name>`, each made safe as one path segment.
  * **Params:** rewritten to the absolute local paths. `RunLog.request` keeps the original `drs://` URIs.
  * **Shutdown** interrupts preparation. The run stays unprepared, and the sweep prepares it after the restart.
  * **A run cancelled while it's being prepared** stays `CANCELED`. The preparation notices and stops quietly.
* **Responses:**
  * **`run_log`:**
    * `name`: the allowlist entry's name;
    * `start_time` and `end_time`;
    * `stdout` and `stderr`: URLs;
    * `exit_code`;
    * `system_logs`: the state reason.

    `cmd` is left out: the engine command line is full of host paths.
  * **`TaskLog`:**
    * `id`, `name` and `tes_uri`;
    * `cmd`: the first executor's command;
    * `start_time` and `end_time`;
    * `exit_code`: the last attempt's last executor.

    No `system_logs`: the executor's are Funnel's internal log lines, which the TES views drop too (Task 6). The first version copied them; caught in review, and a unit test now pins it.
  * **Other details:**
    * `next_page_token` is `""` on the last page, as WES specifies.
    * `system_state_counts` lists every WES state, zeros included.
    * Logs requested before the engine starts are an empty `200`.
    * Outputs download as `application/octet-stream` attachments.
  * **Default error bodies:** errors raised before a WES controller is chosen use Spring's default error body, not WES `ErrorResponse`. Examples: the firewall's `400` for `..` in a path, and `415`. See Future Directions.
* **Option (b) as built:**
  * The availability filter looks up a disabled platform only for WES per-run paths (`/runs/{run_id}` and below). The request then goes on to authentication; the health rule still applies.
  * In the controller, per-run endpoints look up the platform whatever its enabled state; submitting and listing need it enabled.
* **Verified end to end**, with a throwaway Postgres, Funnel, Nextflow 26.10.5 with the patched nf-ga4gh (D22, D23), and JWTs from a local key server standing in for Hydra:
  * **Every rejection above** returned its status and message, plus no token → `401` and no visa → `403`.
  * **A bundled checksum workflow** with the 1000 Genomes `HG00096…bam.bai` as a `drs://` input:
    * the input was downloaded (8,747,040 bytes) and the params rewritten;
    * the run reached `COMPLETE`, and its report (name, size, MD5, SHA-256) downloaded through `outputs`;
    * `stdout`/`stderr`, `/tasks` and `tes_uri` all work;
    * another researcher gets `404` on the run, its outputs and its cancel;
    * `..` and `%2e%2e` in an output path are refused.
  * **Limits:** a third concurrent run → `403`; cancelling a running run → `CANCELING`, then `CANCELED`; over quota → `403`, "resets at 2026-12-01T00:00:00Z".
  * **Paging:** `page_size=1` gives a token; the next page differs; the last page's token is `""`; a bad token → `400`.
  * **Disabled platform (option b):** the owner keeps `GET /runs/{id}`, `/status`, `/tasks`, outputs, logs and cancel. Another researcher gets `404`, no token gets `401`, and list, submit and service-info are `404`.
  * **nf-core/demo 1.0.2, git-pinned to `db7f526`** (`-profile test`, `NXF_SYNTAX_PARSER=v1`): fetched, `COMPLETE`, 7 tasks, 74 output files including `multiqc/multiqc_report.html`. core-api restarted mid-run and reattached.
  * **Cancelled while preparing:** stays `CANCELED`; only `outputs/` is left.
  * **Restart:** a run left queued and unprepared by a stopped core-api is picked up by the sweep, completes, and its `workflow/` is removed.
* **Unit tests (TestNG; 26 new, core-api total 220):** `DrsInputsTest`, `WesRunServiceTest`, `RunPreparationServiceTest`. core-api has no Mockito, so the submit checks are static methods tested directly. `@WebMvcTest` slices come with Task 12.

### Task 9: Bundled demo workflow (D20)

* Add one bundled Nextflow workflow, `drs-checksum`. It takes one input file and writes a small report with the file name, size, MD5 and SHA-256.
* Location: `compute/workflows/nextflow/drs-checksum/`, tracked in git (see Task 10 for the ignore rules).
* Find an existing 1000 Genomes `.bai` DRS object for the demo input, and note its ID in the run script's output and in the docs. Don't create new datasets.

**Decisions (Task 9):**

* **Files** (`compute/workflows/nextflow/drs-checksum/`; `compute/.gitignore` already tracks `workflows/`):
  * `main.nf`, `nextflow.config` (manifest only);
  * `README.md`: what it does, the demo input and its visas, the expected report, how an admin allowlists it, an example WES submission, and a standalone run.
* **The workflow:**
  * one process, `CHECKSUM`, in `ubuntu:24.04` (task images need `bash`, Task 1), pinned by tag like the Compose images;
  * one parameter, `input`; core-api sets `outdir`;
  * it writes `checksum-report.json` (`name`, `size`, `md5`, `sha256`), published to `outdir`;
  * a missing input fails with a clear error.
* **Strict Nextflow 26 syntax:** `nextflow lint` passes on Nextflow 26.10.5, so the allowlist entry needs no engine settings. No `env`/`eval` outputs, so it works on released nf-ga4gh 1.5.0 (D22).
* **Safe with any file name:** the input is staged under a fixed name, so the shell never quotes an arbitrary name. The original name goes into the report as an escaped JSON string. Through WES, staged names are already safe (Task 8), but standalone runs aren't. Checked with a file named `it's a "test" file.bai`: the report is valid JSON, and its checksums match the host's `md5` and `shasum`.
* **Demo input:**
  * `drs://refcloud.ga4gh.localhost/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai`, 6,842,584 bytes.
  * The 1000 Genomes onboarding (`apps/admin-cli/.../onboard_1000_genomes.py`) uses each S3 file name as the DRS object ID, so the ID is the same on every setup. HG00096 is the first subject onboarded.
  * It reads `exome_alignment/` files; Task 8's test used a `low_coverage` file that isn't onboarded.
  * Its DRS checksum (`type` `etag`, the S3 ETag) equals its MD5, so the report's `md5` can be checked against DRS.
* **Two visas (decided 2026-11-12):** the 1000 Genomes dataset is visa-gated (`ga4gh_visa:aws_opendata_registry_1000_genomes`). So the demo researcher needs both the dataset's visa and the platform's. The Objective now says so, and D20 has a note.
* **The demo ID** is recorded in the workflow's README now. Tasks 10 (run script output) and 12 (docs page) now say to pick it up.
* **Verified end to end** (throwaway Postgres, Funnel, Nextflow 26.10.5):
  * **Admin CLI on the throwaway database:**
    * `seed`, `dataset init`, then the real `dataset onboard`, stopped once HG00096 was in. The `.bai` row matched the S3 listing (ID, size, key, `etag`).
    * `compute-platform init` (it correctly refused a per-user limit above the connection's ceiling), `allowlist add --bundled drs-checksum` (it found the folder), `enable`, and `visa approve`.
  * **core-api** used its default `bundled-workflows-base-dir` (`../..`, the repo root), so the workflow came from this folder.
  * **A researcher with only the platform's visa** → `403` "you don't have access to DRS object …".
  * **A researcher with both visas** → `COMPLETE`, with one output, `checksum-report.json`:
    * name `HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai`;
    * size 6842584;
    * md5 `a8a1f1ba420f7d75c7955b04b5972c54`, equal to the DRS checksum;
    * sha256 `89d6677f9e8d54fd3d771177ab352fb5d7435cf4ad1fb5dec33e5a508612f5c0`.
  * **After the run:** the run directory kept only `outputs/` and logs.

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
* **Run script:** `./scripts/refcloud-run-native.sh` lists the platform's WES and TES endpoints alongside the other GA4GH services. It also prints the demo input's DRS URI (Task 9: `drs://refcloud.ga4gh.localhost/HG00096.mapped.ILLUMINA.bwa.GBR.exome.20120522.bam.bai`), the two visas it needs, and a pointer to `compute/workflows/nextflow/drs-checksum/README.md`.
* **Helm mode:** stays working with compute **off**. No connections are configured and nothing compute-related is deployed (stage 2 adds it). Both modes share the host databases, so the platform setup registers shows as unavailable in helm mode (decided 2026-11-12). Run `./scripts/refcloud-static-analysis-helm.sh` and `./scripts/refcloud-static-analysis-deploy-local.sh`.
* **Docs:** add a `compute/` entry to the root `CLAUDE.md` repository layout. Also document native-mode compute in `deploy/local/README.md`, keeping the root `README.md` to a pointer.
* **System dependencies:** the root `README.md` requirements table lists every new host dependency with its minimum version, and the docs say what runs where:
  * Nextflow and the Java version it needs run **natively on the host**: core-api launches `nextflow run` as a subprocess.
  * Funnel runs **in a Docker container** (Compose) and starts task containers through the host's Docker socket, so it adds no host dependency beyond Docker.

**Decisions (Task 10):**

* **Already in place from earlier tasks:** the default connection (`nextflow-funnel-docker-local` in `deploy/local/compute/connections.yml`, Task 3); `compute/.gitignore` (Task 1); and the Caddy and Helm ingress routes for `/ga4gh/wes/*` and `/ga4gh/tes/*` (Task 5).
* **Funnel (decided 2026-11-12: always started, linted with a scoped exception):**
  * `deploy/local/funnel/Dockerfile`: `quay.io/ohsu-comp-bio/funnel:0.11.14` plus `docker-cli` (Task 1), pinned by tag like the app images. It runs as root, which using the host's Docker socket needs.
  * `deploy/local/funnel/funnel.yml`: a static config with the Task 1 settings (local compute, BoltDB, and the `RunCommand` with a writable `/tmp`).
  * **Absolute paths come in as flags.** Funnel's work directory, BoltDB file and allowed directories are absolute host paths, so `docker-compose.yml` passes them as flags (`--Worker.WorkDir`, `--BoltDB.Path`, `--LocalStorage.AllowedDirs`). They're built from `REFCLOUD_COMPUTE_STORAGE_ROOT`, which `scripts/lib/common.sh` exports (`$PWD/compute/nextflow-funnel-docker`). It's also the bind mount, at the same path inside the container.
  * Compose refuses to start without the variable, with a message saying what to set. Running Compose by hand is documented.
  * **The `funnel` service:**
    * always started, like Kratos and Hydra;
    * the TES API is published on `127.0.0.1:8000` only, since it has no auth;
    * the Docker socket is mounted (local development only, said in a comment and the docs);
    * the image is built locally as `refcloud-local/funnel:0.11.14`;
    * the base image has a healthcheck, so `up --wait` waits for Funnel to be ready.
* **Static analysis (`scripts/refcloud-static-analysis-deploy-local.sh`):**
  * hadolint and Trivy now also scan `deploy/local`'s Dockerfiles.
  * Trivy scans `deploy/local` separately, with its own `deploy/local/.trivyignore`: `DS-0002` (root user), with its reason. So the apps' non-root rule is unchanged. Without the file, `DS-0002` fires on the Funnel Dockerfile, so the exception is needed.
  * The CVE image list leaves out locally built `refcloud-local/*` images and scans Funnel's base image through its Dockerfile instead.
  * The script sets `REFCLOUD_COMPUTE_STORAGE_ROOT` itself for `docker compose config`.
  * CI's `deploy/local/**` path filter already covers the new files.
* **Nextflow check (`nextflow_installed` in `scripts/lib/common.sh`):**
  * `nextflow` must be on the `PATH`.
  * `NXF_VER=<pinned> nextflow -version` must report the version pinned in the connections file. This is the same check as core-api's engine health check.
  * So the "minimum version" is the exact pinned version, whatever launcher is installed. The first check downloads it into `~/.nextflow` (internet access needed).
  * Without Nextflow, setup warns and skips compute.
* **Default platform (decided 2026-11-12: `nextflow-demo`):**
  * `init_default_compute_platform` runs from `apps/admin-cli` after the datasets are onboarded. It uses the admin CLI: `compute-platform init` (name "Nextflow Demo"), `allowlist add --bundled drs-checksum`, then `enable`.
  * Per-user limits: 2 concurrent runs; 2 CPU cores, 2 GB of memory and 10 GB of disk per task; 30 minutes per run; 10 core-hours a month.
  * An existing platform is left as it is, like datasets: an admin may have changed it. The log says how to recreate it.
* **`--reset`** deletes `compute/nextflow-funnel-docker/` (`reset_compute_storage`, which refuses any other path).
* **Run script:** `print_service_urls compute` (native mode only) lists every **enabled** platform's WES and TES endpoints, read from the database. It also prints the demo: the bundled workflow, its DRS input, the two visas it needs, and a pointer to the README. With no enabled platform, it says how setup registers one.
* **Docs:**
  * root `README.md`: a Nextflow row (`brew install nextflow`; Homebrew has 26.10.5) and a one-line pointer;
  * `deploy/local/README.md`: a "Compute (native mode)" section covering what runs where, storage, setup, the demo and its two visas, running Compose by hand, helm mode, and the D23 restart limitation;
  * `deploy/local/CLAUDE.md`: the new files, the Compose command, and notes on the Docker socket and the same-path mount;
  * root `CLAUDE.md`: the `compute/` directory, and that compute runs in native mode only.
* **Helm mode (decided 2026-11-12):** compute is off, but the databases are shared, so the `nextflow-demo` platform shows as unavailable there ("connection not configured"), per Task 5's rule. It's documented, not hidden. `./scripts/refcloud-static-analysis-helm.sh` passes; the chart is unchanged.
* **Verified:**
  * **Static analysis:** both suites pass.
  * **The Compose `funnel` service**, started alone under a separate project name with a scratch storage root:
    * built and became healthy;
    * its Docker CLI (29.9.3) reached the host daemon (29.4.0);
    * BoltDB was created under the storage root.
  * **The demo through that Funnel:** `drs-checksum` on the demo `.bai`, run by Nextflow 26.10.5 with nf-ga4gh 1.5.0's TES executor, completed with the expected report.
  * **The setup functions,** on a throwaway database (psql via `PGHOST`/`PGPORT`, the CLI via `POSTGRES_PORT`):
    * `nextflow_installed`: warns without Nextflow; passes with the pinned launcher;
    * `init_default_compute_platform`: registers the platform with its limits, allowlist entry and enabled state; a re-run skips it;
    * `print_service_urls compute`: correct before and after setup;
    * `reset_compute_storage`: refuses an unexpected path and deletes the expected one.
  * **Not run here:** the full `./scripts/refcloud-setup.sh` and `./scripts/refcloud-run-native.sh` on the developer's machine. They change the local databases, and Nextflow wasn't installed. The user runs them; Task 12 does the full "Done when" check.
* **The demo needs Task 11 first.** Access requests exist only for datasets today (`POST /datasets/{id}/request-access`). A researcher can't request the platform's visa until Task 11 adds the compute platform request-access endpoint and page, and `visa approve` needs a request to exist. `deploy/local/README.md` already describes the flow as it will be after Task 11.

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
  * how to run the demo: the demo input and its two visas, and the example submission, from `compute/workflows/nextflow/drs-checksum/README.md` (Task 9).

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
* **Direct TES task submission by researchers** (deferred 2026-11-12; researchers submit through WES only). It would need:
  * a **per-platform image allowlist**: a new table plus admin CLI commands. The workflow allowlist can't vet a task that names its container image and command directly, and a pipeline's images can't be derived reliably in advance;
  * input restrictions: `https://` and inline `content` only, no `file://`; `drs://` stays a WES concern (D17);
  * TES-side quota checks;
  * an output story, e.g. per-user storage and a download endpoint.
* **Runtime CPU/memory enforcement on Docker:** Funnel 0.11.14 doesn't pass task resources (or tags) to its `docker run` template. Patch or upstream it so `--cpus`/`--memory` apply (Task 6).
* Upstream: the nf-ga4gh transient-status-error retry (D23, patch in Decisions (Task 7)). Open an issue and PR alongside the `.command.env` one (D22); once released, bump the pinned nf-ga4gh version.
* **DRS inputs from other DRS servers** (asked 2026-11-12; stage 1 accepts only the refcloud's own DRS objects, D17):
  * **open-access objects:** resolve through that server's DRS API (object, then access URL or `access_id`). This needs an **admin-managed allowlist of trusted DRS hosts**, so core-api never fetches arbitrary URLs (SSRF).
  * **controlled access:** the refcloud's Hydra tokens only carry the refcloud's own visas, so the researcher would supply credentials for that server (a GA4GH Passport from a broker it trusts, or a bearer token it issued) through a clearly marked extension field on `POST /runs`, never in `workflow_params`. They'd be held in memory only for background preparation, then discarded; a core-api restart mid-preparation would fail the run with "resubmit".
* **Compute errors raised before a controller is chosen** use Spring's default error body, not the WES `ErrorResponse` (Task 8). Examples: the request firewall's `400` for `..` in a path, and `415` for an unsupported content type. Render them as `ErrorResponse` for `/ga4gh/wes/**` and `/ga4gh/tes/**`, e.g. through an error controller.
* **The run's engine command in `RunLog.run_log.cmd`**, with host paths shortened to run-directory-relative ones (Task 8 leaves it out).
* **Stage 2 core-api image:** it needs `git` (WES fetches pinned commits), and the bundled workflows copied in, with `ga4gh.refcloud.compute.wes.bundled-workflows-base-dir` pointing at them (Task 8).
* All other Future Directions in the discussion doc: Cromwell, TESK, DRS write-back, inputs from other DRS servers, retention and storage caps, serving last-known run state while a platform is unhealthy, and cloud deployment on AWS.
