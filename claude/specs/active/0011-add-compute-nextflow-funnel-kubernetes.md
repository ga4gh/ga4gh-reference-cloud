# Feature Spec: Add compute stage 2 to the refcloud: Nextflow + Funnel (Kubernetes) in helm mode

**Branch:** `ja-iss-60-nextflow-funnel-kubernetes`

**Linked GitHub Issue:** [Issue 60](https://github.com/ga4gh/ga4gh-reference-cloud/issues/60)

## Objective

Add the second compute platform type, **Nextflow + Funnel (Kubernetes)** (`nextflow-funnel-kubernetes`), and run it in **helm mode** (minikube), with storage on a `hostPath` volume. This is stage 2 of the plan in [`claude/discussions/0000-about-adding-compute.md`](../../discussions/0000-about-adding-compute.md) (D16), building on stage 1 ([spec 0008](../archive/0008-add-compute-wes-tes.md)).

Researchers see no difference: the same WES and TES APIs, visas, allowlists, limits, DRS inputs and output downloads as stage 1. Behind them:

* Nextflow runs as **one Kubernetes Job per WES run**, created by core-api;
* Funnel runs in the cluster and starts each task as a **Kubernetes Job**;
* the Nextflow Jobs and task pods run in a **compute namespace** of their own, isolated with NetworkPolicies.

**Done when:** on a fresh helm-mode setup (`./scripts/refcloud-setup.sh`, then `./scripts/refcloud-run-helm.sh`), a researcher with approved visas for the `nextflow-demo-kubernetes` platform and the 1000 Genomes dataset can:

1. submit the bundled demo workflow through WES, with a 1000 Genomes `.bai` DRS object as input;
2. watch it run through the refcloud TES on Funnel, with its task running as a Job in the compute namespace;
3. download its report through the output download endpoint.

`./scripts/refcloud-compute-demo.sh -p nextflow-demo-kubernetes <token>` passes. A core-api restart during a run reattaches to it (see Task 8 for the nf-ga4gh caveat). From a pod in the compute namespace, only the connections Task 6 allows succeed. Native mode keeps working as before.

## Context

**Source of truth:** the discussion doc (decisions D1–D23) and spec 0008's Decisions sections, which record how stage 1 was built and tested. The decisions this spec relies on most:

* D3: the request path;
* D9: connections;
* D10: health;
* D11: limits;
* D12: the run-scoped TES credential and the engine adapter;
* D13, D15: shared-filesystem storage;
* D14: platform types;
* D17–D20: DRS inputs, outputs and the allowlist.

**Request path** (D3), with stage 2's pieces:

```
researcher → refcloud WES (core-api) → Nextflow (Kubernetes Job, compute namespace)
           → refcloud TES (core-api) → Funnel (Kubernetes backend) → task Jobs (compute namespace)
```

**What stage 1 already provides,** reused unchanged where possible:

* the WES and TES APIs, run lifecycle, queue and poller, run-scoped TES tokens, the allowlist, DRS input staging and output downloads;
* the `WorkflowEngine` interface (`platformType()`, `launch`, `status`, `terminate`, `cleanUp`). Stage 2 adds a second implementation next to `NextflowEngine`;
* connection health checks, service-info and the service registry;
* the demo script (`-p <platform>`, `REFCLOUD_DEMO_BASE_URL`);
* the chart's ingress routes for `/ga4gh/wes/` and `/ga4gh/tes/`.

**Stage 1 tracks a run through files** its launcher writes into the run directory: the engine's process ID and, when it finishes, its exit code. That's how core-api follows runs across its own restarts. Stage 2 replaces the host process with a Job, but the run directory is still shared storage, so the same files can be written from inside the Job (Task 4).

**Decided before drafting (2026-11-16):**

* **Two demo platforms, not one.** `nextflow-demo` stays on native mode's connection (`nextflow-funnel-docker-local`). A new `nextflow-demo-kubernetes` uses a new `nextflow-funnel-kubernetes-local` connection.
  * Both modes share one database, and runs are tied to the stack that ran them. With one shared platform, helm mode's engine would look in Kubernetes for a run started in native mode, find nothing, and fail it.
  * In each mode, the other mode's platform shows as unavailable (connection not configured), as `nextflow-demo` does in helm mode today. Researchers request access to each separately.
* **A compute namespace created by the chart** for the Nextflow Jobs and task pods, so isolation applies to the whole namespace: NetworkPolicies, a resource quota, no service account tokens. Production needs the same.
  * It's **named per release** (e.g. `<release>-compute`), and nothing else in the chart exists once per cluster. So several refclouds can share a cluster, e.g. to simulate separate organizations for federated analysis (Future Directions).
  * Its outgoing traffic is denied by default, with an **admin-controlled allowlist** (Task 6).
* **Our own Nextflow image:** the pinned Nextflow with the pinned nf-ga4gh plugin already inside it. Runs need no internet access to download either, so the compute namespace's outgoing traffic can be locked down, and versions can't drift. It's built with the other app images and pushed to ECR by CI.

**Storage:** a `hostPath` directory on the minikube node, mounted at the **same absolute path** in every pod that touches runs: core-api, Funnel, the Nextflow Jobs and the task pods. This is the helm-mode version of native mode's same-path mount. It only works on a single-node cluster; real clusters, including production on AWS, need stage 3's S3 storage.

**Production:** stage 2 itself isn't deployed to production (`hostPath`). Anything it adds that production will need goes into the rollout spec ([spec 0010](0010-production-rollout.md)): the Nextflow image in ECR, the compute namespace, RBAC, and Funnel in the chart.

## Engineering Tasks

### Task 1: Spike: verify the stage 2 stack

In minikube (with Calico), using throwaway resources, before designing the rest. Record what's found under Decisions, and update the discussion doc's To Verify items 3 and 7. If Funnel's Kubernetes backend doesn't fit, stop and revisit before continuing.

* **Funnel 0.11.14's Kubernetes backend:**
  * the server Deployment, and the RBAC it needs to create its worker and executor Jobs in another namespace;
  * where its database lives;
  * whether its image runs under the chart's rules (non-root, read-only root filesystem, no privilege escalation), or which exceptions it needs.
* **File staging on the `hostPath`:** Funnel's local storage with `AllowedDirs` on the shared path; the worker and executor templates mounting it at the same path; nf-ga4gh's plain-path inputs and outputs, as in stage 1.
* **Nextflow as a Job:** a first version of our image runs the bundled `drs-checksum` end to end through Funnel, with no internet access. Check Nextflow's writable paths (`NXF_HOME`, work and temp directories) under a read-only root filesystem, and both arm64 and amd64.
* **Limits:** does Funnel apply each task's CPU and memory to its Job? Stage 1's Docker backend ignores them.
* **Following a run:** the Job's status, the exit-code file in the run directory, or both, including after a core-api restart.
* **Minikube's size:** measure CPU and memory during the demo, and decide whether `start_minikube` needs explicit `--cpus` and `--memory`. Today it uses 2 CPUs and about 6 GB.
* **Outputs (To Verify 7):** default to the WES API only, with `minikube mount` as a documented debugging aid.
* **Kubernetes API access from core-api:** an existing client library or plain HTTP with the service account token. Prefer the smallest option that covers creating, reading and deleting Jobs and Secrets (see `apps/core-api/CLAUDE.md` on new dependencies).
* **Network:** list every connection the compute pods make (the refcloud TES, Funnel, DNS), for Task 6. Image pulls are the node's, not the pods'.

**Decisions (Task 1):**

**The spike was run on 2026-11-16** in minikube (with Calico):
* in a throwaway namespace with a throwaway cluster role, both deleted after; scratch files are left in `/data/refcloud-spike` on the minikube node;
* Funnel 0.11.14 (`quay.io/ohsu-comp-bio/funnel:0.11.14`), and Nextflow 26.10.5 with nf-ga4gh 1.5.0;
* the bundled `drs-checksum` workflow on a local file, with nf-ga4gh pointed straight at Funnel. The refcloud's WES and TES were proven in stage 1.

* **Result: the stack works end to end, in about 15 seconds.**
  1. Nextflow runs as a Job with no internet access: Calico allowed it only Funnel and DNS.
  2. It sends the task to Funnel's Kubernetes backend.
  3. A worker Job and an executor Job run the task.
  4. The report lands in the run's `outputs/`, and stage 1's launcher writes exit code 0.
* **Funnel's Kubernetes backend is built around S3, so `hostPath` needs workarounds.**
  * **Two layers.** For each task, the server creates a worker Job (`funnel worker run --taskID …`), and the worker creates an executor Job for each of the task's executors. So workers need:
    * Kubernetes API access: a service account token, and a Role to create Jobs and watch pods;
    * network access to the server's RPC port (9090), to fetch their task and report events (with the embedded database).

    Executors need neither.
  * **For every task with inputs or outputs** (all of nf-ga4gh's), the backend refuses to run unless `GenericS3` has a bucket and region. It also creates a **cluster-scoped PersistentVolume** and a claim per task; its default template uses AWS's S3 CSI driver. Verified workaround:
    * a placeholder `GenericS3` entry with `Disabled: true`;
    * PersistentVolume and claim templates using `hostPath`. The volume must be labelled `namespace: <namespace>`, or Funnel won't delete it;
    * a **ClusterRole** for Funnel's server on `persistentvolumes`: create, get, list, update, patch and delete.

    Funnel's clean-up then removes every Job, pod, claim and volume it created.
  * **Custom worker and executor templates are needed anyway,** since the defaults expect per-task config maps and claim subpaths. In ours, workers and executors mount the shared `hostPath` at the same path and use it directly.
  * **Cosmetic:** clean-up tries to delete a per-task ServiceAccount it never created, and logs an error. Tagging tasks `_WORKER_SA` (nf-ga4gh's `tes.tags`) may avoid it. Check in Task 3. (Task 3: it doesn't, and it mustn't be used.)
  * **Upstream candidate:** a Funnel option to skip the per-task volume when the templates don't use it would remove the need for cluster-wide permissions.
* **Limits:** the executor template receives each task's CPU and memory. Setting limits equal to requests enforces them (verified: 1 CPU, 500M). Funnel's default template sets requests only. Workers need small requests and limits of their own.
* **Everything can run non-root.** Funnel's server, the workers, the executors and Nextflow all ran as user 10001, with:
  * read-only root filesystems;
  * all capabilities dropped, and no privilege escalation;
  * the `RuntimeDefault` seccomp profile.

  Every file in the run directory belonged to 10001.
  * **Executors must run as 10001 too.** With capabilities dropped, a root executor can't write into the worker's directories (verified failure: "Permission denied"). Workflow images that need root won't run, which the compatibility table should record. `drs-checksum` (ubuntu) works.
  * **Executors and Nextflow have no service account token** (verified from inside the executor).
* **The Nextflow image:**
  * the official `nextflow/nextflow` images are amd64-only;
  * ours: `eclipse-temurin:21-jre` (multi-architecture), plus the release's self-contained `nextflow-26.10.5-dist`, with `nextflow plugin install nf-ga4gh@1.5.0` into `/opt/nextflow`;
  * it runs non-root on a read-only root filesystem, given:
    * `NXF_HOME` on a writable `emptyDir`, because Nextflow creates `$NXF_HOME/secrets` at start (`NXF_ENABLE_SECRETS=false` is ignored in 26.10.5);
    * `NXF_PLUGINS_DIR=/opt/nextflow/plugins` and `NXF_OFFLINE=true`;
    * a heap cap under the memory limit (`NXF_OPTS=-Xmx640m` with a 1Gi limit).
* **Following a run:**
  * the Job's status isn't enough: the launcher exits 0 even when Nextflow fails, so the Job "succeeds";
  * the exit-code file in the run directory stays the source of truth, as in stage 1;
  * the Job's name and UID replace the process identity;
  * a Job that failed or disappeared without an exit-code file means the run was lost (deadline, eviction, out of memory), so the run ends `SYSTEM_ERROR`;
  * the launcher's process ID file isn't needed in a pod.
* **Network connections** (for Task 6):
  * Nextflow → the refcloud TES (Funnel directly in the spike), and DNS;
  * Funnel's server → the Kubernetes API, and DNS;
  * workers → Funnel's RPC port (9090), the Kubernetes API, and DNS;
  * executors → nothing;
  * core-api → Funnel (TES, 8000) and the Kubernetes API (Task 4).

  Image pulls are the node's, not the pods'.
* **Pod Security:** inline `hostPath` volumes are forbidden at both the baseline and restricted Pod Security levels. So with stage 2's `hostPath` storage, the compute namespace can enforce neither, and its pods are hardened through their security contexts (above) instead. Storage through claims (stage 3) would allow restricted, since every pod is already non-root. Decide in Task 6.
* **Minikube's size:**
  * with no refcloud release installed, minikube idles at about 2.5 GB;
  * the spike (Funnel, a Nextflow JVM, one task) peaked at about 2.9 GB;
  * Kubernetes sees 8 GB, but the minikube container is capped at about 6 GB, so the scheduler can overcommit.

  Measure with the full release in Task 8, and probably set `--memory` and `--cpus` explicitly in `start_minikube`.
* **Outputs (To Verify 7):** through the WES API only. For debugging, `minikube ssh` reaches the run directories on the node.
* **Kubernetes API access from core-api:** recommended: plain HTTP with `java.net.http` (already used by the health checks) and Jackson. It's about five calls:
  * create, get and delete a Job;
  * create and delete a Secret;
  * a `SelfSubjectAccessReview`.

  It uses the in-cluster service account token, re-read on each request because tokens rotate, and the cluster's CA certificate. There's no new dependency: core-api has no Kubernetes client, and Spring Boot doesn't manage one. The alternative, the fabric8 client with its JDK HTTP client, adds dependencies to the lockfile and the CVE audit.
* **Decided (2026-11-17):**
  * **every compute pod runs as non-root user 10001,** executors included. Workflow images that need root aren't supported; the compatibility table records them;
  * **Pod Security in stage 2:** every pod is hardened through its own security context, and the compute namespace is labelled `warn` and `audit` at `restricted`, with no `enforce` label. Stage 3's storage through claims allows `enforce: restricted`;
  * **core-api calls the Kubernetes API with `java.net.http` and Jackson,** with no client library;
  * **Funnel, with the cluster-wide volume permission.** The chart grants it once, to Funnel's server's service account only, through a ClusterRole and binding named per release; workers and executors don't get it. PersistentVolumes have no namespace, so the permission covers every volume in the cluster, not only Funnel's own. Accepted for stage 2: minikube has no other volumes, and only core-api can reach Funnel's API. Production needs the same permission (Funnel's default S3 storage is also a volume per task), so it's narrowed before then (Future Directions).

### Task 2: Chart compute wiring and the core-api image

* **Connections in the chart** (spec 0008, Future Directions):
  * a `ga4gh.referenceCloud.compute.connections` value, rendered into a ConfigMap as `connections.yml` and mounted into core-api and the admin CLI, with `REFCLOUD_COMPUTE_CONNECTIONS_FILE` pointing at it;
  * its local default defines `nextflow-funnel-kubernetes-local`, using the compute namespace the chart creates for this installation, so helm mode has compute out of the box; production sets its own;
  * connection credentials come from Kubernetes Secrets, by environment variable name, as in `connections.yml` today;
  * the compute service-info settings (ID suffix, auth instructions URL) and the engine callback URL (`http://refcloud-api:8080`) as values, passed as environment variables.
* **The storage volume:** the `hostPath` mounted into core-api at the storage root.
* **The core-api image:**
  * it needs `git`, because WES fetches pinned commits;
  * it needs the bundled workflows, with `ga4gh.refcloud.compute.wes.bundled-workflows-base-dir` pointing at them.
* Check: the chart renders and passes static analysis, and core-api starts with the connection and reports the platform's health.

**Decisions (Task 2):**

Done 2026-11-17:
* **The local connection moves to Task 4,** with its type: core-api refuses to start with a connection of an unknown type. Task 2 ships the wiring with no connection (compute is off in helm mode), and Task 4's check includes "core-api reports the platform's health".
* **Connections:** `ga4gh.referenceCloud.compute.connections`, next to the other `ga4gh.referenceCloud` values, empty by default:
  * rendered into the `refcloud-compute-connections` ConfigMap (`templates/common/compute-connections.yaml`) and mounted into core-api and the admin CLI at `/etc/refcloud/compute/connections.yml`. With no connections, the file is only a comment, which both read as "compute is off";
  * core-api and the admin CLI read it at startup, so their pods carry a checksum of the file and restart when it changes;
  * **Task 4 adds the local connection in the template, not in `values.yaml`:** it needs the release's namespace and storage path, which values can't compute, and Helm can't remove a default map entry from an override file (`{}` merges). The value then holds only extra connections (production's).
* **Service-info and callback:**
  * values for the ID suffix (`local.helm`, like DRS's IDs) and the environment (`local`);
  * the auth instructions URL is derived from the docs host (`https://docs.<baseURL>/docs/compute/access`), so production needs no value;
  * the engine callback URL is fixed in the template (`http://refcloud-api:8080`): it's the chart's own Service.
* **Storage:** `/data/refcloud-compute/<release>` on the minikube node (persisted across restarts), one directory per release:
  * mounted into core-api at the same path, as a `hostPath` of type `Directory`, local only (`refcloud.computeStorageRoot` in `_helpers.tpl`);
  * `refcloud-run-helm.sh` creates it before deploying, owned by 10001 with mode 0750 (`MINIKUBE_COMPUTE_STORAGE_ROOT` in `scripts/lib/common.sh`). A directory Kubernetes created would be root's and unwritable by the non-root pods. Clearing it on `--reset` is Task 7.
* **Images:** core-api and the admin CLI build from the repo root, with their Dockerfiles in their app directories, so both bundle `compute/workflows/` at `/app/compute/workflows` (env vars in the Dockerfiles point at `/app`).
  * The root `/.dockerignore` is an allowlist of what they copy. `minikube image build` copies the whole context into the node first and reads only that file: without it, a repo-root build took 7.5 minutes (the repo is 2.1 GB, mostly `node_modules`); with it, under a second.
  * `REFCLOUD_IMAGES` (`scripts/lib/common.sh`) gives each image its context and Dockerfile. CI's `push-docker-image.yml` gains an optional `dockerfile` input.
  * The core-api image installs `git` (`apk add`, as the admin CLI does with `apt-get`).
* **The admin CLI checks bundled workflows in its pod too:** its image bundles them, `REFCLOUD_BUNDLED_WORKFLOWS_BASE_DIR` (default `../..`, the repo root from `apps/admin-cli`) says where they are, and an unknown name is now refused instead of only warned about.
* **Verified** in minikube (Calico): the chart, Dockerfile and workflow checks pass; core-api has `git` and the workflows, writes to the storage directory, logs "no compute connections configured (/etc/refcloud/compute/connections.yml); compute is off", and serves WES service-info with ID `org.ga4gh.refcloud.wes.nextflow-demo.local.helm` and the docs auth URL; the admin CLI pod reads the same file (the native-mode platform shows its connection as `MISSING`) and refuses an unknown bundled name.

### Task 3: Funnel in the chart

* Funnel 0.11.14 with its Kubernetes compute backend:
  * a Deployment, a ClusterIP Service and a ConfigMap (`funnel.yml`);
  * a ServiceAccount, with a Role and RoleBinding to create its Jobs in the compute namespace;
  * its database on the shared storage, or wherever Task 1 settles.
* Which namespace Funnel's server runs in is settled with Task 1's network findings. The compute namespace keeps the compute stack together, so core-api is its only caller from outside.
* The image is pinned (version and digest), and matches native mode's Funnel version. It follows the chart's security context rules, or records each exception with its reason in `.trivyignore.yaml`.
* Probes, and a resource request and limit.

**Decisions (Task 3):**

Done 2026-11-17:
* **The chart creates the compute namespace now** (`templates/common/compute-namespace.yaml`, `refcloud-compute` for the `refcloud` release), since Funnel needs it, with Task 1's Pod Security labels (`warn` and `audit` at `restricted`). Task 6 adds its quota, limits and NetworkPolicies. Funnel's server runs there, with its workers and the executors, so core-api is its only caller from outside.
* **Local only:** the namespace, Funnel and its RBAC render only when `cloudEnvironment` is `local` (their storage is a `hostPath`). The AWS platform spec adds its own.
* **Funnel's config** (`templates/apps/funnel/config-map.yaml`), from the spike's:
  * its BoltDB database at `<storage root>/funnel/db/funnel.db`, so tasks survive a restart; one replica with the `Recreate` strategy, since only one server can open the file;
  * the worker's directories at `<storage root>/funnel/work`, removed after each task (`LeaveWorkDir: false`);
  * **`LocalStorage.AllowedDirs` is `<storage root>/runs` only,** not the whole root, so no task can read or write Funnel's own files (core-api already limits each task to its own run directory);
  * the worker, executor, volume and claim templates live verbatim in `files/funnel/` (Funnel renders them per task). The config map embeds them with `.Files.Get`, replacing only two placeholders (the storage root and Funnel's image), so Helm never interprets their braces;
  * the unused volume points at `<storage root>/funnel/unused-volumes/<task>`, which is never created because nothing mounts it.
* **Image:** `quay.io/ohsu-comp-bio/funnel:0.11.14`, pinned by its multi-architecture digest in `_helpers.tpl` (`refcloud.funnelImage`), for the server and the workers; native mode's Dockerfile notes the version sync. It runs as user and group 10001 (the image defaults to root).
* **RBAC** (`templates/apps/funnel/rbac.yaml`), least privilege, verified by running tasks:
  * Funnel's server: Jobs (create, get, list, watch, delete), pods (get, list, watch), claims (create, get, list, watch, delete), and the ClusterRole on volumes, named per release (`<release>-compute-funnel-persistentvolumes`);
  * workers: Jobs (create, get, list, watch, delete), pods (get, list, watch) and pod logs (get);
  * **no permission on ServiceAccounts.** At clean-up Funnel deletes a per-task ServiceAccount it never created (`funnel-worker-<namespace>-<task>`). With a task tagged `_WORKER_SA`, it deletes the ServiceAccount the tag names instead: tagged `funnel-worker`, it tried to delete the shared worker account. So the tag doesn't help, and the permission would let a task's tags delete the namespace's accounts. Without it, each delete fails and Funnel logs two errors per task and one at startup: expected, documented in `deploy/helm/CLAUDE.md`.
* **Probes** on Funnel's TES service-info (`/ga4gh/tes/v1/service-info`, as core-api's health check calls it); requests 50m and 64Mi, limits 500m and 256Mi. Workers: requests 50m and 64Mi, limits 1 CPU and 256Mi.
* **Trivy:** KSV-0109 ("secrets in a ConfigMap") matches the word `automountServiceAccountToken` in the executor template: a false positive, accepted in `.trivyignore.yaml` for Funnel's config map only.
* **Verified** in minikube with Calico, submitting TES tasks to Funnel from core-api's pod (busybox, input and output in a run directory):
  * a task ran in seconds through a worker Job and an executor Job, and wrote the right output; every Job, claim and volume was deleted after, and the worker's directory too;
  * Funnel's startup clean-up of a fake orphaned task deleted its volume but not its claim, which stays unbound until the namespace goes: harmless;
  * `helm upgrade` prints the expected Pod Security warning when Funnel's Deployment changes (noted in the README's troubleshooting);
  * the chart passes static analysis, and renders no compute resources for AWS.

### Task 4: The Kubernetes engine in core-api

* **The local connection** `nextflow-funnel-kubernetes-local`, added by the chart's connections template in local mode (Task 2's decisions), so helm mode has compute out of the box. Check that core-api starts with it and reports the platform's health.
* **Refuse Funnel's control tags:** core-api's TES API reserves only `refcloud_` tag keys today, so a task's `_`-prefixed tags (e.g. `_WORKER_SA`, Task 3) reach Funnel, which acts on them. Refuse keys starting with `_` too, like the reserved prefix, with a unit test.
* **The platform type:** `ComputePlatformType.NEXTFLOW_FUNNEL_KUBERNETES` (`nextflow-funnel-kubernetes`, "Nextflow + Funnel (Kubernetes)"), and the same type in the admin CLI's `PLATFORM_TYPES`.
* **Its connection settings:** the engine section holds the Nextflow image and the compute namespace instead of a local executable, with the same Nextflow and nf-ga4gh version pins. Validation per type, in core-api and the admin CLI.
  * The namespace is where the connection's Nextflow Jobs and task Jobs run. Platforms on the same connection share it, and another connection can name another namespace.
