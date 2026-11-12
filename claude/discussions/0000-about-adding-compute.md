# Discussion about adding compute standards (WES/TES) to the refcloud

## Context

This discussion started over on 2026-11-11. An earlier, paused discussion (2026-10-22, recorded in the [spike plan doc](https://claude.ai/artifact/XxCgh41zmfb56VhQjkUE1c)) is a source of ideas. Its conclusions don't bind this one.

## Goals

Compute in the refcloud serves four purposes. All four are goals, in this order:

1. **Illustration.** Show the standards working end to end, e.g. a workflow that reads a DRS object, runs, and produces outputs. Writing outputs back as new DRS objects comes later (D18).
2. **Reference for implementers.** The code and deployment show others how to build and connect WES/TES. Clear code matters, not just working code.
3. **Conformance target.** A known-good WES/TES that tool and client builders can test against.
4. **Real compute.** Others can take the codebase, deploy it, and run real compute jobs.

The first milestone is to demonstrate a working reference implementation. The long-term aim is a codebase that others deploy for real workloads, so nothing demo-specific should be built into the code.

## Terminology

A **Compute Platform** is the whole stack in the table below, registered and access-controlled as one unit. Each Compute Platform has its own refcloud WES and TES APIs, one workflow engine, one task executor, the compute infrastructure under them, and its own limits and visa. The layers are listed in request order, top to bottom:

| Layer | What it does | Examples | Where the term appears in the standards |
|---|---|---|---|
| **Refcloud WES API** | Accepts workflow runs from clients, checks auth and limits, and hands runs to the workflow engine | `apps/core-api` | WES |
| **Workflow engine** | Reads a workflow, works out the step order, and turns each step into a task | Nextflow, Cromwell, Toil, Snakemake | WES `workflow_engine`, and `workflow_engine_versions` in service-info |
| ↳ *Workflow language* | The format a workflow is written in. This is a property of the engine, not a separate component: choosing the engine fixes which languages the platform supports (e.g. Cromwell runs WDL and CWL) | CWL, WDL, Nextflow DSL, Snakemake | WES `workflow_type` |
| **Refcloud TES API** | Accepts tasks from the workflow engine (or directly from clients), checks auth, and hands tasks to the task executor | `apps/core-api` | TES |
| **Task executor** | Runs one containerized task: stage inputs, run the command, collect outputs | Funnel, TESK | The thing behind a TES API |
| **Compute infrastructure** | Where containers physically run | Kubernetes, Slurm, AWS Batch, local Docker | Not in the standards |

Usage rules:

- **Compute Platform** means the whole stack above. Nothing else.
- **Engine** means the workflow engine only.
- **Executor** means the task executor (the TES implementation, e.g. Funnel or TESK).
- **Backend** is used only when quoting third-party config. Funnel calls Kubernetes, Slurm and AWS Batch its "compute backends", and Cromwell calls the place tasks run a "backend" (TES is one of them).
- **Node** is avoided because in HPC and Kubernetes it means a single worker machine.

## Decisions

- **D1. Hybrid implementation.** The refcloud implements its own internet-facing WES and TES API entrypoints so they fit the refcloud's existing auth pattern. Existing workflow engines and task executors run behind them. Refcloud code is the API layer plus the glue that connects it to the engine and executor. We don't write a workflow engine or task executor.
- **D2. All four goals apply** (see Goals). Demonstrate first; others deploying it for real compute is the long-term target.
- **D3. Stacked request path.** `client → refcloud WES → workflow engine → refcloud TES → task executor`. The engine sends its tasks through our TES rather than its native executor, which shows WES and TES integrated top to bottom. Researchers submit work **only through WES**, and inspect it through WES and TES. In stage 1, TES is read-only for researchers (list, get, cancel their own tasks); only workflow engines, using a run-scoped token, create tasks. Direct task submission by researchers is a Future Direction (it needs a per-platform image allowlist).
  - This narrows engine choice to engines with a working TES client.
  - A user's token can expire during a long workflow, so the engine doesn't call TES with it. It uses a credential scoped to its run instead (D12): an opaque random token (`rcrun_…`) minted by core-api for each run. Only its hash is stored, it's valid only while the run is active and only on that run's platform, and it's passed to Nextflow as an environment variable.
- **D4. Compute lives in `apps/core-api`.** This optimizes for development time. We can split it into its own app later if scaling or self-hosting needs call for it.
- **D5. Admin-registered Compute Platforms, gated by visas.** Admins register one or more Compute Platforms in their refcloud. Each Compute Platform has a Passport visa, and researchers request access to it the same way they request access to a dataset. The researcher UI gets a "Browse Compute Platforms" page modeled on "Browse Datasets".
  - Schema impact: `passport_visa.dataset_id` is currently `NOT NULL` and unique (one visa per dataset), so it becomes nullable and a `compute_platform_id` is added (see D7).
  - Admin CLI impact: it needs to register Compute Platforms. Access requests (for datasets and platforms alike) are processed with new `refcloud visa` commands (`list`, `list-requests`, `approve`, `deny`, `revoke`), which replace today's raw SQL `UPDATE`.
- **D6. One WES and one TES service per Compute Platform.** Each registered Compute Platform gets its own base paths, e.g. `/ga4gh/wes/{platformId}/v1/...` and `/ga4gh/tes/{platformId}/v1/...`. The platform ID comes **before** the version: nf-ga4gh appends `/v1/tasks` to its TES endpoint, so `/ga4gh/tes/v1/{platformId}` can't work (found in the spike, 2026-11-11). WES uses the same shape for consistency. Each has its own service-info and is listed separately in the service registry, so registering a Compute Platform adds two services to the registry. Clients choose a platform the standard way, by choosing a service. Authorization asks whether the caller holds that platform's visa.
  - Service registry impact: `ServiceRegistryService` currently builds a fixed list of services from config when the app starts. It has to become dynamic, adding a WES and a TES service for each registered Compute Platform.
- **D7. A visa points at exactly one gated resource** (revised 2026-11-11). `passport_visa` keeps `dataset_id` (made nullable) and gains a nullable `compute_platform_id`. A check constraint requires **exactly one** of them to be set, so a visa always gates either a dataset or a Compute Platform, never neither and never both. Postgres enforces this even against raw SQL.
  - Each gated resource type adds one nullable column. That's fine for the two types we have.
  - The admin CLI keeps today's lifecycle: it creates a visa together with its resource and deletes them together.
  - Schema changes are **new** Liquibase changesets built on the existing ones; production can't be `--reset`.
  - Originally D7 had resources point at a standalone visa (`dataset.visa_id`, `compute_platform.visa_id`). That was dropped because the database can't enforce "exactly one owner" when the owners are in different tables, and it would have meant rewriting every dataset/visa query.
- **D8. The registered unit is called a "Compute Platform".** "Compute backend" was dropped because Funnel and Cromwell already use "backend" for the infrastructure layer. "Compute Node" was considered for its federated-network meaning but dropped because of the HPC meaning (one worker machine). See Terminology.
- **D9. Registration is split between deployment config and the database.**
  - **Connections** are defined in deployment config: a `connections.yml` file read by both core-api (`spring.config.import`) and the admin CLI, located by `REFCLOUD_COMPUTE_CONNECTIONS_FILE` (locally `deploy/local/compute/connections.yml`; in helm, a ConfigMap rendered from values, with production connections in the deployer's own values file). Secrets stay in Kubernetes secrets; the file only names the environment variable that holds each one. A connection is a named description of how to reach one engine and executor setup on some compute infrastructure: how the engine is launched, the executor URL, service credentials, storage.
  - **Compute Platforms** are catalog and policy records in the database: name, description, visa, workflow allowlist, per-user limits, enabled/disabled. Each one refers to a connection by key.
  - One connection can back more than one Compute Platform, e.g. a "Demo" platform with tiny limits and a loose visa, and a "Full" platform for approved researchers, both on the same Funnel cluster.
  - This separates the deployer's concerns from the admin's. In practice, for reference and PoC deployments, the deployer and admin will usually be the same team or person. Tooling should make that easy, e.g. local setup registers a default platform for the default connection.
- **D10. Connection health checks.** The refcloud regularly checks the health of every connection that backs a registered Compute Platform. Platforms sharing a connection share its health. A platform whose connection is unhealthy, or whose connection key isn't in config, is marked **unhealthy**. Startup doesn't fail.
  - **Isolation:** an unhealthy connection never fails the whole application. It blocks access only to the platforms it backs, until it recovers. Connection health is kept out of core-api's Kubernetes liveness and readiness probes.
  - **API behavior:** every request to an unhealthy platform's WES or TES returns `503` with a GA4GH error body. The one exception is `service-info`, which keeps responding normally and reports the health.
  - **Health in service-info:** each WES and TES service-info gets an optional health property: status plus the time it was last checked. Health in service-info has been discussed as an extension to the service-info spec, so we put it where the spec is likely to land rather than in a refcloud-only endpoint. If the spec settles on a different shape, we update the refcloud to match.
    - Checked 2026-11-11 against the service-info spec (`ga4gh-discovery/ga4gh-service-info`, `develop`): no schema sets `additionalProperties: false`, so an extra property is valid. The spec has no existing health, status or uptime field.
    - Shape: `"health": { "status": "healthy" | "unhealthy", "checkedAt": "<ISO 8601 timestamp>" }`, e.g. `"health": { "status": "unhealthy", "checkedAt": "2026-11-11T15:04:05Z" }`.
    - Because the service registry's `GET /services` returns each service's service-info, health shows up in the registry listing automatically.
  - **Service registry:** keeps listing every platform's services whatever their health, so service IDs stay stable.
  - **Researcher UI:** "Browse Compute Platforms" shows each platform's health and labels a platform with an unhealthy connection "unhealthy".
- **D11. Two-stage limits.**
  - **Connection limits** (set by the deployer in config) cap how heavily the infrastructure is used in total, pooled across all users and all platforms on that connection.
  - **Per-user limits** (set by the admin through the admin CLI, per Compute Platform) cap what one user can use on that platform.
  - Coverage:

    | Limit | Connection (pooled, deployer) | Per user, per platform (admin) |
    |---|---|---|
    | Concurrent runs | ✓ total | ✓ |
    | Tasks or CPU/memory in flight | ✓ total | none |
    | Per-task resources (CPU, RAM, disk) | ✓ the most the infrastructure can run | ✓ must be ≤ connection's |
    | Max run duration | ✓ ceiling | ✓ must be ≤ connection's |
    | Compute-hours per month | optional total budget | ✓ quota |

  - **Ceiling rule:** a per-user value can never exceed the connection's ceiling. The admin CLI rejects such values when they're set, and at runtime the lower of the two applies.
  - **When a limit is hit:**
    - **Monthly quota used up** (per-user quota or pooled budget) → **reject** the submission with a clear message saying which quota was hit and when it resets. Runs aren't queued for this, because queueing suggests the run will start soon when it may not start until next month.
    - **Pooled concurrency full** (concurrent runs, tasks or CPU/memory in flight) → **queue** (`QUEUED` in WES/TES) until capacity frees up.
    - **Per-user limit** (concurrent runs, run duration, per-task resources) → **reject** at submission.
    - **Per-task request larger than the infrastructure ceiling** → **reject**, because it could never run.
    - **Quota runs out partway through a run** → the run is allowed to finish (bounded by max run duration). New submissions are rejected.
  - **Where limits are enforced:** WES checks allowlist, concurrent runs and quota when a run is submitted. TES checks per-task resources and pooled task concurrency when each task is created. Because every task passes through the refcloud TES (D3), task-level enforcement lives in one place, whether tasks come from a workflow or straight from a client.
- **D12. The first platform is Nextflow + Funnel.**
  - **Engine: Nextflow**, with the nf-ga4gh TES executor, so the engine sends tasks to the refcloud TES (D3). Nextflow is a CLI, not a server: the WES glue launches one `nextflow run` per WES run (a host subprocess in native mode, a Kubernetes Job in helm mode). How the engine is launched is part of the connection config (D9).
  - **Executor: Funnel.** It works in both local modes. In native mode it's a service in `deploy/local/docker-compose.yml` using its local compute backend (tasks run as Docker containers on the host). In helm mode it runs in minikube using its Kubernetes compute backend (tasks run as Kubernetes Jobs). TESK is Kubernetes-only, so it can't cover native mode.
  - **Pluggability:**
    - **Executors:** the refcloud TES talks to the executor over TES, so a new TES executor (e.g. TESK) is a new connection in config, with no new code.
    - **Engines:** each engine needs an adapter, a small `WorkflowEngine` interface (submit, status, cancel, logs, outputs) with one implementation per engine. Nextflow is the first.
  - **Attributing tasks to runs:** the WES glue issues a credential scoped to one run and configures that run's Nextflow process to call the refcloud TES with it. TES identifies the run, and so the user, from the credential, without relying on task tags.
- **D13. Storage: a shared local directory for the first platform.** In native mode this is a host directory. In helm mode it's a `hostPath` volume in minikube. Everything runs on the developer's machine at no cost. The engine's work directory, task inputs and outputs, and workflow outputs all live there (D18). S3-compatible object storage (e.g. MinIO) comes later, once the fully local setup works.
- **D14. Compute platform types and naming.** A compute platform type is a stack the refcloud supports. It's named after its layers from the top down: engine + executor (infrastructure). The docs list the supported types so people know which infrastructure the refcloud supports. Storage is not part of the name; it's a setting on the connection (D9).

  | Display name | Config key | Storage | Used by |
  |---|---|---|---|
  | **Nextflow + Funnel (Docker)** | `nextflow-funnel-docker` | Shared filesystem | Native mode; any single machine with Docker |
  | **Nextflow + Funnel (Kubernetes)** | `nextflow-funnel-kubernetes` | Shared filesystem (`hostPath` in minikube, a shared volume on real clusters) | Helm mode; any Kubernetes cluster |

  - "Local" is deliberately left out of names. It could mean the developer's machine, Funnel's `local` backend, or single-node storage. The Kubernetes type also runs on real clusters.
  - Later types follow the same pattern, e.g. `nextflow-tesk-kubernetes`, `cromwell-funnel-docker`.
- **D15. Storage is a connection setting, not a platform type.** Each connection sets how files are staged: `shared-filesystem` first, `s3` (MinIO locally, S3-compatible stores in real deployments) later. Adding S3 doesn't create new platform types. The storage setting exists from the start, even while `shared-filesystem` is the only value, so adding S3 doesn't need a refactor.
- **D16. Implementation order.**
  1. Nextflow + Funnel (Docker), using a shared host directory (native mode).
  2. Nextflow + Funnel (Kubernetes), using a `hostPath` volume (helm mode).
  3. MinIO/S3 file staging as a storage setting for both platform types.
  4. Longer term: Cromwell + Funnel (Docker) and beyond. Stages 1–3 cover most of what the refcloud needs.
- **D17. WES stages DRS inputs at submission.**
  1. WES scans `workflow_params` for `drs://` URIs.
  2. It checks the user's access to each one, using the same visa check as the DRS endpoints.
  3. It resolves each to an access URL and downloads it into the run's input directory on the shared filesystem.
  4. It rewrites the parameters to local paths, then launches the engine.
  - Access is checked with the user's identity while they're present, before their token can expire (D3). Neither the engine nor the executor ever handles user credentials or `drs://` URIs, so the approach works for any engine.
  - Scope: only the refcloud's own DRS objects whose bytes are in S3 (`aws_s3_access_object`). A submission referencing anything else (another DRS server, a manifest) is rejected with a clear message.
  - Cost: inputs are copied before the run starts. Stage 3 (S3 storage) could pass signed URLs instead.
- **D18. Workflow outputs stay with the run; no DRS write-back for now.**
  - Researchers get their outputs through WES. Registering outputs as DRS objects is out of scope: researcher outputs have no dataset, and researchers can't create datasets. So there is no `local_file_access_object` for now; inputs are S3-only and nothing is served from local files through DRS.
  - Local storage lives in the repo, under a git-ignored directory per platform type: `compute/<platform-type>/`, e.g. `compute/nextflow-funnel-docker/`. The directory is the default for the local setup only; deployers set the path in the connection config (D9). The root `CLAUDE.md` repository layout gets a `compute/` entry.
  - Layout per run:

    ```
    compute/nextflow-funnel-docker/runs/<run_id>/
      inputs/    staged DRS inputs (deleted when the run ends)
      work/      engine intermediates (deleted when the run ends)
      outputs/   workflow outputs (kept)
    ```

  - When the run ends (complete, failed or cancelled), `inputs/` and `work/` are deleted and `outputs/` is kept.
- **D19. Output download endpoint (stage 1, non-standard).** The WES spec has no way to download files (`RunLog.outputs` is free-form), so the refcloud adds a clearly marked extension, the same pattern as health in service-info (D10). It's a stop-gap until DRS write-back.
  - `GET /ga4gh/wes/{platformId}/v1/runs/{run_id}/outputs/{path}` streams a file from the run's `outputs/` directory.
  - `RunLog.outputs` lists each output file with its relative path, size and download URL.
  - Only the user who submitted the run can download. This is the same check WES already applies to `GET /runs/{run_id}`, so there's no new authorization model.
  - Paths are resolved strictly inside the run's `outputs/` directory, so a `{path}` can't reach other files (no `..`, no following symlinks out of it).
  - The download endpoint follows the platform's health like every other WES request (D10).
  - When DRS write-back arrives, the listed outputs can also carry `drs://` URIs.
- **D20. Workflow sources: pinned git, TRS, and bundled demos only.** Arbitrary workflows are not supported. Each Compute Platform's allowlist (D9) contains entries of three kinds:
  - **Git:** a repository URL pinned to a tag or commit, plus the main script path, e.g. `https://github.com/<org>/<repo>/blob/<tag-or-sha>/main.nf`.
  - **TRS:** a tool on an external TRS server, pinned to a version, e.g. a Dockstore workflow. The TRS spec defines no `trs://` URI scheme (checked against TRS 2.0.2), so the submitted `workflow_url` is the version's TRS descriptor URL, e.g. `https://dockstore.org/api/ga4gh/trs/v2/tools/{id}/versions/{version_id}/NFL/descriptor`.
  - **Bundled demos:** workflows shipped in this repo, submitted as `refcloud:bundled/{name}`. This is the only exception to "no arbitrary workflows". Bundled demos are still allowlist entries, so admins choose whether a platform offers them, and the local setup enables them on the default platform.
  - Git and TRS entries are resolved to a full commit SHA when the admin adds them (tags only; branches are refused). Git entries are submitted as GitHub's URL for the main script at that commit, e.g. `https://github.com/nf-core/demo/blob/<sha>/main.nf`. Only GitHub-hosted repositories are supported for now.
  - A submission's `workflow_url` must match an allowlist entry exactly. Uploaded workflows (`workflow_attachment`) are rejected.
  - **Demo input:** existing 1000 Genomes DRS objects. The demo workflow checksums a BAM index (`.bai`), which is small. No new datasets are created.
- **D21. Engine settings are locked to the allowlist entry.** How an approved workflow runs is fixed by the admin, not the researcher. Each allowlist entry (D20) holds its own engine settings, e.g. Nextflow profiles (`-profile test`) and engine environment variables such as `NXF_SYNTAX_PARSER=v1`, which the spike showed older nf-core pipelines need on Nextflow 26. WES rejects `workflow_engine_parameters` so a submission can't change how a workflow runs. Researchers supply only `workflow_params`.
- **D22. Fix the nf-ga4gh `.command.env` gap upstream; document what works meanwhile.** nf-ga4gh 1.5.0 doesn't copy back `.command.env`, so Nextflow `env`/`eval` outputs fail on copy-based TES executors (Funnel, TESK). Current nf-core modules use `eval` for tool versions, so most current nf-core pipelines are affected. A four-line patch, verified in the spike (nf-core/demo 1.2.0 runs, and all 58 plugin tests pass), goes upstream to `nextflow-io/nf-ga4gh` as an issue + PR. We don't ship a fork. Until it's released, the docs publish a workflow compatibility table so people can see what works today:

  | Workflow | Works on nf-ga4gh 1.5.0 + Funnel 0.11.14 |
  |---|---|
  | Bundled `drs-checksum` demo | ✅ |
  | nf-core/demo 1.0.2 (`-profile test`, `NXF_SYNTAX_PARSER=v1`) | ✅ |
  | nf-core/demo 1.2.0 and other workflows with `env`/`eval` outputs | ❌ until the nf-ga4gh fix is released |

  Allowlists (D20) should only include workflows known to work.
- **D23. Fix nf-ga4gh's handling of TES outages upstream** (2026-11-12). nf-ga4gh aborts the whole workflow on the first failed task-status request (`checkIfRunning()` / `checkIfCompleted()` have no error handling). So any core-api restart longer than a few seconds fails every workflow in flight, even though core-api reattaches to the run (Task 7). A patch, verified end to end, retries transient errors (no response, or `5xx`) on the next poll, and fails only after a new `tes.pollErrorTimeout` (default 10 minutes). It goes upstream as an issue + PR, like D22; we don't ship a fork. With it, a run survived ~20 s of core-api downtime. Until it's released, restarting core-api fails active runs.

## Tabled

- **Implementing TRS in the refcloud.** Important, but out of scope for now. Workflows are sourced from external TRS servers, pinned git repositories and bundled demos (D20).

## Future Directions

- While a platform is unhealthy, serve the last-known state of runs and tasks (from the refcloud's own records) for read requests, instead of returning `503` for everything (D10).
- Stage 2 (helm) engine launch (proposed 2026-11-11, to be confirmed in stage 2): in Kubernetes, Nextflow runs in its own container as a **Kubernetes Job per WES run**, created by core-api through the Kubernetes API (needs RBAC to create Jobs in the namespace). The Job uses a pinned Nextflow image (the official `nextflow/nextflow` image is published; check its architectures, or build a small image that also pre-caches the pinned nf-ga4gh plugin so runs don't need internet access). It mounts the shared storage (a `hostPath` in minikube; a ReadWriteMany volume, or S3 from stage 3, on real clusters such as EKS) and gets the run-scoped TES token from a Kubernetes Secret. The alternative, running `nextflow` as a subprocess inside the core-api pod, is weaker: Nextflow's JVM would compete with core-api for the pod's memory, and restarting or scaling core-api would kill running workflows (D12, D16).
- Upstream: track the nf-ga4gh `.command.env` issue/PR (D22) and the transient-status-error retry issue/PR (D23). Once released, bump the pinned plugin version and widen the compatibility table. A second gap: nf-ga4gh doesn't return `.command.trace`, so Nextflow's per-task resource metrics are missing (a candidate for a follow-up PR).
- A second engine: Cromwell (WDL). Its server/REST style of integration would show the `WorkflowEngine` adapter is really pluggable, and WDL matters to much of the GA4GH community (D12).
- A second executor: TESK, for helm mode and real Kubernetes clusters (D12).
- S3-compatible object storage (MinIO locally, S3 or other object stores in real deployments) as an alternative to the shared directory, giving outputs standard DRS access URLs (D13).
- Writing workflow outputs back to DRS. This needs an ownership or access model for researcher-owned objects (owned by the user, or gated by a visa so they can be shared), plus a way to serve the bytes: a `local_file_access_object` for shared-filesystem storage, or `aws_s3_access_object` once S3 storage lands (D18).
- Resolving `drs://` inputs from other DRS servers, and manifest DRS objects as inputs (D17).
- Retention policies for kept outputs, and a storage cap on connections, so a laptop's disk doesn't fill up (D18).
- A researcher UI page listing the user's runs, their states and output downloads. Stage 1 exposes these through WES only (D19).
- Cloud deployment on AWS: whether a hosted refcloud offers compute, and which connection limits (D11) keep its costs bounded. Tabled 2026-11-11 until the fully local setup (D16 stages 1–3) works.

## To Verify (spike)

Stage 1 items were checked on 2026-11-11. Details are in the spec's **Decisions (Task 1)**.

1. ✅ nf-ga4gh can authenticate to TES with a credential supplied for each run. `tes.oauthToken` read from an environment variable becomes `Authorization: Bearer` on every create, poll and cancel request (D12).
2. ✅ **Native mode:** Funnel in Docker Compose with the Docker socket and a same-path mount of the storage root works. It needs a derived image that adds the `docker` CLI.
3. ⏳ **Stage 2 (helm mode):** Funnel's Kubernetes backend in minikube, with the shared `hostPath` directory.
4. ✅ Nextflow 26.10.5, nf-ga4gh 1.5.0 and Funnel 0.11.14 run on arm64. amd64-only task images run under emulation.
5. ✅ TRS: resolve to the source git repository and commit. Dockstore's TRS descriptor files are incomplete for Nextflow workflows (D20).
6. ✅ File staging: Funnel copies plain host paths under `LocalStorage.AllowedDirs`. Task images need `bash`. Tasks need a writable `/tmp` because Funnel runs them `--read-only`. **Gap:** nf-ga4gh 1.5.0 doesn't return `.command.env`, so Nextflow `env`/`eval` outputs (used by current nf-core modules) fail (D12, D20).
7. ⏳ **Stage 2 (helm mode):** with minikube's Docker driver, a `hostPath` is a directory inside the minikube node, not on the macOS host. Decide whether developers reach outputs through `minikube mount` or only through the API.

## Open Questions

_None._

## Next Step

Stage 1 (D16) is specified in [`claude/specs/active/0008-add-compute-wes-tes.md`](../specs/active/0008-add-compute-wes-tes.md).
