# Feature Spec: Production rollout for the end of 2026

**Branch:** _TBD_

**Linked GitHub Issue:** _TBD_

**Target:** in production **before the end of 2026**. The last working day before the break is **December 18th, 2026**, so aim to deploy by **December 14th–16th**, leaving time to watch production and fix problems before the break.

## Objective

Bring production (https://refcloud.ga4gh.org, on EKS Auto Mode) up to date in **one update**: everything merged since the current production release, in a single release tag and `helm upgrade`. Production is updated once, at the end, rather than after each branch.

**Done when:**

* production runs the new release;
* researchers can register, log in, get a passport token, request dataset access and use the GA4GH APIs as before;
* compute is available in production (if it made the cut, see Task 1): a researcher with approved visas can run the bundled demo through WES on the production compute platform;
* the post-deploy checks (Task 6) pass, and someone has watched production for at least a day before the break.

## Context

**What the release includes** (as of 2026-11-16; finalized in Task 1):

* **Compute, stage 1** ([spec 0008](../archive/0008-add-compute-wes-tes.md)): WES and TES APIs, Compute Platforms, the visa admin commands, and the researcher UI's compute pages. Its database migrations include **converting every timestamp column to UTC `timestamptz`**, whose production notes are under spec 0008's Decisions (Task 2).
* **Deployment hardening** ([issue 58](https://github.com/ga4gh/ga4gh-reference-cloud/issues/58)):
  * every Service is ClusterIP, and the ALB targets pod IPs;
  * NetworkPolicies, which need policy enforcement on in the cluster;
  * Kratos and Hydra run without dev mode, with Ory usage reports off;
  * **Kratos upgraded to `v26.2.0`**, in step with Hydra, with one-step registration;
  * Kratos logs at `info`.
* **The year-end compute work, as it lands** ([milestones](../../../docs/milestones/2026-12-18-Compute-Milestones.md)):
  * stage 2 (Nextflow + Funnel on Kubernetes, [spec 0011](../archive/0011-add-compute-nextflow-funnel-kubernetes.md)). Its compute namespace, Funnel and demo platform were **local only** then; the AWS platform (below) brings the namespace and Funnel to AWS, with a platform of its own. What does reach production:
    * core-api runs as its own ServiceAccount (`refcloud-api`) instead of the namespace's default, and with the **`Recreate` strategy**: every deploy or restart has a short gap (about 40 seconds) with no API, never two core-apis at once;
    * a Liquibase migration adds `compute_run.engine_job_uid` (nullable, with a check that a run has at most one engine identity; it has a rollback);
    * the compute connections file is mounted into core-api and the admin CLI; production's is empty (compute off) unless the AWS platform ships;
    * the core-api and admin CLI images build from the repo root (they bundle `compute/workflows/`), and core-api's includes `git`. CI's image workflow takes a Dockerfile path for them;
    * a new image, `refcloud-nextflow`, pushed by `push-docker-image-nextflow.yml` to an ECR repository of the same name (Task 2);
    * core-api's TES API refuses tag keys starting with `_` (Funnel acts on them);
  * stage 3 (S3 storage, [spec 0012](../archive/0012-add-compute-s3-storage.md)). RustFS, its setup Job, its data claim and its three Secrets are **local only** (`cloudEnvironment: local`), so production gets none of them; production uses Amazon S3 through the AWS platform's connection. What does reach production:
    * core-api's AWS SDK, upgraded from 2.25.15 to 2.57.8 through its BOM, with the JDK-based `url-connection-client` instead of Apache's (whose `httpcore5`, pinned by Spring Boot, had HIGH CVEs), plus the `sts` module. DRS's S3 reads use it too (checked against the public 1000 Genomes bucket);
    * new `s3` connection settings, and core-api's run storage layer. There's no schema migration: `compute_run.run_directory` and `workflow_path` hold `s3://` locations for S3 runs;
    * startup refuses an S3 connection whose runs could outlive their storage credentials. With AWS's one-hour limit, `max-run-duration-seconds` can be at most 3180 (45 minutes, 2700, is planned);
    * the `refcloud-nextflow` image adds nf-amazon 3.12.1, `curl`, and the S3 launcher `refcloud-run-s3`;
    * WES refuses `s3://` values in `workflow_params`;
    * Funnel 0.12.3 (in production only with the AWS platform);
  * the compute platform on AWS (EKS, [spec 0013](0013-add-compute-aws-platform.md)), **built and passed end to end on staging (2026-11-26)**. The chart runs compute on AWS from a block of values (`ga4gh.referenceCloud.compute.aws`), on AWS resources the infra repo's Terraform created for staging and production alike (Task 2). The platform is `nextflow-aws-us-east-2` ("Nextflow on AWS (us-east-2)"). What else reaches production:
    * core-api: the Nextflow Jobs' node selector, tolerations and SELinux level (connection settings), the run storage endpoint's name pinned in each Job's `/etc/hosts`, and STS requests that go to the connection's `sts-endpoint` or AWS's own, never to the storage endpoint;
    * the chart: the compute namespace's name as a value; Funnel on AWS (Pod Identity, no keys); workers that reserve their executors' CPU and memory; policies that work under EKS's policy agent;
    * the demo script works against any deployment (`REFCLOUD_DEMO_BASE_URL`), and tries `nextflow-aws-us-east-2`;
  * the researcher UI's My Runs page;
  * the docs update.

**What changes for users:** everyone logs in again once after the deploy (Hydra's cookie names change when it leaves dev mode). Accounts and passwords are kept: the Kratos upgrade was tested from `v0.9.0-alpha.2` with existing accounts and sessions, locally and then on staging's RDS (spec 0013, Task 3). Datasets and visas get their snake-case IDs (Task 6), e.g. `ga4gh_visa:aws_opendata_registry_1000_genomes`, and existing approvals move with them.

**Rehearsed on staging (2026-11-24, spec 0013, Task 3).** Staging went from the old chart to the current one with the steps below, and found what production would hit too. Those findings are written into the tasks below, marked "(staging)".

Production details (account IDs, CIDRs, ARNs, secret values) stay in the private values repo and the Terraform repo, never in this public repo.

## Engineering Tasks

### Task 1: Scope and release candidate (by December 11th)

* **Go/no-go on compute:** decide which year-end items ship.
  * If the AWS compute platform isn't ready and tested by December 11th, ship without it. Compute's APIs deploy, but no production platform is registered or enabled (the platforms page shows none).
  * Record what ships under Decisions.
* Merge every included branch into `main`, and check that `main` passes CI.
* **Rehearse in minikube (helm mode)** with the exact commit that will be tagged:
  * a fresh `./scripts/refcloud-setup.sh --reset`, then `./scripts/refcloud-run-helm.sh`;
  * the end-to-end checks of Task 6, as far as they apply locally.
* Cut the lockstep release tag (e.g. `v0.2.0`), and check that CI pushed every image to ECR under that tag.

**Decisions (Task 1):**

### Task 2: Infrastructure (Terraform)

Cluster-wide changes, in the Terraform repo, prepared ahead and applied as part of the rollout:

* **Network policy enforcement. Done 2026-11-20** (spec 0013, Task 1), for the whole shared cluster; no other namespace had policies. Staging showed it enforces, and that kubelet probes still get through (staging). Kept for reference: add the `amazon-vpc-cni` ConfigMap in `kube-system` with `enable-network-policy-controller: "true"`, through the Kubernetes provider ([AWS: Use Network Policies with EKS Auto Mode](https://docs.aws.amazon.com/eks/latest/userguide/auto-net-pol.html)). It applies to the whole cluster:
  * first, check whether it already exists (`kubectl -n kube-system get cm amazon-vpc-cni -o yaml`);
  * list other apps' policies (`kubectl get networkpolicy -A`): any outside the refcloud's namespace start being enforced too, so check they allow what those apps need;
  * keep the NodeClass default (`networkPolicy: DefaultAllow`).
* **Compute on AWS. Done 2026-11-25** (spec 0013, Tasks 4 and 6), for staging and production alike, so production's resources already exist, unused until the rollout. In the infra repo's Terraform:
  * `foundation`: an S3 gateway endpoint on the VPC's route tables, and the `ebs-gp3` StorageClass (encrypted gp3, `WaitForFirstConsumer`) for task workspaces and Funnel's database;
  * `reference-cloud`, per environment:
    * a run bucket (public access blocked, encrypted, TLS only; runs' files expire after 30 days);
    * three roles through EKS Pod Identity (no keys anywhere): core-api's, the run role (trusting only core-api's; each run's session narrowed to its prefix by core-api, for at most an hour), and Funnel's workers'. Their Pod Identity associations name the release namespace's `refcloud-api` and the compute namespace's `funnel-worker`;
    * an Auto Mode node pool for compute pods (tainted, on-demand `c`/`m` instances up to `xlarge`, removed 5 minutes empty), capped at 16 vCPU / 64 GiB for production. A shared node class keeps them in the private subnets, tagged `Project`/`Component`;
  * compute's private S3 endpoint, shared by both environments: an interface endpoint at fixed addresses, whose policy admits only the run buckets and the compute roles, and the buckets refuse the run and worker roles anywhere else. Workflow pods reach it with no DNS.

  Not built: a refcloud-only budget. It needs a cost-allocation tag only the Organization's payer account can activate; the node pools' caps bound the cost, and the account-wide budget stays (spec 0013, Task 4).
* **Logs:** check where the cluster's pod logs go and how long they're kept, and set a retention period.
* **The `refcloud-nextflow` and `refcloud-setup` ECR repositories. Done 2026-11-20** (spec 0013, Task 1). `refcloud-setup` is the setup Jobs' image (spec 0013, Task 2), which every deploy runs.

**Decisions (Task 2):**

### Task 3: Production values and secrets

In the private values repo, write production's values as staging's (staging):

* **Deploy from this repo's chart** (`deploy/helm/charts/app`), with `--kube-context`, `--namespace`, `--wait` and `--timeout 10m`, as the values repo's README does for staging. Production's section there still points at the old chart repo.
* `image.registry`, `image.tag` (the release tag) and `image.pullPolicy: IfNotPresent`. No pull secrets: the nodes pull from ECR with their own IAM role.
* **`networkPolicy.ingressFrom`: required on AWS.** Without it the chart fails to render. Set it to the VPC's public subnets, where the internet-facing ALB sits, not the whole VPC: pods get VPC addresses, so the whole VPC would let every pod in the cluster in (staging).
* **Service-info:** one shared `ga4gh.referenceCloud.serviceInfo` block (`idSuffix`, `environmentName`, `environment`, `organization`, `contactUrl`, `documentationUrl`), for every service. The chart refuses the old per-service `drs.serviceInfo`, `serviceRegistry.serviceInfo` and `compute.serviceInfo`, which production's values still have (staging).
* Drop `replicaCount`, `livenessProbe` and `readinessProbe`, which the chart doesn't read.
* **Secrets:** the setup Jobs check and create the chart's Secrets on every deploy (spec 0013, Task 2). The first deploy also needs `refcloud-db-bootstrap` (Task 4).
* **Compute** (if it ships): the same block as staging's (spec 0013, Task 8), with production's own values from the infra repo's `terraform output compute` (production's entry):
  * `ga4gh.referenceCloud.compute.namespace`: production's compute namespace, as the Terraform's `compute_environments` names it (its Pod Identity association names it);
  * `storageClass: ebs-gp3`;
  * `nodeSelector` and `tolerations`: production's compute node pool (`refcloud.ga4gh.org/compute`, value `prod`, effect `NoSchedule`);
  * `aws`: `bucket`, `region`, `runRoleArn`, `s3Endpoint` and `s3EndpointIps` from the output. The engine's size and the limits stay at the chart's defaults, as staging's (decided 2026-11-26: production mirrors staging to start with; its node pool allows more);
  * no connections of its own, no Secrets: core-api and Funnel's workers use Pod Identity. Compute's service-info comes from the shared `serviceInfo` block, and the auth instructions URL follows from `baseURL`.

  Written into production's values file at the rollout, together with the switch to this chart, not before (spec 0013, Task 9).
* Any value or Secret the year-end specs add. Check each spec's production notes, and `deploy/helm/README.md` (Remote Deployment).

**Decisions (Task 3):**

### Task 4: Before deploying

* **Read-only checks** (staging; the commands are in spec 0013, Task 3, decision 8):
  * how the release was applied (`kubectl get deploy -o json --show-managed-fields`): `helm/Apply` means server-side;
  * each Secret's keys (names only);
  * the old chart's connection strings, which name each database after its Secret's `username`, as the current chart does;
  * the databases, their owners and the roles (`\l`, `\du`);
  * that the master user has `ADMIN` on the refcloud's roles (`pg_has_role(current_user, <role>, 'MEMBER WITH ADMIN OPTION')`). The bootstrap needs it to grant them to `refcloud_admin`.
* **Snapshots** of the production databases: the refcloud database, Kratos and Hydra. Note the snapshot IDs privately; they're the rollback point. If the RDS instance is shared with other projects (staging's is), the snapshot covers them too. Rolling back then means restoring it as a new instance and copying the refcloud's databases back, not restoring over the live instance (staging).
* **The bootstrap login:** copy the RDS master user's login from Secrets Manager into `refcloud-db-bootstrap` (`deploy/helm/README.md`, "The bootstrap login"). The first deploy's `setup-predeploy` uses it to create `refcloud_admin` and `refcloud-db-admin` (spec 0013, Task 2, decision 4).
* **A dry run:** `helm upgrade ... --dry-run=server`, after the `Recreate` patch (Task 5). It simulates Helm 4's server-side apply without changing anything.
* **The UTC migration's checks** (spec 0008, Decisions (Task 2), production rollout notes):
  * run `SHOW timezone;`;
  * look at the visa rows' `current_status_at` (approvals were made with raw SQL, possibly with hand-typed local times), and fix outliers before upgrading.
* **Announce** a short maintenance window: migrations run before the new pods start, and everyone logs in again once.
* **Rollback plan:**
  * the previous release tag with its values, plus restoring the snapshots (the Kratos, timestamp and dataset ID migrations aren't reversible in place);
  * if only network policies cause trouble, `--set networkPolicy.enabled=false`.

**Decisions (Task 4):**

### Task 5: Deploy

* Apply Task 2's Terraform changes. Policy enforcement has no effect on the refcloud until its policies exist.
* `helm upgrade` with the release tag and the production values. The pre-upgrade hooks run `setup-predeploy` first, then the migrations (Liquibase, Kratos `v26.2.0`, Hydra); watch them finish. `setup-postdeploy` runs after the install. Their logs (`kubectl logs job/setup-predeploy`, `kubectl logs job/setup-postdeploy --all-containers`) should show, as staging's did:
  * the bootstrap: `refcloud_admin`, `refcloud-db-admin`, Data Connect's `data_connect_reader` role and its Secret created, and the app roles granted to `refcloud_admin`;
  * the Hydra client's redirect URIs updated: a client made for the old chart has others;
  * `refcloud seed` run. core-api's old pod stops before its new one starts (`Recreate`), so the API is down until the new pod is ready.
* **Before `helm upgrade`, switch core-api's Deployment to `Recreate` once** (spec 0011, Task 8). Production's Deployment was created with rolling updates, and Kubernetes filled in their default settings, which Helm's server-side apply can't remove, so the upgrade would be refused as invalid. Replacing the whole strategy restarts nothing:

  ```bash
  kubectl -n <namespace> patch deployment refcloud-api --type=json \
    -p '[{"op":"replace","path":"/spec/strategy","value":{"type":"Recreate"}}]'
  ```

  (`scripts/refcloud-run-helm.sh` does the same locally.)
* Helm 4 applies changes server-side: if anything in the release was changed by hand with `kubectl`, the upgrade refuses to overwrite it (a conflict). Check what changed and why before using `--force-conflicts`.
* Wait for every deployment to be ready.

**Decisions (Task 5):**

### Task 6: After deploying

* **The bootstrap login:** delete `refcloud-db-bootstrap`. If RDS rotates the master password (`aws secretsmanager describe-secret ... RotationEnabled`), rotate it once more to retire the copy. If not, leave it alone, since another project may use it (staging).
* **Data Connect:** `setup-postdeploy` runs `refcloud seed`, so existing table types pick up the UTC data model (spec 0008, Decisions (Task 2)). Check its log.
* **The datasets' and visas' IDs (staging):** production's still have the old dot-and-hyphen form, e.g. `aws.opendata.registry.1000-genomes`. The CLI only knows the snake-case IDs, and Data Connect can't register tables for the old ones (spec 0013, Task 3, decision 14):
  1. **rename them in place,** keeping every approval: the migration is in git (`git show d8c5faf:apps/admin-cli/migrations/0001-snake-case-dataset-ids.sql`). Run it from the helper pod as `refcloudapi`, with the password piped in on stdin, never in a command line. It's one transaction, and it skips datasets already renamed:

     ```sh
     kprod() { kubectl --context <prod context> --namespace <prod namespace> "$@"; }
     DB_HOST=$(aws rds describe-db-instances --db-instance-identifier <prod DB instance> --query 'DBInstances[0].Endpoint.Address' --output text)
     # as refcloudapi: the password on stdin's first line, then psql reads the SQL that follows
     refcloudapi_psql() {
       { kprod get secret refcloud-api-db-credentials -o jsonpath='{.data.password}' | base64 -d; echo; cat; } \
         | kprod exec -i deploy/<helper pod> -- bash -c 'IFS= read -r PGPASSWORD; export PGPASSWORD; exec psql -h "$1" -U refcloudapi -d refcloudapi -v ON_ERROR_STOP=1' _ "$DB_HOST"
     }
     echo "SELECT id FROM dataset ORDER BY 1; SELECT id, dataset_id FROM passport_visa ORDER BY 1;" | refcloudapi_psql   # before: old IDs
     git show d8c5faf:apps/admin-cli/migrations/0001-snake-case-dataset-ids.sql | refcloudapi_psql                        # NOTICE: migrated ... -> ...
     echo "SELECT id FROM dataset ORDER BY 1; SELECT visa_id, count(*) FROM passport_user_visa_assertion GROUP BY 1;" | refcloudapi_psql  # after
     ```

  2. **re-onboard both datasets,** so they get Data Connect tables and appear on the Explore Data page: `refcloud dataset reset -d <id> --yes`, then `refcloud dataset onboard -d <id>`, in the admin CLI pod, for `aws_opendata_registry_1000_genomes` and `ga4gh_refget_sequences_v2_conformance`. Onboarding is slow: run it in the pod with `nohup`, logging to `/tmp`.
* **Remove the helper pod** once the dataset steps are done: the admin CLI pod replaces it (spec 0013, Task 3, decision 3).
* **End to end, in a browser:**
  * register a new account (one screen);
  * log in;
  * open View Passport Token;
  * request access to a dataset, and approve it with `kubectl exec deploy/refcloud-admin-cli -- refcloud visa approve ...`;
  * use DRS with the token;
  * on the Explore Data page, both datasets and the platform table load.
* **Deployment checks:**
  * every pod is ready and stays ready;
  * `kubectl get svc` shows only ClusterIP Services;
  * `kubectl get networkpolicy` lists the refcloud's policies;
  * from a throwaway pod, the Kratos and Hydra admin APIs time out (`deploy/helm/README.md`, Network Policies);
  * the service registry lists every service, with health, each with production's ID suffix, environment name and the release tag as version.
* **Compute** (if it ships), as staging did (spec 0013, Task 8; the commands are in `deploy/helm/README.md`, Compute on AWS):
  1. register and enable `nextflow-aws-us-east-2`, "Nextflow on AWS (us-east-2)", on the connection `nextflow-funnel-kubernetes-aws`, with a monthly cap of 10 core-hours per researcher and `drs-checksum` on its allowlist; its WES service-info reports it healthy;
  2. approve a test account's visas (the platform and 1000 Genomes);
  3. run the demo: `REFCLOUD_DEMO_BASE_URL=https://refcloud.ga4gh.org ./scripts/refcloud-compute-demo.sh <token>`. A run's first task waits a minute or so for compute nodes;
  4. its outputs download through WES; its files are under its own prefix in production's bucket;
  5. the compute nodes come and go (`kubectl get nodes -l refcloud.ga4gh.org/compute=prod`: none some 10 minutes after the last run), tagged `Project=reference-cloud` and `Component=compute`;
  6. the kill switch: `refcloud compute-platform disable` hides the platform (its WES service-info `404`); enable it again.
* **Docs:** the docs site says compute is available in production (today it says local only), and describes the production platform.

**Decisions (Task 6):**

### Task 7: Over the break (by December 18th)

* Watch production for at least a day after the deploy: errors, pod restarts, costs.
* **Compute over the break:** keep platform access approval-only, approve nothing while nobody is watching, and know the kill switch: `refcloud compute-platform disable` in the admin CLI pod.
* Write down where things stand: what shipped, the snapshot IDs (privately), and the rollback steps.

**Decisions (Task 7):**

## Future Directions

* ~~A staging environment (a second namespace or cluster with production-like settings), so a rollout like this one is rehearsed against AWS rather than only minikube.~~ Done: staging, a namespace on the same cluster, runs the current chart and rehearsed this rollout (spec 0013, Task 3, 2026-11-24).