* **A `WorkflowEngine` for the type,** launching one Job per run in the compute namespace:
  * our Nextflow image, with the run directory mounted from the `hostPath` at the same path;
  * the run-scoped TES token in a per-run Secret, deleted at clean-up;
  * the same environment as stage 1 (pinned versions, reserved names, `refcloud.config`);
  * resource requests and limits for Nextflow itself;
  * `activeDeadlineSeconds` from the run's maximum duration, `backoffLimit: 0`, and labels with the run ID.
* **Following, cancelling and cleaning up:**
  * the Job's identity (name and UID) replaces stage 1's process identity, and the exit-code file in the run directory still reports how it ended (Task 1);
  * a Job that disappeared ends the run `SYSTEM_ERROR`, as a lost process does in stage 1;
  * cancelling deletes the Job after the grace period;
  * clean-up removes the Job, its Secret and the run's workflow directory.
* **Access:** core-api's ServiceAccount gets a Role in the compute namespace, limited to what the engine does (Jobs, Secrets, reading pods and logs).
* **The Nextflow image:**
  * a Dockerfile, with its location settled in Task 1;
  * built by the run script (`minikube image build`) and by CI (pushed to ECR with the lockstep tag);
  * covered by the Dockerfile static analysis.
* Unit tests: building the Job, mapping its status, and the reserved environment.

