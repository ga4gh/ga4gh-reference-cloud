# Feature Spec: Add compute stage 3 to the refcloud: S3 storage

**Branch:** `ja-iss-62-minio-s3`

**Linked GitHub Issue:** [Issue 62](https://github.com/ga4gh/ga4gh-reference-cloud/issues/62)

## Objective

Add `s3` as a connection storage setting (D15), so compute can run on a cluster with no shared filesystem. This is stage 3 of the plan in [`claude/discussions/0000-about-adding-compute.md`](../../discussions/0000-about-adding-compute.md) (D16). It builds on stage 1 ([spec 0008](../archive/0008-add-compute-wes-tes.md)) and stage 2 ([spec 0011](../archive/0011-add-compute-nextflow-funnel-kubernetes.md)). The AWS compute platform comes next ([milestones](../../../docs/milestones/2026-12-18-Compute-Milestones.md), item 3): Funnel's Kubernetes backend on EKS with an S3 bucket.

Locally, helm mode runs an S3-compatible server in minikube in place of S3.

Researchers see no difference: the same WES and TES APIs, visas, allowlists, limits, DRS inputs and output downloads. Behind them, everything a run reads and writes lives under the run's own prefix in a bucket:
* WES stages DRS inputs into the bucket and rewrites the parameters to `s3://` URIs;
* Nextflow's work directory and output directory are `s3://` locations, and the Nextflow Job keeps nothing on shared storage;
* Funnel's workers stage each task's inputs and outputs through S3;
* WES lists and streams outputs from the bucket, through the same download endpoint (D19).

**Done when:** on a fresh helm-mode setup (`./scripts/refcloud-setup.sh`, then `./scripts/refcloud-run-helm.sh`), a researcher with approved visas for `nextflow-demo-kubernetes` and the 1000 Genomes dataset can:

1. submit the bundled demo workflow through WES, with a 1000 Genomes `.bai` DRS object as input;
2. watch it run with its inputs, work directory and outputs in the local bucket, with no shared filesystem: no pod in the compute namespace mounts a `hostPath`;
3. download its report through the output download endpoint.

Also:
* `./scripts/refcloud-compute-demo.sh` passes;
* a core-api restart during a run reattaches to it;
* a run's engine can't read another run's files (Task 3);
* native mode works as before.

## Context

**Source of truth:** the discussion doc (decisions D1–D23) and the Decisions sections of specs 0008 and 0011. This spec relies most on:
* D13, D15: storage as a connection setting;
* D17: DRS inputs staged by WES;
* D18: the run layout;
* D19: output downloads.

**Request path,** unchanged from stage 2 (D3):

```
researcher → refcloud WES (core-api) → Nextflow (Kubernetes Job, compute namespace)
           → refcloud TES (core-api) → Funnel (Kubernetes backend) → task Jobs (compute namespace)
```

Today every layer shares one directory. With S3, every layer reads and writes the run's prefix in the bucket instead.

**What depends on a shared filesystem today:**
* WES preparation writes the workflow and the staged DRS inputs into the run directory (`RunPreparationService`);
* the engines write `refcloud.config` and `params.json` there. The launcher writes Nextflow's logs and its exit code there, and core-api reads them to follow a run across restarts;
* Nextflow's `-work-dir` and `--outdir` are paths in it;
* TES accepts task inputs and outputs only inside it (`TesTaskRules`);
* WES lists and streams outputs and logs from it, and clean-up deletes its intermediate directories;
* in helm mode, core-api, the Nextflow Job, and Funnel's workers and executors all mount the `hostPath`. This mount is also why the compute namespace can't enforce Pod Security (spec 0011, Task 1).

**Found while drafting (2026-11-18):**
* **MinIO's community edition is no longer an option.**
  * Official images stopped in October 2025.
  * The repository went into maintenance mode in December 2025 and has since been archived.

  So "MinIO locally" (D15, the milestones doc) becomes "an S3-compatible server locally", chosen in Task 1. The candidates:
  * SeaweedFS and RustFS, both Apache 2.0, and both documenting STS `AssumeRole` with session policies;
  * Garage (AGPL), where I found no STS;
  * the community fork of MinIO, `pgsty/minio` (AGPL).
* **Funnel's Kubernetes backend runs each task's worker and executor as separate pods,** which share the task's files through a volume.
  * Stage 2 used the shared `hostPath` for this.
  * Funnel's default is a per-task volume over the bucket, through AWS's Mountpoint for S3 CSI driver.

  This is the biggest unknown (Task 1).
* **core-api already uses the AWS SDK for Java v2** (`software.amazon.awssdk:s3`, version 2.25.15) for DRS's S3 access objects.

**Storage layout:** each run keeps D18's layout, under the connection's bucket and optional prefix:

```
s3://<bucket>/<prefix>runs/<run_id>/
  inputs/     staged DRS inputs (deleted when the run ends)
  work/       Nextflow's work directory (deleted when the run ends)
  outputs/    workflow outputs (kept)
  engine/     workflow.tar.gz, the workflow (deleted when the run ends); refcloud.config, params.json,
              nextflow.log, stdout, stderr, the exit code (kept)
```

(Task 2: the workflow goes up as one archive instead of a `workflow/` folder.)

**Decided before Task 1 (2026-11-18):**
1. **S3 for the Kubernetes type only.**
   * Production needs only `nextflow-funnel-kubernetes` on S3.
   * S3 for `nextflow-funnel-docker` would add a second S3 server, in Docker Compose. It would also need Funnel's S3 config there, and testing in both modes, for a combination nothing needs by December.
   * D15 still holds: storage is a connection setting. The Docker type refuses `s3` for now (Future Directions).
2. **Helm mode's local connection switches to S3,** so helm mode rehearses production. There's no second connection or demo platform.
   * The shared-filesystem code stays: native mode uses it, and it's the fallback (see below).
   * Whether the chart keeps a switch back to `hostPath` is settled after Task 1.
3. **Each run's engine gets credentials limited to that run's prefix.**
   * In stage 2, the Nextflow Job mounts only its own run directory, so a workflow can't see other runs.
   * With one bucket, credentials for the whole bucket would let any allowlisted workflow's Nextflow code read other users' runs. For example, a samplesheet's contents are read by Nextflow itself, not in a task, so the refcloud TES never sees those reads.
   * Per-run credentials (STS `AssumeRole` with a session policy) bring back stage 2's isolation.
   * Cost: the local server must support STS, and runs longer than an hour on AWS need their credentials refreshed (Task 1).
   * WES still refuses `s3://` URIs in parameters, and TES still keeps tasks inside the run's prefix (Task 2).
4. **core-api streams output downloads, as today.**
   * The local server isn't reachable from outside the cluster, so redirecting to a presigned URL would mean exposing it through the ingress.
   * Streaming keeps D19's endpoint, auth and behavior.
   * Presigned redirects for large outputs on AWS are a Future Direction.

**Fallback:** suppose Task 1 shows S3 can't work in both minikube and EKS on this schedule. Production can instead run stage 2's shared-filesystem design on a ReadWriteMany volume (EFS on EKS), with a claim in place of the `hostPath`. Then the AWS platform needs no stage 3. Task 1 ends with this go/no-go.

**Production:** stage 3 is what the AWS compute platform builds on. Anything production needs goes into the rollout spec ([spec 0010](../active/0010-production-rollout.md)): the connection's S3 settings, the IAM roles and the SDK changes. The bucket and the roles themselves go into the AWS platform spec. The local S3 server is never deployed to production.

## Engineering Tasks

### Task 1: Spike: verify S3 storage end to end

Run this in minikube (with Calico), using throwaway resources, before designing the rest. Record what's found under Decisions, and add the results to the discussion doc's To Verify list.

* **The local S3 server.** Pick one that:
  * is maintained, with an official multi-architecture image pinned by digest;
  * runs non-root on a read-only root filesystem, as one small process. Minikube has about 2.7 GiB to spare at rest (spec 0011, Task 7);
  * has the S3 features we use: path-style requests, multipart upload, `ListObjectsV2`, and STS `AssumeRole` with a session policy (decision 3).

  Apache 2.0 is preferred for code others deploy. The candidates are listed under Context.
* **Nextflow on S3, offline:**
  * get the nf-amazon plugin into our image at a pinned version, so runs stay offline. Check first whether Nextflow's `dist` build already includes it;
  * point it at the local server (`aws.client.endpoint`, path-style access), with credentials from the environment;
  * run `drs-checksum` through nf-ga4gh to Funnel, with an `s3://` work directory and output directory.

  Check what nf-ga4gh sends as each task's inputs and outputs (`s3://` URLs?), and whether the `.command.*` files and the exit code round-trip.
* **Funnel's S3 storage:**
  * in its Kubernetes backend, `GenericS3` with the local server's endpoint;
  * for EKS, from Funnel's docs, `AmazonS3` with the default credential chain.

  Check whether stage 2's placeholder S3 setting goes away.
* **How a task's worker and executor share its files** without a shared filesystem. The options:
  * **Funnel's default:** a per-task volume over the bucket, through the Mountpoint for S3 CSI driver (an EKS add-on). It works across nodes. But Mountpoint doesn't support every file operation (no appends or renames), and it may not run in minikube against a non-AWS server;
  * **a per-task claim** from a storage class (minikube's `standard`, EBS on EKS), with the executor scheduled on the worker's node (ReadWriteOnce);
  * **node-local scratch** (`hostPath`), with the executor on the worker's node. The simplest, but it keeps the `hostPath`, so Pod Security stays at `warn`.

  Prefer one mechanism that works in both minikube and EKS, and that lets the compute namespace enforce `restricted`.
* **The Nextflow Job without shared storage:** how the workflow, `refcloud.config` and `params.json` get in, and how the logs and the exit code get out. Proposed:
  * core-api uploads them under the run's `workflow/` and `engine/`;
  * the Job copies them into an `emptyDir` before Nextflow starts, and uploads the logs and the exit code when Nextflow exits, with Nextflow's own `nextflow fs` commands or a small S3 client in the image;
  * a pod that dies before uploading is Lost, as in stage 2.

  Check whether researchers can still see logs while a run is going.
* **Per-run credentials** (decision 3):
  * on the chosen server: `AssumeRole` with a session policy limited to `runs/<id>/`. Check that another run's prefix is refused;
  * how Nextflow's AWS SDK picks the credentials up (the environment, or `credential_process` for a refresh);
  * AWS caps a session at 1 hour when one role assumes another, and core-api's own credentials are already a role session. So runs longer than an hour need a refresh: find one that adds no new network paths.
* **Network:** list who connects to the server: core-api, the Nextflow Jobs, Funnel's workers, and Funnel's server if it does. Executors shouldn't.
* **Minikube's memory** with the server added. Resize the local connection if needed.
* **Go/no-go:** if S3 can't work in minikube on this schedule, or EKS's docs rule out what works in minikube, stop and choose between:
  * narrowing it, e.g. S3 for data, with a ReadWriteMany volume for the Nextflow Job's files;
  * the fallback (Context).

**Decisions (Task 1):**

Agreed before starting (2026-11-18):
* **Minikube first; EKS is checked against its docs only.** This covers how worker and executor pods share files across nodes, Pod Identity, and the limit on credentials issued from another role. The fully local setup comes first. Real EKS testing belongs to the AWS platform spec, once its Terraform exists, and the fallback stays open until then.
* **Production runs are capped at about 45 minutes at launch.** That fits each run, its cancel grace period and its final upload inside AWS's one-hour limit, so credentials are issued once, at launch, with no refresh. It also bounds cost. Local runs keep their 1-hour cap, since the local server allows longer credentials. Refreshing credentials, for longer runs, is a Future Direction.
* **Logs stay live:** the Nextflow Job uploads them every 15–30 seconds while the run is going, not only at the end, since the My Runs page will show them. That likely means a small S3 tool in the image (e.g. `s5cmd` or `rclone`), since running `nextflow fs` that often would start a second JVM inside the Job's 1 GB.
* **The local S3 server:** try SeaweedFS and RustFS, and record why one won.

**The spike was run on 2026-11-18** in minikube (with Calico):
* a throwaway namespace (`spike-s3`) labelled `enforce: restricted`, deleted afterwards. Scratch files are left in the session's scratchpad;
* the workflow ran with nf-ga4gh pointed straight at Funnel, as in stage 2's spike. The refcloud's WES and TES were proven in stage 1.

* **Result: the stack works on S3 end to end, with no shared filesystem, in about 18 seconds.**
  1. A stand-in for core-api uploads the run's files as one archive and a 200 KB input, and issues credentials limited to `runs/r3/`.
  2. The Nextflow Job (no service account token, no `hostPath`) fetches the archive and runs the bundled `drs-checksum` with an `s3://` work directory and output directory.
  3. nf-ga4gh sends the task to Funnel. Funnel's worker downloads the input from S3 onto a per-task claim, the executor runs, and the worker uploads the outputs.
  4. The report lands in `runs/r3/outputs/` with the right MD5. The logs and exit code 0 land in `runs/r3/engine/`.

  Every pod ran non-root under `enforce: restricted`.
* **The local S3 server: RustFS 1.0.6** (`rustfs/rustfs`, Apache 2.0, multi-architecture). Both candidates passed the credential tests below. RustFS won because:
  * it's one single-purpose process (about 35 MiB at rest), and its image already runs as user 10001;
  * its image includes its CLI (`rc`), which can create the bucket, users and policies from a Job at deploy time;
  * `AssumeRole` works for any user with a policy, as with MinIO.

  SeaweedFS 4.54 (`weed mini`, about 70 MiB) runs a master, volume server, filer, S3 gateway, admin server and workers in one process. Its STS needs roles declared with trust policies in a config file, and its own `AssumeRole` test still tolerates failure.
* **Per-run credentials work on RustFS:**
  * a dedicated user (`engine-issuer`, allowed only `runs/*`) calls `AssumeRole` with a session policy limited to `runs/<id>/`. A 2-hour lifetime was honoured (the server allows 15 minutes to 12 hours);
  * the credentials read, wrote and listed their own prefix, and were refused everything else: another run's prefix (read, write, list, delete), the bucket root, `runs/`, and creating buckets;
  * the session policy narrows the user's own policy. A session policy allowing everything still couldn't read outside `runs/*`;
  * temporary credentials can't call `AssumeRole` again;
  * Nextflow (nf-amazon) and `curl` both used them, with the session token.
* **Funnel: upgrade to 0.12.3** (released 2026-11-09). It has moved to [`calypr/funnel`](https://github.com/calypr/funnel); images are still at `quay.io/ohsu-comp-bio/funnel`, and the old repository only redirects. 0.12.3:
  * provisions a **per-task workspace claim from a StorageClass** (`Kubernetes.Storage.volumeClaimTemplates`) and mounts it into both the worker and the executor. The worker Job owns the claim, so Kubernetes deletes it with the Job. Funnel makes no PersistentVolume calls at all, so **the cluster-wide PersistentVolume permission goes away** (spec 0011, Future Directions), along with stage 2's placeholder S3 setting and its PersistentVolume and claim templates;
  * reads S3 through its `AmazonS3` backend, with a custom `Endpoint` and the region, and credentials from the worker's environment (the AWS SDK's default chain). The same config works for AWS without the endpoint, and no keys sit in Funnel's config file;
  * no longer tries to delete ServiceAccounts it didn't create (stage 2 logged two errors per task); the spike's logs had no errors;
  * checks each task against `Kubernetes.Resources.Limits` (set from the connection's per-task maximums);
  * adds a control tag, `_IMAGE_PULL_POLICY`, which core-api already refuses (all `_` tags, spec 0011 Task 4);
  * lists security fixes in its release notes (access control, file access, credential handling).

  **One exception:** the worker's root filesystem must be writable. 0.12.3 stages outputs in a temporary directory beside the task's workspace (the parent of `WorkDir/<task>`), and Funnel refuses any mount above the workspace. So the worker uses `WorkDir: /tmp` (world-writable in the image) with `readOnlyRootFilesystem: false`. This is restricted-compatible; Trivy needs an exception with that reason. The executor keeps a read-only root. It's an upstream candidate (Future Directions).
* **Worker and executor share files through the per-task claim:** ReadWriteOnce from minikube's `standard` StorageClass. The executor template has a required `podAffinity` to its own worker pod (by task ID), so on a multi-node cluster both land on one node. No pod in the namespace mounts a `hostPath`, so **the compute namespace can enforce `restricted`.**
* **The Nextflow Job without shared storage:**
  * the image adds nf-amazon 3.12.1 (Nextflow 26.10.5's own pin, `nextflow plugin install`; the `dist` build doesn't include it) and the distribution's `curl`. Runs stay offline;
  * core-api uploads the run's files as one archive (`engine/run.tar.gz`: workflow, `refcloud.config`, `params.json`). The Job fetches it with `curl --aws-sigv4` (with the session token) and extracts it into an `emptyDir`. Every transfer the Job makes itself is a single object, so it needs no S3 tool beyond `curl`;
  * the launcher (`s3-launcher.sh` in the spike) uploads `engine.stdout.log`, `engine.stderr.log` and `nextflow.log` every 15–20 seconds while Nextflow runs, and those plus `engine.exitcode` when it ends. The logs were in the bucket mid-run;
  * `refcloud.config` adds `aws.region` and `aws.client.endpoint` / `s3PathStyleAccess`. `-work-dir` and `--outdir` are the run's `s3://` prefixes.
* **Cancelling works:** deleting the Job sends the pod SIGTERM, and the launcher forwards it to Nextflow, which cancels its TES task (Funnel: `CANCELED`). The launcher then uploaded the final logs and exit code 1 within the default 30-second grace period, and Funnel deleted the worker and executor Jobs and the claim.
* **What nf-ga4gh sends:** every input and output is `s3://<bucket>/runs/<id>/...`: the task's `.command.sh` and `.command.run`, the staged input, and `.command.out`, `.command.err`, `.command.log`, `.exitcode` and the outputs. So TES's rule is a scheme, bucket and prefix check.
* **Directory markers:** Nextflow writes zero-byte keys ending in `/` (e.g. `runs/r3/outputs/`). The output listing skips them.
* **Network,** verified under default-deny NetworkPolicies:
  * Nextflow → the refcloud TES (Funnel directly in the spike) and the S3 server, both by IP, with no DNS;
  * Funnel's workers → Funnel's RPC port, the S3 server, the Kubernetes API, and DNS;
  * Funnel's server → the Kubernetes API and DNS. It doesn't use S3, so it gets no S3 credentials;
  * executors → nothing;
  * core-api → the S3 server, for S3 and STS (Task 2).
* **Memory:** RustFS peaks at about 37 MiB, and Funnel 0.12.3's server at about 21 MiB. The local connection's sizing doesn't change.
* **EKS, from the docs** (verified in the AWS platform spec):
  * per-task claims from EBS (gp3, ReadWriteOnce, `WaitForFirstConsumer`). EKS Auto Mode manages the EBS driver. The executor's `podAffinity` keeps it on its worker's node. Each task waits for a volume to be created and attached, which takes seconds to tens of seconds;
  * Funnel's workers get their S3 access through EKS Pod Identity on their ServiceAccount (Funnel's default credential chain), and core-api's role may call `AssumeRole` on a run role. Those sessions are chained, so they last at most an hour, hence the 45-minute cap;
  * on AWS the S3 endpoint is a DNS name, so the Nextflow Jobs need DNS and a route to S3 (a VPC gateway endpoint). Locally they use the server's IP and no DNS.
* **Go/no-go: go.** Nothing calls for the fallback.

**Decided after the spike (2026-11-18):**
* **Funnel 0.12.3 in both modes:** helm mode and native mode's Docker Compose Funnel, so one version is pinned everywhere and both modes get its security fixes. Native mode's config is checked against 0.12.3's retired settings, and native mode is tested end to end in Task 8 (Task 6).
* **Funnel's worker gets a writable root filesystem** (`WorkDir: /tmp`), with a Trivy exception that gives the reason. The executor keeps a read-only root. The upstream issue is under Future Directions.

### Task 2: S3 storage in core-api

* **The setting** (D15): `storage.type: s3`, with:
  * `bucket`, and an optional `prefix`;
  * `region`;
  * for S3-compatible servers, an optional `endpoint` and `path-style-access`;
  * optional credential environment variable names (`access-key-id-env`, `secret-access-key-env`). Without them, the AWS SDK's default credential chain applies (EKS Pod Identity or IRSA).

  Validated at startup. Per decision 1, a Docker-type connection with `s3` fails startup with a clear message. The admin CLI reads connections but not their storage, so it only needs to accept the new type.
* **Run storage behind one interface,** with shared-filesystem and S3 implementations, used everywhere the run directory is used today (Context). With S3:
  * WES uploads the workflow. It stages each DRS input by streaming the download into the bucket (multipart upload), then rewrites the parameters to `s3://` URIs;
  * TES accepts task inputs and outputs only under the run's prefix. `http(s)` inputs stay allowed, as today;
  * WES lists outputs with `ListObjectsV2` and streams them through the same endpoint, with the same owner check (D19). Logs work the same way;
  * clean-up deletes `workflow/`, `inputs/` and `work/` under the run's prefix, and keeps `outputs/` and `engine/`.
* **WES refuses `s3://` URIs in `workflow_params`.** Inputs are DRS only (D17), and only the refcloud may give a run's engine a storage location. This holds even with per-run credentials.
* **Health** (D10): the storage check writes, reads and deletes a probe object under the connection's prefix.
* **The AWS SDK:**
  * bump the existing `software.amazon.awssdk:s3` (2.25.15 is from 2024), through the SDK's BOM;
  * add `sts` if Task 3 needs it;
  * set the endpoint override and path-style access for S3-compatible servers;
  * update the lockfile and pass the dependency audit (`apps/core-api/CLAUDE.md`).
* **Tests:**
  * unit tests for the prefix rules, the parameter refusal, the layout and the listing;
  * the S3 calls against the local server, in a throwaway container (as with the throwaway Postgres).

**Decisions (Task 2):**

Agreed before starting (2026-11-18):
* **The workflow goes up as one archive:** preparation fetches the workflow into a temporary directory, uploads it as `engine/workflow.tar.gz`, and deletes the local copy. core-api keeps no run directory for S3 runs. At launch, Task 4 adds `refcloud.config` and `params.json` as two small objects. This replaces the layout's `workflow/` folder.
* **Column names stay:** `compute_run.run_directory` and `workflow_path` hold a path or an `s3://` location (e.g. `s3://<bucket>/<prefix>runs/<id>/` and `…/workflow/main.nf`), documented as such. There's no migration. The Kubernetes engine maps them to the Job's local paths.
* **S3 downloads return the whole file:** there's no `Range` support for now. Range support goes with presigned redirects (Future Directions). Downloads from the shared filesystem keep Spring's range handling.

Done 2026-11-18:
* **Settings** (`ComputeConfig.Storage`): `type: s3` with `bucket`, `prefix` (empty, or segments ending in `/`), `region`, `endpoint`, `path-style-access`, and `access-key-id-env` / `secret-access-key-env` (both or neither; neither means the AWS SDK's default chain). Each type takes only its own settings, and `nextflow-funnel-docker` refuses `s3` (decision 1). The admin CLI ignores storage settings, so it needed no change.
* **`compute/storage/`:**
  * `RunStorage`, one per connection (`RunStorages`), with `SharedFilesystemRunStorage` (stage 1–2's code, moved out of WES, preparation, the health check and the engines) and `S3RunStorage`;
  * `S3Location` and `RelativePaths` hold the location rules; `WorkflowArchive` writes the workflow as a gzipped ustar archive.
* **The workflow archive is written by core-api's own small tar writer:** regular files with their executable bit, and directories; not symlinks (as with bundled copies) and not `.git`. Paths longer than ustar allows (255 bytes) fail preparation with the path. This avoids a new dependency for one format; GNU and BSD `tar` both extract it (tested).
* **Inputs stream into S3** in 16 MiB parts (one PUT if smaller; multipart otherwise, aborted on failure). The size is checked against DRS's, and the object deleted if it's wrong.
* **Downloads:** S3 outputs and logs are a `Resource` that knows its size from a HEAD request and makes a new GET on each read. Spring still answers a `Range` request, by reading from the start and skipping: correct but not efficient. The efficient version goes with presigned redirects.
* **Who cleans up:** `ComputeRunService` removes a run's intermediate files through its storage when the run ends; engines remove only their own resources (a Job, a Secret). `NextflowKubernetesEngine` refuses S3 runs until Task 4.
* **TES** checks a task's locations by the run's: on S3, `s3://<bucket>/<run key>/…` with ordinary segments only (another run's prefix, the bucket root, other buckets, `..` and empty segments are refused). `ComputeCaller` and `RunPrincipal` carry the run's location as a string, since `Path` mangles a URI.
* **WES** refuses any `workflow_params` string starting with `s3://` (`DrsInputs.refuseStorageLocations`).
* **Health:** the S3 check writes, reads and deletes `<prefix>health/probe-<uuid>`, so core-api's credentials need `health/*` as well as `runs/*` (Task 5). A missing credential environment variable makes the connection unhealthy with its name, not a failed startup.
* **The AWS SDK:** 2.25.15 → 2.57.8 (October 30; the newest at least two weeks old), through its BOM. The SDK's default HTTP client is now Apache HttpClient 5, whose `httpcore5` (pinned by Spring Boot at 5.3.6) has two HIGH CVEs. core-api makes only synchronous calls, so `build.gradle` excludes the Apache and Netty clients and uses the JDK-based `url-connection-client`. The dependency audit is clean, and Apache HttpClient 4.5.13, which the old SDK brought, is gone too.
* **Verified:**
  * 305 unit tests (52 new), including an in-memory S3 fake that pages its listings;
  * static analysis and the dependency audit pass;
  * a throwaway scratch test (deleted after) ran against a RustFS 1.0.6 container through the real client: settings, the health probe, the workflow archive, a 40 MiB multipart input (0.5 seconds), a wrong-size input leaving nothing (and no open multipart upload), outputs with Nextflow's directory markers and a space in a name, logs, and clean-up. The SDK's default request checksums work with RustFS;
  * DRS's anonymous ranged read from the public 1000 Genomes bucket works on the new HTTP client.

### Task 3: Per-run storage credentials

* **core-api issues each run's engine temporary credentials** limited to the run's prefix:
  * through STS `AssumeRole` with a session policy: read, write, list and delete under `runs/<id>/` only;
  * once, at launch, lasting the run's maximum duration plus its cancel grace period and a margin for the final upload. There's no refresh (Task 1);
  * core-api refuses at startup an S3 connection whose maximum run duration doesn't fit inside the credentials' lifetime: on AWS, about 45 minutes, inside the one-hour limit.
* **They reach the engine like the TES token,** through the per-run Secret. They're never stored in the database or logged.
* **Funnel's workers keep credentials for the whole connection.** They run Funnel's code, not the workflow's, and executors get no credentials.
* **Connection settings:** on AWS, the role to assume. The local server takes STS requests at its S3 endpoint.
* **Verify:** a run's engine can't list or read another run's prefix, or the bucket's root.

**Decisions (Task 3):**

Done 2026-11-18 (no questions needed; the choices below follow decisions 3 and Task 1's):
* **Connection settings:**
  * `storage.run-role-arn`, required for `s3`. On AWS it's the role core-api's own role assumes for each run. RustFS issues credentials for its caller (core-api's own user) and ignores the ARN, so locally any well-formed ARN does (e.g. `arn:aws:iam::000000000000:role/refcloud-compute-run`);
  * `storage.run-credentials-max-seconds`: the longest the server issues credentials for. It defaults to 3600 (AWS's limit when core-api's own credentials are already a role session); RustFS allows up to 43200.
* **How long a run's credentials last:** the run's engine lifetime, `ComputeRunConfig.engineLifetime`: its max duration, plus the cancel grace period (2 minutes), plus the 5-minute margin that the Kubernetes Job's deadline already adds. So the credentials outlive anything the Job can do, including its final upload. The Job's deadline now uses the same method. STS's 15-minute minimum is respected.
* **Startup refuses a connection whose runs could outlive their credentials,** naming the longest max-run-duration-seconds that fits. With the AWS default that's 3180 seconds (53 minutes), so the planned 45 minutes fits. It's a `@PostConstruct` check in `RunStorages`, so a misconfigured connection fails startup with the reason.
* **The session policy** allows the run's objects (get, put, delete, multipart) and listing under its prefix (with `s3:prefix` matched on the run's key, `key/` and `key/*`), plus `GetBucketLocation`; nothing else. What the engine gets is what both the role (on RustFS, core-api's user) and this policy allow. The session is named `refcloud-run-<run id>`, so the server's or CloudTrail's records show which run did what. ARNs use the `aws` partition (not GovCloud or China).
* **Issued at launch:** `ComputeRunService` issues the credentials (through the run's storage) just before launching the engine. A refusal ends the run `SYSTEM_ERROR` with "could not issue the run's storage credentials: …". Shared-filesystem storage issues none.
* **Delivery:** `WorkflowEngine.launch` takes them alongside the TES token. The Kubernetes engine puts them in the run's existing Secret as `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and `AWS_SESSION_TOKEN` (wiring them into the Job is Task 4). `RunCredentials.toString` prints only the key ID's first characters and the expiry. `AWS_` is reserved for the Kubernetes engine, so an allowlist entry can't override the credentials or point the SDK at another endpoint.
* **Health:** the S3 check also issues a 15-minute credential for a probe "run" under `health/`, so a run role core-api may not assume (or a server without STS) shows as an unhealthy connection, not as failed launches. On AWS that's one `AssumeRole` per check interval (60 seconds), which shows up in CloudTrail.
* **The AWS SDK's `sts` module** comes from the same BOM, with the same HTTP client exclusions. The dependency audit is clean.
* **Verified:**
  * 313 unit tests (an STS fake records each request; the session policy is checked exactly);
  * static analysis, the dependency audit;
  * a throwaway scratch test (deleted after) against a RustFS 1.0.6 container, set up as Task 5 will be: a `core-api` user allowed only `runs/*` and `health/*`, with credentials issued through it. The health check (S3 and STS probes) passed. A run's credentials lasted the 67 minutes requested, and read, wrote, listed, copied (as `publishDir` does) and deleted within their run. They were refused on another run (including `r10` next to `r1`), `runs/`, the bucket root, `health/`, copying another run's output into their own, and `AssumeRole` with themselves.

### Task 4: The Kubernetes engine on S3

* **The Nextflow image** adds nf-amazon at a pinned version. The wrapper checks it like the other pins.
* **For S3 connections:**
  * `refcloud.config` adds the S3 client settings (the endpoint and path-style access);
  * `-work-dir` and `--outdir` become `s3://` locations under the run's prefix.
* **The Job:**
  * no `hostPath`;
  * an `emptyDir` for the engine's files, filled before Nextflow starts and uploaded when it ends (Task 1's mechanism);
  * credentials from the per-run Secret.
* **Status:** the exit-code object first, then the Job and its pod, as in stage 2. Logs are read from the bucket.
* **Reattaching after a core-api restart, cancelling and cleaning up** work as in stage 2.
* Unit tests.

**Decisions (Task 4):**

Agreed before starting (2026-11-18):
* **The S3 launcher lives in the Nextflow image** (`compute/images/nextflow/`), next to the `refcloud-nextflow` version check. It needs the image's `curl` and `tar`, and the image is built in lockstep with core-api. Stage 2's shared-filesystem Job keeps core-api's `run-launcher.sh`.
* **nf-amazon is the version Nextflow itself pins,** baked into the image (3.12.1 for 26.10.5), and the image build fails if it differs from Nextflow's `plugins-info.txt`. There's no connection setting for it.
* **Logs are uploaded every 15 seconds** while Nextflow runs.
* **The Kubernetes engine reads and writes its run files through `RunStorage`** on both storage types, so stage 2's `hostPath` mode uses the same code.
* **Verification before Tasks 5–6 exist:** the real image and the Job core-api generates run in a throwaway namespace, with a throwaway RustFS and Funnel 0.12.3, as in the spike. The end-to-end through WES is Task 8.

Done 2026-11-18:
* **The image** (`compute/images/nextflow/`):
  * adds nf-amazon 3.12.1, and the build fails unless that's the version in Nextflow's own `plugins-info.txt` (checked: a build with 3.11.0 fails, naming the pin);
  * adds `curl`, and `refcloud-run-s3`, the S3 launcher. It:
    * fetches `engine/workflow.tar.gz` (extracted into `workflow/`), `engine/refcloud.config` and `engine/params.json` into the Job's own `/refcloud/run`, and ends with exit code 126 if it can't, giving the reason in `engine.stderr.log`;
    * runs the engine, forwarding SIGTERM and SIGINT as stage 2's launcher does;
    * uploads the three logs every 15 seconds (`REFCLOUD_LOG_SYNC_SECONDS`). Its uploader stops at a marker file, so an upload is never cut off mid-way;
    * when the engine exits, uploads the logs, then `engine/engine.exitcode`.

    curl signs every request itself (`--aws-sigv4`, with the session token). ShellCheck is clean.
  * nf-amazon needed no entry in `NXF_PLUGINS_DEFAULT`: Nextflow loads it offline for `s3://` paths. So the version-check wrapper is unchanged.
* **The engine** (`NextflowKubernetesEngine`) on S3:
  * writes `refcloud.config` (with `aws.region`, plus the endpoint and path-style access when the connection sets an endpoint) and `params.json` through the run's storage;
  * the Job has an `emptyDir` for `/refcloud/run` instead of the `hostPath`, runs `refcloud-run-s3`, and sets `-work-dir` and `--outdir` to the run's `s3://` prefixes. Its environment adds `REFCLOUD_RUN_URL`, `AWS_REGION` and `AWS_EC2_METADATA_DISABLED=true` (the SDK never looks for instance credentials), and the three credential variables come from the run's Secret;
  * an endpoint is reached by its address (resolved by core-api, as for the TES callback), which means path-style URLs. Without an endpoint (AWS), the run URL is the bucket's own (`https://<bucket>.s3.<region>.amazonaws.com/…`), which needs DNS: for the AWS spec. S3 prefixes are now limited to URL-safe characters (letters, digits, `_`, `.`, `-`);
  * status and the lost-contact check read `engine.exitcode` and the end of `nextflow.log` (a suffix-range GET) through `RunStorage`, on both storage types. A failure reading storage is treated like a failure reaching the Kubernetes API: the run stays alive and is checked again.
* **`NextflowEngine.RunFiles`** gives the command its local directory, workflow, work directory and outputs, so one command builder serves both storage types.
* **`RunStorage`** gains `writeEngineFile`, `readEngineFile` and `readEngineFileTail` (in the run directory on a shared filesystem, under `engine/` on S3).
* **Verified:**
  * 317 unit tests; static analysis; the Dockerfile checks (hadolint wanted no pipe in the pin check, so it writes the file and greps it);
  * a scratch test (deleted after) in a throwaway namespace enforcing `restricted`, with RustFS, Funnel 0.12.3, a `core-api` RustFS user, and default-deny NetworkPolicies (the Job allowed only RustFS and Funnel, with no DNS). It prepared runs with the real storage code, issued scoped credentials, and applied the Secret and Job built by the engine's own code, using the image built from the repo:
    * `drs-checksum` on a 200 KB input exited 0, and the logs were readable from the bucket while it ran. The report's MD5 matched, and clean-up left only `engine/` and `outputs/`;
    * a cancel (deleting the Job) made Nextflow cancel its TES task and upload exit code 1 with the final logs;
    * a run whose workflow archive was missing ended with 126, with curl's 404 and the launcher's reason in its log.

### Task 5: The local S3 server in the chart

* **Local only** (`cloudEnvironment: local`): the server chosen in Task 1, with:
  * its image pinned by digest;
  * a non-root user and a read-only root filesystem;
  * a PersistentVolumeClaim for its data (not a `hostPath`);
  * probes, and resource requests and limits.
* **Its namespace,** the compute namespace or the release's, is settled with Task 1's network list.
* **The bucket and accounts** are created idempotently when deploying (by a Helm hook Job or the run script): accounts for core-api and Funnel, and one that issues STS credentials if Task 3 needs it.
* **Credentials** go in Secrets, generated per installation if practical, so the repo holds no defaults. Otherwise, dev-only defaults are allowlisted in `.gitleaks.toml` with a reason.
* **NetworkPolicies:**
  * the server accepts only the clients Task 1 lists;
  * the compute namespace's egress rules gain it for the Nextflow Jobs and Funnel's workers;
  * executors still get nothing.

  Update the policy tables in the templates and `deploy/helm/README.md`.
* **Production** uses S3 on AWS instead (the AWS platform spec).

**Decisions (Task 5):**

Agreed before starting (2026-11-18):
* **RustFS lives in the release namespace, and run outputs survive `--down`,** as stage 2's `hostPath` files do (the shared database still lists those runs), and as S3 outlives a release in production. Its data claim is kept when the release is uninstalled (Helm's `resource-policy: keep`), and only `--reset` deletes it. The compute pods reach it across namespaces, as they reach core-api.
* **`refcloud-setup.sh` makes the credentials,** like the chart's other Secrets: random values (from `/dev/urandom`, so nothing in the repo and nothing to allowlist), created only when missing, so re-running setup keeps them in step with RustFS's kept data. `--reset` deletes them together with that data, and setup then makes new ones. There are three Secrets in the release namespace: RustFS's root account (used only by the setup Job), core-api's user and Funnel's user.
* **Funnel's Secret is copied into the compute namespace by the chart** (Task 6), by looking it up at deploy time, since the compute namespace doesn't exist when setup runs. A deploy that can't find it fails, saying to run setup.
* **RustFS 1.0.6,** pinned by digest. It's newer than two weeks, but it's the version every check ran against, and it's local only.
* **A Helm post-install/post-upgrade hook Job** (the `rc` CLI in RustFS's own image) creates the bucket and the `core-api` (`runs/*`, `health/*`) and `funnel` (`runs/*`) users with their policies. It's safe to re-run.

Done 2026-11-18:
* **Chart** (`templates/apps/rustfs/`, local only, in the release namespace):
  * a Deployment (`Recreate`), user 10001 with fsGroup, read-only root, console off, logs to stdout (`RUSTFS_OBS_LOG_DIRECTORY` empty), probes on `/health/live` and `/health/ready`, requests 50m/128Mi and limits 1 CPU/512Mi;
  * a ClusterIP Service `rustfs` on 9000;
  * `rustfs-data`, a claim from the default StorageClass, `ga4gh.referenceCloud.compute.localObjectStorage.size` (10Gi), kept on uninstall;
  * `rustfs-setup`, a ConfigMap (the script and the two policies) and a post-install/post-upgrade hook Job. It waits for RustFS to be ready, then creates the bucket and the users and policies with `rc`. Every step is safe to repeat (checked: re-adding a user, re-creating a policy, which replaces it, and re-attaching all succeed). The image and bucket name are in `_helpers.tpl` (`refcloud.rustfsImage`, `refcloud.localComputeBucket`).
* **core-api** gets `REFCLOUD_COMPUTE_S3_ACCESS_KEY_ID` / `_SECRET_ACCESS_KEY` from `refcloud-compute-s3-core-api` (local only). The connection names them in Task 7.
* **NetworkPolicies:** `rustfs` (release namespace) accepts 9000 from core-api, `rustfs-setup`, and the compute namespace's `refcloud-nextflow` and `funnel-worker`. Both of those gain egress to it (`refcloud.computeEgressToRustfs`). The tables in both templates and the README say so.
* **Scripts:** `apply_k8s_secrets` creates the three Secrets with `create_k8s_secret_if_missing` and `random_secret` (48 hex characters from `/dev/urandom`; no new host tool). They're in `K8S_SECRETS`, so the run script checks them before deploying and `--reset` deletes them. `reset_kubernetes` then deletes `rustfs-data`, and `--down` says the runs' files are kept.
* **Verified** in minikube (the chart's static analysis passes too):
  * the Secrets were created once (a second attempt kept them);
  * RustFS ran as 10001 with its claim bound, and the setup Job completed and created both users;
  * stand-in pods, each labelled as a component:
    * core-api could use `runs/` and `health/`, and was refused elsewhere and on creating buckets;
    * Funnel's worker (by DNS name) could use `runs/` only;
    * the Nextflow Job reached RustFS by address with no DNS at all;
    * an unlabelled pod and an executor couldn't connect;
  * uninstalling the release removed RustFS and the compute namespace but kept the claim. Reinstalling adopted the same volume with its objects and users intact, and a plain `helm upgrade` ran the setup Job again without trouble;
  * the release was uninstalled afterwards, as before the check. The empty claim and the three Secrets remain, as setup and `--down` would leave them.

### Task 6: Funnel on S3, and the compute namespace

* **Funnel 0.12.3 in both modes** (Task 1), pinned by digest: the chart, and native mode's Docker Compose image and config, checked against 0.12.3's retired settings.
* **Funnel's config:**
  * its S3 storage pointed at the local server;
  * `LocalStorage` off;
  * stage 2's placeholders dropped where Task 1 allows.
* **The worker and executor templates** follow Task 1's choice of how they share files.
* **RBAC:** narrow or drop the cluster-wide permission on PersistentVolumes if the per-task volume changes (spec 0011, Future Directions).
* **Pod Security:** once nothing mounts a `hostPath`, the compute namespace enforces `restricted`.
* **The quota and LimitRange** cover anything new, e.g. `requests.storage` for per-task claims.

**Decisions (Task 6):**

Agreed before starting (2026-11-18):
* **Helm mode is S3 only:** there's no `hostPath` anywhere in the chart (Funnel, core-api, the Nextflow Job), and no switch back. That's what lets the compute namespace enforce `restricted`. The Kubernetes engine's shared-filesystem code stays, unit tested, for a real cluster with a shared volume. The EFS fallback isn't needed (Task 1 was a go). Locally the S3 server is RustFS; on AWS, Amazon S3.
* **The local connection switches to S3 in this task,** together with Funnel, so helm mode is never half switched. Task 7 keeps the setup and run-script clean-up (the node directory) and the docs.
* **Funnel's database** goes on a small claim in the compute namespace, deleted with the release on `--down`. core-api keeps its own record of every task, and run files are in RustFS.
* **Native mode enforces task resources:** Funnel 0.12.3's `Worker.Container.EnforceResources`, so a Docker task gets the CPU and memory limits Kubernetes already applies.

Done 2026-11-18:
* **Helm mode on S3, with no `hostPath`:**
  * the local connection (`_compute.tpl`) is `s3`: RustFS's `refcloud-compute` bucket, its Service endpoint with path-style access, core-api's key variables, a placeholder run role (RustFS ignores it) and credentials of up to 12 hours;
  * core-api no longer mounts the node's storage, and `refcloud.computeStorageRoot` is gone from the chart (the run script still creates the directory until Task 7);
  * the compute namespace enforces `restricted`.
* **Funnel 0.12.3 in the chart** (pinned by digest):
  * its config enables only `AmazonS3`, pointed at RustFS; credentials come from the workers' environment;
  * `Kubernetes.Resources` (defaults and maximums) come from the connection, written in `Gi`, because Funnel reads them in GiB but compares them to TES's GB values as plain numbers;
  * `Kubernetes.Storage` (`files/funnel/storage.yaml`) gives each task a claim of its own from the default StorageClass;
  * its database is on its own 1Gi claim (`funnel-db`).

  The volume and claim templates, stage 2's placeholder S3 entry and the PersistentVolume ClusterRole are all gone. That resolves spec 0011's Future Direction on narrowing Funnel's cluster-wide permission. Funnel's Role is unchanged and was enough.
* **The workspace claim's size** is the task's `disk_gb`, or 1G (`refcloud.defaultTaskDiskGb`) for a task that asks for none. Funnel sizes the claim from the task as submitted, before applying its own defaults, so a request of 0 would make an invalid `0G` claim. This is an upstream candidate (Future Directions).
* **Funnel's credentials** are copied into the compute namespace by the chart (`s3-secret.yaml`, `lookup`), as agreed in Task 5. A deploy that reaches a cluster without setup's Secret fails, saying to run setup.
* **Worker and executor templates:** the worker has no `hostPath`, `WorkDir: /tmp` and a writable root, with the `funnel-s3` environment. The executor no longer renders `hostPath` volumes (Funnel adds the workspace mounts) and has the `podAffinity` to its worker.
* **The quota** adds `persistentvolumeclaims` and `requests.storage` (tasks in flight × `max-task-disk-gb`, plus `funnel-db`, half again).
* **Native mode:**
  * the Dockerfile is on 0.12.3, pinned by digest; it's still Alpine, so `apk add docker-cli` works;
  * the Compose image tag follows;
  * the Docker run template is 0.12.3's default (with `{{.GPUArgs}}`, which 0.12 requires) plus our executable 1 GB `/tmp`, with `EnforceResources: true`;
  * the docs site's platform table says Funnel 0.12.3.
* **Verified:**
  * the chart, `deploy/local` and Dockerfile checks pass;
  * native mode, in a throwaway container: Funnel 0.12.3 started on a copy of native mode's existing 0.11.14 database. A task with a file input and output completed, and the Docker command carried `--cpus 1 --memory 256m` (the container saw the 256 MiB limit) and the executable `/tmp`. Funnel's own task list doesn't show the old database's tasks. That's harmless: core-api serves finished tasks from its own records and asks Funnel only about active ones;
  * helm mode, deployed in minikube with no Pod Security warning:
    * core-api reported the S3 connection healthy (RustFS, the STS probe, the Kubernetes API, Funnel);
    * Funnel ran on its claim, with its Secret copied and no ClusterRole;
    * two S3 tasks sent straight to Funnel completed, with outputs in RustFS. Their claims were 1G (no disk request) and 2G (`disk_gb: 2`), the executors ran read-only with their `podAffinity` and the workers with a writable root, all accepted under `restricted`. Jobs and claims were deleted afterwards;
    * a plain busybox pod was refused, and the README's new restricted `np-check` was accepted and still couldn't reach the internet;
    * Funnel's tasks survived a restart of its server;
    * the release was uninstalled afterwards (RustFS's claim kept, empty).
  * Not yet: a run through WES. That needs a researcher's token, so it's the end-to-end in Task 8, along with native mode's.

### Task 7: The local connection, setup and the run script

* **Helm mode's local connection uses S3** (decision 2).
  * core-api no longer mounts the `hostPath`.
  * `prepare_compute_storage`, and its clean-up on `--reset`, change to match.
* **`--reset` empties the local storage,** whether by emptying the bucket or deleting the server's volume.
  * Runs left in the shared database point at prefixes that no longer exist, as with directories today.
  * `--down` and the server's volume: decide whether uninstalling keeps the data.
* **Native mode** stays unchanged (decision 1).
* **Docs:**
  * `deploy/helm/README.md`: the Compute section (the storage, the S3 server, how to look at a run's files) and troubleshooting;
  * `deploy/helm/CLAUDE.md`;
  * the root `CLAUDE.md`;
  * a mention in `deploy/local/README.md`.

**Decisions (Task 7):**

Agreed before starting (2026-11-18):
* **Stage 2's storage code goes, with no clean-up step:** `prepare_compute_storage`, `reset_minikube_compute_storage`, `delete_funnel_volumes` and their constants. Nobody else has stage 2's interim setup (it was too recent), so there's no one-time clean-up command either.
* **Old runs recorded with node paths are left as they are:** stage 2's runs in a local database now point at a connection on S3, so WES can't show their outputs or logs. `--reset` clears them, and the situation can't recur now that stage 2 is behind us.

Done 2026-11-18 (the connection switch and core-api's mount moved to Task 6, and `--reset` deleting RustFS's data was done in Task 5):
* **Scripts:** removed `prepare_compute_storage` (the run script's node-directory step), `reset_minikube_compute_storage`, `delete_funnel_volumes` (from `--reset` and `--down`), and the `MINIKUBE_COMPUTE_STORAGE_ROOT` and `COMPUTE_USER_ID` constants. Nothing references them any more, in the scripts or in CI. `reset_kubernetes`'s comment now mentions RustFS's data.
* **Docs:**
  * `deploy/helm/README.md`: the setup and `--reset` notes (RustFS's Secrets, its data), the run steps (no node directory; RustFS's setup hook), the Compute section (RustFS, the three accounts, task files copied in and out by Funnel's workers), Teardown (`--down` keeps runs' files), the Secrets table, plus Task 6's run-files and troubleshooting notes;
  * `deploy/helm/CLAUDE.md`: the `--down` and `--reset` note;
  * the root `CLAUDE.md`: helm mode's runs keep their files in RustFS;
  * `deploy/local/README.md`: its helm-mode paragraph was still stage 1's ("helm mode has no compute"), now helm mode's own platform and S3 storage.
* **Verified:** the scripts parse (`zsh -n`). A deploy without the node-directory step came up with the connection healthy. The real `./scripts/refcloud-run-helm.sh --down` uninstalled the release, said the runs' files are kept, and left `rustfs-data` bound. `--reset` itself drops the databases, so it's left to Task 8's end-to-end test.

### Task 8: Tests, docs and the end-to-end check

* **Static analysis and tests:** core-api (unit tests and `staticAnalysis`), the admin CLI, the helm chart, `deploy/local` and the Dockerfiles. Run the secrets scan before committing.
* **End to end,** on a fresh helm-mode setup (`./scripts/refcloud-setup.sh --reset`):
  * the demo;
  * a cancel;
  * a core-api restart during a run (with the D23 caveat, as in spec 0011);
  * Task 3's credential check;
  * a look in the bucket afterwards: `outputs/` and `engine/` are kept, and the rest of the run's prefix is gone.
* **Isolation:** spec 0011's Task 6 checks, updated for the S3 server.
* **Docs site:**
  * the platform types table shows S3 storage in helm mode;
  * the compute overview's storage text is updated.
* **Discussion doc and milestones:**
  * D13 and D15 ("MinIO" becomes the chosen server);
  * the To Verify list;
  * the Next Step;
  * item 2 of the milestones doc is marked done.
* **Production notes** in [spec 0010](../active/0010-production-rollout.md):
  * the connection's `s3` storage;
  * the bucket and the IAM roles;
  * the SDK changes;
  * nf-amazon in the Nextflow image;
  * Funnel's S3 config;
  * no `hostPath`.

**Decisions (Task 8):**

Agreed before starting (2026-11-18):
* **The user's end-to-end test includes a credential check:** an optional step with ready-made commands. A stand-in pod labelled as a Nextflow Job uses a running run's credentials from its Secret to try another run's prefix and the bucket root (both refused) and its own prefix (allowed).
* **The docs site changes are small:** the platform types table (storage: shared filesystem in native mode, S3 in helm mode) and the overview's storage paragraph. A fuller page on run storage goes with the general docs pass (milestones item 6).

Done 2026-11-18:
* **Docs site** (`compute/compute-overview.mdx`):
  * the platform types table: Funnel 0.12.3 on both types, nf-amazon on the Kubernetes type, and each type's storage (a shared directory in native mode, S3 in helm mode);
  * a Storage paragraph: storage is a connection setting, RustFS stands in for Amazon S3 locally, each run's engine gets credentials for its own files only, and tasks' containers never reach the storage;
  * the Inputs bullet: inputs are downloaded into the run's own storage, and `workflow_params` can't name storage locations (`s3://...`).

  The site's format, lint, type checks and build pass.
* **Discussion doc:**
  * D13 and D15: RustFS replaces "MinIO", since MinIO CE is archived, and D15 records what stage 3 added;
  * D16's stage list: S3 staging is done for the Kubernetes type, with the Docker type left to this spec's Future Directions;
  * To Verify item 8: done, with the results;
  * the Next Step: links this spec, then the AWS platform (milestones item 3);
  * Future Directions: object storage is done for the Kubernetes type, while DRS access URLs for outputs are still to come.
* **Milestones doc:** item 2 is marked "Built 2026-11-18, end-to-end test pending", with a schedule note, and the risks are updated.
* **Production notes** in [spec 0010](../active/0010-production-rollout.md):
  * stage 3 in production: the connection's `s3` storage and its Task 3 fields;
  * IAM split into core-api's role, the run role it assumes, and Funnel's workers' role;
  * the bucket, an S3 route for the Nextflow Jobs and Funnel's workers, and DNS for the Nextflow Jobs (corrected during Task 8: the workers already have DNS);
  * an EBS StorageClass for task workspace claims;
  * the SDK changes, nf-amazon in the Nextflow image, Funnel 0.12.3's S3 config, and no `hostPath`.
* **Fixed two `.gitignore` rules that hid files the branch needs.** Neither was this spec's doing, but its commit needs both.
  * `compute/.gitignore` ignored everything but `workflows/`, so the Nextflow image (`compute/images/nextflow/`: its Dockerfile, stage 2's `refcloud-nextflow` and this spec's `refcloud-run-s3`) was never committed. That broke the image's push workflow, its static analysis and the helm build on any fresh checkout. `!/images/` now tracks it, and run storage stays ignored.
  * `apps/core-api/.gitignore`'s `*.log` hid the two engine-log fixtures `NextflowEngineTest` has read since spec 0008 (`src/test/resources/compute/*.log`). Without them, three of its tests fail on a fresh checkout. `!src/test/resources/**/*.log` now tracks them, and other logs stay ignored. The fixtures hold no tokens or local paths.
* **Checks, all passing:**
  * core-api: 317 tests, `staticAnalysis`, and the `gradle.lockfile` audit (0 findings);
  * the admin CLI: ruff format and check, pyright (0 errors), and the `uv.lock` audit (0);
  * the chart: lint, kubeconform and trivy-config;
  * `deploy/local` and the Dockerfiles: compose, caddy, hadolint and trivy-config;
  * the docs site.
* **Isolation:** spec 0011's Task 6 checks, updated for RustFS, passed again against the final deploy. Each check was run from a stand-in pod that runs under `restricted`.

  | Stand-in pod | Can reach | Blocked |
  |---|---|---|
  | core-api's label, release namespace | RustFS | |
  | unlabelled, release namespace | | RustFS, Funnel's TES |
  | the Nextflow Job's label | RustFS (by address), core-api | Funnel's TES, the internet, DNS |
  | Funnel's worker label | RustFS (by name), Funnel's RPC | the internet |
  | Funnel's executor label | | RustFS, the internet |
  | unlabelled, compute namespace | | RustFS |

  The release was uninstalled afterwards. RustFS's claim and its three Secrets are kept, as `--down` keeps them.

**The user's end-to-end tests** (saved for the end of the spec; record results below). Commands run from the repo root, in helm mode's kubectl context (`minikube`):

1. **Fresh setup:** `./scripts/refcloud-setup.sh --reset`.
   * Expect it to uninstall the release (if installed), delete the Kubernetes Secrets (RustFS's three included) and RustFS's data claim `rustfs-data`, then create new random `refcloud-compute-s3-{root,core-api,funnel}` Secrets.
   * Stage 2's `/data/refcloud-compute` and the spike's `/data/refcloud-spike` on the minikube node are no longer touched; to remove them: `minikube ssh -- sudo rm -rf /data/refcloud-compute /data/refcloud-spike`.
2. **Run helm mode:** `./scripts/refcloud-run-helm.sh`.
   * Expect the six images, then the release with RustFS. Its `rustfs-setup` hook creates the `refcloud-compute` bucket and the `core-api` and `funnel` users.
   * Expect the registration of `nextflow-demo-kubernetes`, which is healthy (its health check covers S3 and STS).
3. **Access:** in the researcher UI, register and request **1000 Genomes** and **Nextflow Demo (Kubernetes)**. Approve both with `kubectl exec deploy/refcloud-admin-cli -- refcloud visa ...` (see the Compute section of `deploy/helm/README.md`).
4. **The demo:** `./scripts/refcloud-compute-demo.sh <token>`.
   * Expect the run to go `QUEUED` → `RUNNING` → `COMPLETE`, the report to download, and its MD5 to match DRS. Note the run ID.
   * Meanwhile, `kubectl -n refcloud-compute get jobs,pods,pvc -w` shows:
     * the run's `nextflow-<run id>` Job;
     * its task's Funnel worker and executor Jobs;
     * the task's workspace claim, deleted when the task ends.
5. **The bucket afterwards:**

   ```sh
   kubectl exec deploy/rustfs -- sh -c 'HOME=/tmp RC_HOST_l="http://$RUSTFS_ACCESS_KEY:$RUSTFS_SECRET_KEY@127.0.0.1:9000" rc object list l/refcloud-compute/runs/<run id>/ --recursive'
   ```

   Expect only `engine/` (the workflow archive, `refcloud.config`, `params.json`, the logs and `engine.exitcode`) and `outputs/` (the report). `inputs/` and `work/` are gone.
6. **Credentials, then a cancel:** submit a run (the WES demo page's `POST /runs`). Find its ID with `kubectl -n refcloud-compute get jobs`.
   * While it's `RUNNING`, check its credentials from inside its own Nextflow Job, which holds them in its environment. `RUN` is this run's ID, and `OTHER` is step 4's run ID:

     ```sh
     RUN=<this run's id>; OTHER=<step 4's run id>
     kubectl -n refcloud-compute exec job/nextflow-$RUN -- sh -c '
       s3() { curl -s -o /dev/null -w "%{http_code}\n" --aws-sigv4 "aws:amz:${AWS_REGION}:s3" \
         --user "${AWS_ACCESS_KEY_ID}:${AWS_SECRET_ACCESS_KEY}" --header "x-amz-security-token: ${AWS_SESSION_TOKEN}" "$1"; }
       bucket=${REFCLOUD_RUN_URL%/runs/*}
       echo "own run:        $(s3 "$REFCLOUD_RUN_URL/engine/params.json")"
       echo "other run:      $(s3 "$bucket/runs/'"$OTHER"'/engine/params.json")"
       echo "bucket listing: $(s3 "$bucket?list-type=2")"'
     ```

     Expect `200`, `403`, `403`. The first `200` shows the signing works, so the two `403`s are the session policy refusing. This replaces the stand-in pod agreed above: the Job's own pod has the run's credentials, its label and its network access, with nothing to copy. The commands haven't been run against a real run (that needs a researcher's token). Their parts were verified separately: curl's signing with a session token is the launcher's (Task 4), and the session policy's scoping on RustFS (Task 3).
   * Then cancel it (`POST /runs/<run id>/cancel`). Expect:
     * `CANCELED`;
     * its Job and its Secret (`nextflow-<run id>-tes-token`) gone: `kubectl -n refcloud-compute get jobs,secrets`;
     * its task cancelled, and its workspace claim gone.
7. **Restart (D23):** submit a run, and while it's `RUNNING`, run `kubectl rollout restart deployment/refcloud-api`. Expect the new core-api to follow the run to its end, reading the exit code from S3. With nf-ga4gh 1.5.0 the likely end is `SYSTEM_ERROR`, "the workflow engine lost contact with the refcloud TES"; if Nextflow didn't poll while the API was down, the end is `COMPLETE`. Either shows reattachment; record which.
8. **Run files survive `--down`:**
   * Run `./scripts/refcloud-run-helm.sh --down`. Expect it to say the runs' files are kept.
   * Then run `./scripts/refcloud-run-helm.sh` again, and download step 4's report again:

     ```sh
     curl -s --cacert "$(mkcert -CAROOT)/rootCA.pem" -H "Authorization: Bearer <token>" \
       https://refcloud.ga4gh.localhost/ga4gh/wes/nextflow-demo-kubernetes/v1/runs/<run id>
     ```

     Fetch the `url` of its file in `outputs.files` the same way.
9. **Native mode on Funnel 0.12.3:**
   * Stop the tunnel (Ctrl+C) and run `./scripts/refcloud-run-native.sh`. Docker Compose builds the new `refcloud-local/funnel:0.12.3` image on first start.
   * Request and approve **Nextflow Demo** (`uv run refcloud visa approve ...` in `apps/admin-cli`).
   * Run `./scripts/refcloud-compute-demo.sh <token>`; it picks `nextflow-demo`. Expect `COMPLETE` and a matching MD5.
10. **Optional:**
    * the docs site's compute overview (`npm run start` in `apps/docs`);
    * an isolation spot check in helm mode, with `deploy/helm/README.md`'s restricted `np-check` pod. Its `nc -z -w 3 1.1.1.1 443` should fail.
11. **Before committing:**
    * add the newly tracked files: `compute/images/nextflow/` and `apps/core-api/src/test/resources/compute/*.log`;
    * leave the deletions of `files/funnel/pv.yaml` and `pvc.yaml` in place;
    * run `./scripts/refcloud-static-analysis-secrets.sh staged`.

**Results:** the user ran the end-to-end tests on 2026-11-18 from a fresh `--reset` setup, and everything passed. Steps 6 and 7 ran from a helper script that submits the demo run and acts as soon as its Nextflow pod is up, since the demo is `RUNNING` for under a minute.

## Future Directions

* S3 storage for the `nextflow-funnel-docker` type (decision 1).
* Presigned-URL redirects for large output downloads on AWS (decision 4).
* **Upstream (Funnel):** let 0.12.3's worker stage output snapshots inside the task workspace, or in a configurable directory, so the worker can keep a read-only root filesystem (Task 1).
* **Upstream (Funnel):** size the workspace claim from the task after `Kubernetes.Resources.Defaults` are applied, so a task with no disk request gets the default rather than a `0` size (Task 6; the chart repeats the default meanwhile).
* Refreshing a run's storage credentials while it runs, so runs on AWS can last longer than about 45 minutes (Task 1). For example, a `credential_process` helper in the Nextflow image that asks core-api for new credentials with the run token.
* Server-side copies for DRS inputs already in S3, or passing signed URLs as D17 suggested, instead of streaming each input through core-api.
* Workflow outputs as DRS objects (D18). With outputs in S3, they can be served as `aws_s3_access_object`s.
* Retention for kept outputs (discussion doc, Future Directions). On AWS this is a bucket lifecycle rule.