**Decisions (Task 4):**

Agreed before starting (2026-11-17):
* **The Nextflow image:** `compute/images/nextflow/Dockerfile`, built as `refcloud-nextflow` with the lockstep tag: locally by the run script, in CI by a new `push-docker-image-nextflow.yml`, and covered by the Dockerfile static analysis (extended to `compute/`). The user creates the `refcloud-nextflow` ECR repository with Terraform; until then, CI pushes to `develop` fail, which is accepted.
* **Offline:** the Nextflow Job and the task pods keep no outgoing traffic (Task 6). The Kubernetes platform runs workflows that need neither plugins downloaded at startup nor remote files: the bundled `drs-checksum` works, and nf-core/demo doesn't (its `test` profile downloads a plugin, and test data the pipeline's config and samplesheet reference by URL, which WES never sees: WES stages only DRS URIs in `workflow_params`, D17). The compatibility table says so. Ways to widen this are under Future Directions.
* **The Job's identity:** its name derives from the run ID, and a new nullable `engine_job_uid` column on the run (Liquibase) records which Job is the run's. A schema change for production.
* **Cancelling:** core-api deletes the Job, so its pod gets SIGTERM; the launcher forwards it to Nextflow (as the pod's main process it would otherwise ignore it), so Nextflow cancels its tasks and exits. The pod's grace period is the run's cancel grace period; a forced cancel deletes the pod at once. Status reads the exit-code file first, then the pod.
* **Deadlines:** core-api enforces the max run duration as in stage 1; the Job's `activeDeadlineSeconds` adds the grace period and a margin, as a backstop. A pod that can't start (e.g. its image can't be pulled) ends the run `SYSTEM_ERROR` with that reason.
* **Network, brought forward from Task 6:** the engine callback URL becomes `http://refcloud-api.<release namespace>.svc:8080` (the short name doesn't resolve from the compute namespace), and core-api's NetworkPolicy accepts the Nextflow Jobs on its TES API.
* **Access:** core-api gets its own ServiceAccount, with a Role in the compute namespace: Jobs (create, get, list, watch, delete), pods (get, list, delete), Secrets (create, delete). No pod logs: the launcher writes them into the run directory.
* **The Nextflow Job's size** comes from the connection (default 1 CPU and 1 GB), with the JVM heap capped below the limit.
* **Version pins:** the image records the Nextflow and nf-ga4gh versions it contains, and the Job refuses to start when they differ from the connection's pins.
* **Reserved environment** adds what the Job sets: `NXF_PLUGINS_DIR`, `NXF_OFFLINE`, `NXF_OPTS`, `NXF_TEMP`.

Built 2026-11-17:
* **core-api:**
  * `ComputePlatformType.NEXTFLOW_FUNNEL_KUBERNETES`; the engine settings are per type (`nextflow-executable` for docker; `image`, `image-pull-policy`, `namespace`, `cpu-cores`, `memory-gb` for kubernetes), checked at startup;
  * `WorkflowEngine`'s `status`, `terminate` and `cleanUp` now receive the connection (the Kubernetes engine needs its namespace); `EngineStatus.Lost` carries a reason, which becomes the run's state reason;
  * `NextflowKubernetesEngine` shares `NextflowEngine`'s run files, command, environment, failure detection and clean-up; `KubernetesClient` is plain `java.net.http` and Jackson, connecting on first use, so native mode is unaffected;
  * the launcher (`run-launcher.sh`) forwards SIGTERM and SIGINT to the engine and keeps waiting for it (tested under `sh` and `dash`: the engine's own exit code is recorded);
  * `engine_job_uid` (Liquibase `modify-tb-compute-run-add-engine-job-uid`, with a check that a run has at most one engine identity); schema validated with `ddl-auto=validate`;
  * TES refuses tag keys starting with `_`;
  * unit tests for the Job, the token Secret, the environment, status mapping, the type checks and Spring's wiring of `KubernetesClient` (a second, test-only constructor broke it once: it needs `@Autowired`).
* **The Nextflow image** (`compute/images/nextflow/`): `eclipse-temurin:21.0.13_9-jre-noble`, Nextflow's `dist` build and nf-ga4gh in a builder stage; `refcloud-nextflow` wraps `nextflow` and exits 126 when `NXF_VER` or `NXF_PLUGINS_DEFAULT` differ from the image's versions (verified both ways).
* **Chart:** the local connection (limits sized for minikube: 2 runs, 2 tasks, 1 CPU and 1 GB per task; in `_compute.tpl` since Task 5); core-api's ServiceAccount and Role (`templates/apps/core-api/rbac.yaml`); the fully qualified callback URL; core-api's NetworkPolicy rule for the Nextflow Jobs.
* **Scripts and CI:** `REFCLOUD_IMAGES` builds `refcloud-nextflow`; `push-docker-image-nextflow.yml`; the Dockerfile checks cover `compute/images/`.
* **Verified in minikube:** core-api starts with `nextflow-funnel-kubernetes-local` and reports it healthy; the `nextflow-demo-kubernetes` platform was registered through the admin CLI pod (which accepted the bundled workflow) and its WES service-info is healthy, with ID `org.ga4gh.refcloud.wes.nextflow-demo-kubernetes.local.helm`.
* **Found on the way:** minikube's Calico can stop creating pods when its network plugin token expires unrenewed (after the laptop sleeps): restarting the `calico-node` DaemonSet fixes it (README, Troubleshooting).
* **Moved to Task 8** (the user tests everything at the end): an end-to-end WES run with a researcher's passport token (the demo script with `-p nextflow-demo-kubernetes`, and `REFCLOUD_CA_CERT="$(mkcert -CAROOT)/rootCA.pem"` until Task 7 teaches the script helm mode's CA), and a cancel.

### Task 5: Health and limits

* **Connection health for the new type** (D10):
  * the executor: Funnel's service-info;
  * the engine: core-api is allowed to create Jobs in the compute namespace (a `SelfSubjectAccessReview`), and the image is configured;
  * the storage: the root is writable.
* **Limits** (D11):
  * task CPU and memory reach the task Jobs (Task 1);
  * the Nextflow Job's own requests and limits;
  * per-user limits and the monthly quota work as in stage 1.
* **A ResourceQuota on the compute namespace** as a backstop, sized from the connection's pooled limits.

**Decisions (Task 5):**

Agreed before starting (2026-11-17):
* **Monthly core-hours count TES tasks only,** as in stage 1, not the Nextflow Job's own CPU (1 core for the run's whole duration). The Nextflow Jobs are capped by max-concurrent-runs and the max run duration. Counting the engine's core-hours too is a Future Direction for the AWS platform, where budgets stand for money.
* **The engine check** asks the API server, with a `SelfSubjectAccessReview` per permission (allowed for any account, so no extra RBAC), whether core-api may do everything the Kubernetes engine does in the connection's namespace. A missing namespace or Role makes the connection unhealthy with the reason, rather than failing the first run.
* **The ResourceQuota** is computed by the chart from the local connection's settings, so it follows them.
* **Known limit:** per-task disk (`max-task-disk-gb`) is admission and accounting only, as in stage 1: tasks write to the shared `hostPath`, which Kubernetes can't cap per pod.

Done 2026-11-17:
* **The engine check:** `ConnectionChecker` checks each of `NextflowKubernetesEngine.REQUIRED_PERMISSIONS` (Jobs create, get, delete; pods list, delete; Secrets create, delete) with `KubernetesClient.isAllowed` (a `SelfSubjectAccessReview`). The failure names every denied permission and the namespace; an unreachable API is a failure too. The list sits next to the engine and must match core-api's Role in the chart.
* **The ResourceQuota** (`templates/common/compute-resource-quota.yaml`): the local connection moved into one helper (`refcloud.localComputeConnection`, `templates/common/_compute.tpl`) that both the connections file and the quota read. The quota is everything the connection allows at once (each run's Nextflow Job; each task's executor and Funnel worker; Funnel's server), half again, since pods shutting down still count: today 6225m CPU and 6.3 GB requested, 9750m and 7.2 GB in limits, 11 pods.
* **Limits reaching pods:** tasks (Task 3's executor template) and the Nextflow Job (Task 4) set limits equal to requests; per-user limits and quotas are core-api's, unchanged from stage 1 (Task 8 checks them end to end).
* **Verified in minikube:** the quota is in place (only Funnel's server counts against it at rest); the connection is healthy; removing the Secrets rule from core-api's Role made it unhealthy within a minute, with "core-api may not create secrets, delete secrets in namespace refcloud-compute", and restoring it made it healthy again. (Restoring needed `helm upgrade --force-conflicts`: Helm applies server-side, and a `kubectl patch` takes ownership of the fields it changes.)
* Tests: 251, including the check's allowed, denied and unreachable cases.

### Task 6: The compute namespace and its isolation

* **The namespace**, created by the chart and **named per release** (e.g. `<release>-compute`), so two installations in one cluster each get their own. Check that nothing else in the chart exists once per cluster (e.g. a ClusterRole), or name it per release too. It has:
  * Pod Security admission labels `warn` and `audit` at `restricted`, with no `enforce`, since `hostPath` rules out baseline and restricted (Task 1);
  * service account tokens not mounted for task and Nextflow pods, unless Task 1 shows one is needed;
  * the ResourceQuota (Task 5), and a LimitRange with default requests.
* **NetworkPolicies:**
  * in the compute namespace, deny all incoming and outgoing traffic by default, then allow only what Task 1 listed: Nextflow → the refcloud TES; workers and executors → Funnel, if needed; DNS;
  * **beyond that, outgoing traffic is an admin-controlled allowlist** of destinations, empty for the demo platform. A platform can later be allowed to reach, say, a federated-learning server without redesigning the policies.
    * Configure it per connection, since a connection's pods share its namespace. Per platform only if Task 1 shows pods can be labelled by platform: core-api labels the Nextflow Jobs, but Funnel creates the task pods.
    * Locally the allowlist uses `ipBlock` CIDRs. Production on EKS Auto Mode can also filter by domain name.
  * Funnel accepts only core-api, and Nextflow if Task 1 shows it calls Funnel directly;
  * in the refcloud namespace, core-api accepts the Nextflow Jobs (its TES API), added to the `refcloud-api` policy.

  Update the policy table in `templates/common/network-policies.yaml` and `deploy/helm/README.md`.
* **Verify** with stand-in pods, as for the chart's existing policies, and with a real run:
  * a compute pod must not reach the Kratos or Hydra APIs, the database, or core-api other than its TES API;
  * a destination on the allowlist is reachable, and one that isn't times out;
  * the chart rendered for two releases (e.g. `org-a` and `org-b`) has no clashing cluster-wide names.

**Decisions (Task 6):**

Agreed before starting (2026-11-17): workflow pods get no DNS unless their connection's allowlist is non-empty, with core-api giving Nextflow its own address; the allowlist covers workflow pods only; the Kubernetes API by its looked-up endpoint; a LimitRange with defaults and a per-container maximum.

Done 2026-11-17:
* **Already in place from Tasks 3–5:** the per-release namespace with its Pod Security labels; no service account tokens on the Nextflow and executor pods; the ResourceQuota; core-api's policy accepting the Nextflow Jobs.
* **No DNS for workflow pods:** DNS lookups of made-up names can carry data out through kube-dns, which forwards them to the internet. `NextflowKubernetesEngine` resolves core-api's name itself (its Service address) and gives the Job `http://<address>:8080/ga4gh/tes/...`; Funnel's executors need no names.
* **NetworkPolicies** (`templates/common/compute-network-policies.yaml`, local only, with a table in its header): `default-deny` (every pod, in and out), then by `app.kubernetes.io/name`:
  * `funnel`: in on 8000 from core-api's pods (release namespace) and on 9090 from workers; out to the Kubernetes API and DNS;
  * `funnel-worker`: out to Funnel's 9090, the Kubernetes API and DNS;
  * `refcloud-nextflow`: out to core-api's pods on 8080;
  * `funnel-executor`, and anything else: nothing.
* **The Kubernetes API** in policies is the API server's own address and port (policies see traffic after the Service's translation): the chart looks up the `kubernetes` Service's EndpointSlices when deployed (`192.168.49.2:8443` in minikube). `helm template` can't look anything up, so it renders no API rule; a deploy that reaches a cluster but finds no endpoints fails.
* **The outbound allowlist:** `ga4gh.referenceCloud.compute.outgoingAllowlist`, a list of NetworkPolicy egress rules, empty by default. When set, a `workflow-outgoing-allowlist` policy gives the Nextflow Jobs and executors those destinations and DNS. It's per installation, which is per connection here (one compute namespace each).
* **LimitRange** (`templates/common/compute-limit-range.yaml`): defaults (requests 50m and 64Mi, limits 250m and 128Mi) for containers that set none, and a maximum per container of the largest the local connection allows (1 CPU, 1 GB today).
* **Path-level access to core-api** isn't a network matter: policies stop at ports, and core-api serves every API on 8080. The run token is what limits a Nextflow Job to its own run's TES endpoints.
* **Verified in minikube** with stand-in pods labelled as each component (busybox, `nc` and `nslookup`), against core-api, Kratos and Hydra (public and admin), Postgres on the host, Funnel (TES and RPC), the Kubernetes API, the internet (1.1.1.1:443) and DNS:
  * Nextflow: core-api only; executor and unlabelled pods: nothing; worker: Funnel's RPC, the Kubernetes API and DNS only;
  * Funnel's TES API: core-api yes, a pod in the release namespace no;
  * controls: an unrestricted pod reached the internet and Postgres, and the researcher UI reached Kratos, so the blocks are real;
  * with 1.1.1.1:443 allowlisted, the Nextflow and executor stand-ins reached it and DNS, but not 8.8.8.8:443, and the worker didn't change; removing it closed both again;
  * a real Funnel task (as in Task 3) ran under the policies and cleaned up, with no new errors;
  * rendered as `org-a` and `org-b`, the cluster-scoped names (namespace, ClusterRole, ClusterRoleBinding) and storage paths are all per release. Funnel's per-task volumes are named by its task IDs and deleted by namespace label. Two installations in one minikube would still each need their own `baseURL` (ingress host).
* Tests: 253.

### Task 7: Setup, the run script and the demo platform

* **Minikube's size,** if Task 1 says to change it: `start_minikube` sets it, and older clusters get recreate instructions, as for Calico.
* **Registering `nextflow-demo-kubernetes`:**
  * on `nextflow-funnel-kubernetes-local`, with `drs-checksum` allowlisted and the same small per-user limits as `nextflow-demo`;
  * the chart's connections are only visible inside the cluster, so register it where they are, e.g. from the run script through the in-cluster admin CLI after deploying;
  * idempotent: an existing platform is left as it is.
* **The run script** lists the helm-mode platform's WES and TES endpoints, nested as in native mode.
* **Docs:** `deploy/helm/README.md` (a compute section), `deploy/helm/CLAUDE.md` and the root `CLAUDE.md`. Native mode's docs are unchanged except for mentioning the second platform.

**Decisions (Task 7):**

Agreed before starting (2026-11-17): keep minikube as it is and size the local connection to fit; the demo platform's per-user limits match the connection's.

Done 2026-11-17:
* **Minikube's size, measured with the full release deployed:** Docker Desktop has 10 CPUs and 7.75 GiB; minikube's container is capped at 2 CPUs and 6 GiB, while Kubernetes sees 10 CPUs and 7.75 GiB (so it would overcommit); the release used 3.3 GiB at rest, leaving about 2.7. The old local limits allowed about 4.5 GB at full load.
* **So the local connection fits instead** (`_compute.tpl`): 1 concurrent run, up to 2 tasks and 1 GB of tasks in flight, 1 CPU and 1 GB per task (about 2.5 GB at full load). `start_minikube` is unchanged, so no cluster needs recreating. The quota and LimitRange followed (quota: 4725m CPU and 3.3 GB requested, 8250m and 4.2 GB in limits, 9 pods).
* **`nextflow-demo-kubernetes`** is registered by `refcloud-run-helm.sh` after deploying (`init_kubernetes_compute_platform`), through the admin CLI pod, skipping an existing platform (checked in the shared database). **It sets no per-user limits**, so the connection's apply and it follows them: researchers see 1 CPU, 1 GB and 10 GB of disk per task and 1 hour per run; concurrent runs show no per-user limit, though the connection's 1 applies to everyone. It keeps native mode's 10 core-hours a month (the connection has no monthly budget). The platform registered by hand in Task 4 was deleted (no access requests) and re-registered this way.
* **Each run script lists only its own mode's platforms** (`print_compute_urls` filters by connection key), since both modes share the database.
* **Reset and teardown:** `--reset` deletes the node's compute storage (`reset_minikube_compute_storage`, guarded like native mode's) and Funnel's leftover volumes (`delete_funnel_volumes`, by the release's namespace label); `--down` deletes the leftover volumes.
* **The demo script works in both modes:**
  * without `-p`, it uses whichever of `nextflow-demo` and `nextflow-demo-kubernetes` is healthy;
  * for `.localhost` URLs it trusts both local CAs (Caddy's and mkcert's, combined into a temporary bundle), and other URLs use the system's trust store. Before, a machine with Caddy's CA trusted only it, so the production check in spec 0010 (`REFCLOUD_DEMO_BASE_URL=https://refcloud.ga4gh.org`) would have failed TLS verification;
  * its hints name both modes.
* **Verified:** the registration (twice: registered, then skipped), both modes' listings, and the demo script through a temporary port-forward to minikube's ingress with a made-up token: it chose `nextflow-demo-kubernetes`, verified TLS through the bundle and stopped at 401; with `-p nextflow-demo`, it stopped at the new unhealthy hint. The leftover-volume clean-up ran (nothing to delete). **For Task 8:** `--reset` itself (it drops the databases), and the run script end to end with the tunnel.
* **Docs:** `deploy/helm/README.md` (a Compute section, and the run steps), `deploy/helm/CLAUDE.md`, the root `CLAUDE.md`, and native mode's README (the second platform).

### Task 8: Tests, docs and the end-to-end check

* **Static analysis and tests:** core-api (unit tests and `staticAnalysis`), the admin CLI, the helm chart, `deploy/local`, and the secrets scan before committing.
* **End to end,** on a fresh helm-mode setup:
  1. register, request access to `nextflow-demo-kubernetes` and 1000 Genomes, and approve both with the in-cluster admin CLI;
  2. run `./scripts/refcloud-compute-demo.sh -p nextflow-demo-kubernetes <token>`.
* **Restart:** `kubectl rollout restart deployment/refcloud-api` during a run. core-api must reattach. With released nf-ga4gh 1.5.0, Nextflow fails the run if the TES API is down for more than a poll interval (D23), so test reattachment with the patched plugin, or record the outcome against D23.
* **Isolation:** Task 6's checks, from a throwaway pod in the compute namespace.
* **Docs site:**
  * the platform types table lists `nextflow-funnel-kubernetes` as available in local helm mode;
  * the access page mentions both demo platforms;
  * the interactive demo works with either.
* **Production notes:** add what stage 2 means for production to [spec 0010](0010-production-rollout.md).

**Decisions (Task 8):**

## Future Directions

* **Narrow Funnel's cluster-wide volume permission before production** (Task 1), in the AWS compute platform spec:
  * ask Funnel upstream for an option to skip the per-task volume, which would remove the permission. The same issue can ask Funnel to delete only the ServiceAccounts it created (Task 3: it deletes the one a task's `_WORKER_SA` tag names);
  * or a `ValidatingAdmissionPolicy` (built into Kubernetes 1.30+, available on EKS) that lets Funnel's service account touch only volumes labelled with its own namespace and storage location.
* **Widen what offline platforms can run** (Task 4: the Nextflow Job and task pods have no outgoing traffic, so workflows that download plugins or remote files fail, e.g. nf-core/demo's `test` profile):
  * pre-install common Nextflow plugins (nf-schema, nf-validation) in the Nextflow image. Each pipeline pins its plugin versions, so the image would follow the allowlist;
  * let WES stage allowlisted remote inputs (e.g. HTTPS URLs in `workflow_params`) before the run, as it stages DRS URIs. URLs inside samplesheets would still be out of reach, as DRS URIs are there today;
  * open specific destinations per connection through Task 6's outgoing-traffic allowlist. Locally Calico filters by IP range only (GitHub's ranges include its API, which could receive data); EKS Auto Mode can filter by domain name.
* **Count the engine's own core-hours** (Task 5) in the monthly quota and pooled budget, e.g. for the AWS platform, where a Nextflow Job's CPU costs money for the run's whole duration: a run's core-hours (its Job's CPUs × running time) added to its tasks'.
* TESK as a second executor for Kubernetes (discussion doc, Future Directions).
* **Federated analysis and learning across several refclouds** ([`claude/discussions/0001-about-federated-learning.md`](../../discussions/0001-about-federated-learning.md)), discussed 2026-11-16. Each refcloud simulates an organization that independently governs its data (dataset visas), its compute (platform visas, limits) and what runs (the allowlist). Two approaches:
  * **WES-coordinated federation:** our own coordinator submitting runs to each refcloud's WES;
  * **framework-coordinated federation:** a federated-learning framework (e.g. NVFlare) whose site clients are started through each refcloud's WES as long-running allowlisted runs, with the training code built into the client image, connecting out to the framework's server.

  Stage 2 keeps both open with per-release compute namespaces and the outgoing-traffic allowlist (Task 6).
