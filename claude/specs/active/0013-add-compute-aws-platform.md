# Feature Spec: Add compute on AWS: the refcloud's compute platform on EKS

**Branch:** `ja-iss-64-compute-on-aws`

**Linked GitHub Issue:** [Issue 64](https://github.com/ga4gh/ga4gh-reference-cloud/issues/64)

## Objective

Run compute (WES/TES) on the refcloud's AWS cluster. That means the `nextflow-funnel-kubernetes` platform type (stage 2, [spec 0011](../archive/0011-add-compute-nextflow-funnel-kubernetes.md)) with Amazon S3 storage (stage 3, [spec 0012](../archive/0012-add-compute-s3-storage.md)) on EKS, fully working and tested in staging before production. This is item 3 of the [milestones](../../../docs/milestones/2026-12-18-Compute-Milestones.md). AWS means EKS with Funnel's Kubernetes backend (decided 2026-11-13).

An AWS compute platform is a new connection, not new code (D14, D15). What's new is around it:
* the AWS resources, in the team's private Terraform repo: S3 buckets, IAM roles through EKS Pod Identity, a node pool for compute pods, an EBS storage class, a route to S3 and a budget;
* the chart's compute pieces (the compute namespace, Funnel, network policies, quota). Today they render only locally;
* network policies that work with EKS Auto Mode's networking.

Staging and production are two namespaces in one shared EKS Auto Mode cluster. This spec builds and tests compute in staging, and creates production's AWS resources too. Production itself is updated once, at the end ([spec 0010](0010-production-rollout.md)).

**Done when:** in staging, a researcher with approved visas for the staging demo platform and the 1000 Genomes dataset can:

1. submit the bundled demo workflow through WES, with a 1000 Genomes `.bai` DRS object as input;
2. watch it run on the compute node pool, with its inputs, work directory and outputs in staging's bucket;
3. download its report through the output download endpoint.

Also:
* `./scripts/refcloud-compute-demo.sh` passes against staging;
* the isolation checks hold on EKS:
  * a run's credentials reach only its own files;
  * workflow pods can resolve and reach only what they need;
  * the compute namespace denies everything else;
* production's AWS resources exist, and spec 0010 lists exactly what's left for the rollout.

## Context

**Source of truth:** the discussion doc (D1–D23), the Decisions sections of specs 0008, 0011 and 0012, and spec 0010's production notes. Spec 0012 left these to verify on EKS (milestones, Dependencies and Risks):
* per-task workspace claims on EBS;
* EKS Pod Identity for core-api and Funnel's workers;
* DNS and a route to S3 for the Nextflow Jobs.

**How we work on AWS (decided 2026-11-20):**
* **Staging is where this is built and tested.** It's its own namespace in the shared cluster, deployed by hand with `helm upgrade` and a values file in the team's private values repo.
* **The user runs every command against AWS or the cluster** (`aws`, `kubectl`, `helm`, `terraform`). Each task's steps list those commands for the user.
* **Terraform lives in the team's private infra repo:**
  * `foundation` holds shared resources (the VPC, the cluster);
  * `reference-cloud` holds the refcloud's own.

  Every change is reviewed before it's written, and the user runs `plan` and `apply`.
* **Nothing environment-specific goes in this public repo:** account IDs, ARNs, CIDRs, bucket names and hostnames stay in the private repos (spec 0010).

**Found on 2026-11-20** (read-only checks of the cluster and the private repos):
* **The cluster** is EKS Auto Mode (Kubernetes 1.35) with only the built-in `general-purpose` node pool (amd64). Pod Identity and EBS volumes are built into Auto Mode, so there's no add-on to install.
* **No storage class can provision new volumes.**
  * The only one is the legacy `gp2`: the in-tree `kubernetes.io/aws-ebs` provisioner, and not the default.
  * Auto Mode provisions EBS through `ebs.csi.eks.amazonaws.com`.
  * So Funnel's per-task claims and its database claim need a new storage class.
* **No network policies exist or are enforced.** The Network Policy Controller's ConfigMap isn't there, and `kubectl get networkpolicy -A` finds nothing.
* **The cluster has no CoreDNS pods and no `kube-dns` Service.**
  * Auto Mode runs CoreDNS on every node, at the `.10` address of the cluster's service range ([AWS: network policies with Auto Mode](https://docs.aws.amazon.com/eks/latest/userguide/auto-net-pol.html)).
  * The compute namespace's DNS rule selects `k8s-app: kube-dns` pods in `kube-system`, so on EKS it would match nothing and block DNS.
  * On AWS the rule must allow that address instead.
* **Auto Mode supports DNS-based network policies.** The cluster has the `ApplicationNetworkPolicy` and `ClusterNetworkPolicy` CRDs (`networking.k8s.aws/v1alpha1`).
  * An `ApplicationNetworkPolicy` egress rule can name destinations by domain (`domainNames`).
  * A proxy on the node checks the pod's DNS lookups against the allowed names, and lets the pod reach only the addresses those names resolve to.
  * That could let the Nextflow Jobs reach S3 by name, while lookups of other names are still refused. Refusing those lookups is why workflow pods get no DNS today (spec 0011).

  To verify in Task 5.
* **The VPC has no endpoints at all,** S3 gateway included, so S3 traffic from the private subnets goes through the NAT gateways.
* **The AWS account:**
  * it has no Pod Identity associations yet;
  * it has one budget, a monthly budget for the whole account;
  * it's a member of an AWS Organization. Cost allocation tags, which a budget for compute alone would filter on, are activated from the organization's management account.
* **Staging still runs the pre-monorepo chart** (`ga4gh-reference-cloud-helm` 0.1.0, last upgraded in July) and pre-monorepo images.
  * Its values file predates the current chart: it has no `image.registry`, `image.tag` or `networkPolicy.ingressFrom`.
  * So staging has none of this year's changes: specs 0004–0012's chart, the Kratos upgrade (staging runs `v0.9.0-alpha.2`), and the UTC and compute migrations.
  * Task 3 brings it up to date, which also rehearses spec 0010's rollout on AWS.
* **Staging lacks a Secret and a database role, and spec 0010's checklist doesn't mention them either:**
  * the `refcloud-api-data-connect-db-credentials` Secret;
  * the `data_connect_reader` role, which its Liquibase grants need. Locally, `refcloud-setup.sh` creates it.
* **Staging and production use the same release name.** The chart names the compute namespace `<release name>-compute`, so both would claim the same namespace.
* **Images reach ECR only from `develop`.** The push workflows run only there, and each build also moves the `develop` tag. The `refcloud-nextflow` repository doesn't exist yet.
* **Nodes seem to pull from ECR with their own IAM role.** Staging's current node pulled its images a few days ago, although its image pull secret is months old and ECR tokens last 12 hours. If so, the values need no `image.pullSecrets` (to confirm in Task 3).
* **Both AWS SDKs can use Pod Identity:**
  * core-api's run storage uses the SDK's default credential chain when a connection names no keys (`RunStorages.credentials`), and that chain includes Pod Identity;
  * DRS's S3 reads stay anonymous (`AwsS3ObjectReader`), so core-api's new role doesn't change them;
  * Funnel 0.12.3 uses the AWS SDK for Go v2 (`config` v1.33), whose default chain includes Pod Identity too.
* **The Nextflow Job's run URL on AWS is virtual-hosted** (`https://<bucket>.s3.<region>.amazonaws.com/...`, `NextflowKubernetesEngine`), so the Jobs need to resolve exactly that name.

**Decided (2026-11-20):**
1. **Build and test in staging,** in the shared cluster (see above).
2. **Branch images:** the user adds this branch to the image workflows' triggers while the branch is open, and removes it before merging.
3. **Turn on the Network Policy Controller now,** not at the rollout. No namespace has policies, so nothing changes until the chart's policies land in staging.
4. **Write the Terraform for staging and production now,** and apply both. Production's resources stay unused until the rollout.

**Proposed design** (each part settled in its task):
* **Per environment:**
  * a bucket;
  * the three IAM roles spec 0010 lists (core-api's, the run role, Funnel's workers'), through Pod Identity;
  * the compute namespace, named after the environment through a new chart value.
* **Shared by both:** the S3 gateway endpoint, the EBS storage class and the Network Policy Controller, which are all cluster-wide.
* **Either shared or per environment** (Task 4 decides): the compute node pool and the budget.
* **The chart defines the AWS connection** from a small block of values (bucket, region, run role, limits), as it defines the local one (`refcloud.localComputeConnection`).
  * The connections file, the quota, the LimitRange and Funnel's config then all follow from one place.
  * Empty values mean compute is off, which is how production ships if compute isn't ready (spec 0010).
* **Compute pods run on their own node pool.** It's tainted so nothing else lands there, and its limits on total CPU and memory are the hard cost cap.

## Engineering Tasks

### Task 1: Images and cluster prerequisites

* **Images:**
  * the user adds this branch to the push workflows' triggers (temporarily);
  * only `develop` builds move the `develop` tag (`push-docker-image.yml`), so branch builds don't;
  * Terraform (`reference-cloud`): the `refcloud-nextflow` and `refcloud-setup` (Task 2) ECR repositories and their CI push policies.
* **The Network Policy Controller** (Terraform, `foundation`): the `amazon-vpc-cni` ConfigMap, as spec 0010 planned. Afterwards, check that nothing changed for other namespaces (none have policies).

**Decisions (Task 1):**

1. **Only `develop` builds move the `develop` tag.** Done 2026-11-20: in `push-docker-image.yml`, the tag line is empty for other branches, and `.github/workflows/README.md` lists the tags. Workflow checks pass (actionlint, zizmor).
2. **Terraform written and applied 2026-11-20** (the user ran `plan` and `apply`; after the Argo CD removal below, no Argo CD resources remained in either refcloud namespace):
   * `reference-cloud`: `refcloud-nextflow` and `refcloud-setup` in `ecr_repos`, their push policies, and their attachments to the monorepo's CI role. The attachments now take the policy's ARN from the policy resource (the same ARN), so a new policy is created before it's attached. Expected plan: 6 to add, nothing else;
   * `foundation`: the `amazon-vpc-cni` ConfigMap (`kubernetes_config_map_v1`). Expected plan: 1 to add, nothing else. Its `helm` provider uses the current kube context, so the EKS cluster's must be current.
3. **`foundation` no longer has Argo CD, or a `helm` provider** (2026-11-20). The first `foundation` apply ran from the `minikube` context (the local run script switches to it). The `helm` provider followed that context, and installed a new Argo CD into minikube. Argo CD wasn't used for deployments, so the user had it removed:
   * Terraform: its release, certificate, validation records, DNS record and data source, and the `helm` provider, which only it used. So `foundation` now reaches the cluster only through its `kubernetes` provider, which targets EKS explicitly, and no longer depends on the kube context;
   * the release, namespace and CRDs: removed from minikube by me, and from EKS by the user with `helm` and `kubectl`. Terraform's state no longer recorded the release after the failed apply.

### Task 2: A setup Job for any environment

`refcloud-setup.sh` prepares a local environment. Remote environments have so far been prepared by hand, from a helper pod. Instead, for a deploy that works in one step, the chart runs setup itself: Jobs that check each resource and create it, or update it in place. Unlike the script, there's no `--reset`, and the Jobs never delete anything. When everything is intact, they change nothing.

What `refcloud-setup.sh` does, and where each step goes remotely:

| Step | Remotely |
|-|-|
| databases `kratos`, `hydra` and `refcloudapi`, each owned by a login role of the same name | the setup Job, before the migrations |
| the `data_connect_reader` role (read-only, 30-second statement timeout) | the setup Job, before the migrations |
| the Secrets the chart expects | the setup Job, before the migrations: creates missing ones with random values, and never changes an existing one |
| Liquibase migrations | already a chart hook |
| the researcher UI's OAuth client in Hydra | the setup Job, once Hydra is up |
| `refcloud seed` (upserts) | the setup Job, at the end, after Hydra's client |
| onboarding datasets (slow; existing datasets are skipped) | a documented next step after a first deploy, with the admin CLI pod |
| compute platforms | admin CLI commands (Task 8) |
| tools, minikube, the TLS certificate, Docker Compose | local only |

**Decisions (Task 2):**

Agreed 2026-11-20:
1. **Shell scripts, not the admin CLI.** Setup deploys the refcloud's own infrastructure; the admin CLI stays scoped to what an admin data custodian does.
2. **Its own image, `refcloud-setup`:** Alpine with `psql`, `curl` and `jq`, plus the script. No single official image has all three, and the restricted security context rules out installing tools when the Job starts. It's built from `deploy/helm/setup/` with the lockstep tag, pushed by its own CI workflow, and built into minikube by the run script. Its ECR repository is in Task 1's Terraform.
3. **Two Jobs, `setup-predeploy` and `setup-postdeploy`,** each a step of the same script (its argument: `predeploy` or `postdeploy`). A Helm hook runs at one point in a deploy, so one Job can't do both: before the migrations, Hydra doesn't exist yet on a first install; after the install, the migrations would already have failed for lack of databases.

   | Order | Resource | Hook (weight) | Does |
   |-|-|-|-|
   | 1 | `setup-predeploy`'s ServiceAccount, Role and RoleBinding | pre-install, pre-upgrade (-20) | let it read and create Secrets. Hooks too, since the release's own resources don't exist yet; deleted once every pre hook succeeds, so nothing keeps that permission between deploys |
   | 2 | `setup-predeploy` | pre-install, pre-upgrade (-10) | Secrets, databases and roles |
   | 3 | the Kratos, Hydra and Liquibase migrations | pre-install, pre-upgrade (0, as now) | unchanged |
   | 4 | the release's resources | | Helm applies them |
   | 5 | `setup-postdeploy` | post-install, post-upgrade (0) | first container (`refcloud-setup` image): waits for Hydra's admin API to be ready (as `rustfs-setup` waits for RustFS), then the Hydra client. Last container (admin CLI image, the admin CLI pod's settings): `refcloud seed`. Reads Secrets only as environment variables, so it needs no Kubernetes permissions |

   If `setup-predeploy` fails, Helm stops before the migrations, so on an upgrade nothing running changes. If `setup-postdeploy` fails, the release is marked failed after its resources are applied; running the upgrade again repeats both, and `setup-predeploy` finds everything intact.
4. **Database admin credentials: a dedicated `refcloud_admin` login, created with a one-time bootstrap login** (revised 2026-11-20; at first, the master credentials in `refcloud-db-admin`). Staging and production each have their own RDS instance (Postgres 18.3), and set no master password in Terraform. With the `terraform-aws-modules/rds` module, that normally means RDS manages it in Secrets Manager and rotates it (every 7 days by default; to confirm with `describe-db-instances` and `describe-secret`), so a copy in a Kubernetes Secret would go stale. Instead:
   * `setup-predeploy` connects as the admin login in `refcloud-db-admin`. On AWS, that's `refcloud_admin`: `LOGIN CREATEDB CREATEROLE`, not a superuser. On Postgres 16 and later, it manages only the roles it creates or is granted, so not the master user or other projects' roles. Its password is random and lives only in that Secret. Locally, `refcloud-setup.sh` creates the Secret from the local Postgres login;
   * **bootstrap:** for an environment's first deploy, the operator copies the master user's login from Secrets Manager into `refcloud-db-bootstrap` (one command, so the password never shows in the terminal). Whenever that Secret exists and can connect, `setup-predeploy` connects as the master user. It creates or repairs `refcloud_admin` (its password, its attributes, and `ADMIN` on every app role) and creates `refcloud-db-admin`. The operator then deletes the copy, and can rotate the master password, which setup never needs again. A copy that can't connect (rotated) is ignored, with a reminder to delete it;
   * the same step repairs a lost `refcloud-db-admin`, or an app role `refcloud_admin` can't manage;
   * rejected: Pod Identity reading the master password from Secrets Manager on every deploy (the Job could always get the master password); Terraform's `postgresql` provider (RDS is in private subnets, and the password would be in the state); copying the master password before each deploy.
5. **Setup doesn't onboard datasets.** The docs give it as the next step after a first deploy, with the admin CLI pod.
6. **Helm mode runs the Jobs locally too,** against host Postgres. `refcloud-setup.sh` has already prepared everything, so they report every check passing and change nothing, which also confirms the two agree. `refcloud-setup.sh` keeps `--reset` and native mode.
7. **"Intact" means these checks, and only a failing one changes anything.** The script logs each check.

   | Check | If it fails |
   |-|-|
   | a Secret is missing | create it with random values, except the three below |
   | `kratos-secrets`, `hydra-secrets` or `refcloud-ui-hydra-researcher-client` is missing, and its database already has tables | stop with an error saying to restore it. Kratos and Hydra encrypt stored data with the first two, and Hydra keeps the client's secret, so a new random value would break what's stored. Each is created only with its new database |
   | a Secret is missing a key | stop with an error rather than guess. Existing Secrets are never changed |
   | a database is missing, or has the wrong owner | create it, or fix its owner. Each app's database and role are named after its Secret's `username`, as the chart's connection strings are |
   | a role is missing | create it with its Secret's password |
   | a role can't log in with its Secret's password | set the role's password from the Secret |
   | the reader role isn't read-only, lacks its 30-second statement timeout, or can create databases or roles | set them, or remove them. A reader that's a superuser stops with an error: only a superuser can change that, and RDS's master user isn't one |
   | the Hydra client doesn't exist, or the settings the chart decides differ | create it from its Secret, or update those settings (revised 2026-11-24, see below; at first, only its existence was checked) |
   | `refcloud seed` | always runs. It upserts the Data Connect table types and platform tables that ship with each admin CLI version, so it's the one step that rewrites rows (their `updated_at`) when nothing has changed |
8. **The Jobs follow the chart's rules:** the restricted security context; a network policy rule so Hydra's admin API accepts `setup-postdeploy`; pre-install hooks depend only on Secrets that exist before the release (`setup-predeploy` mounts none: it reads them through the Kubernetes API, so it starts and explains what's missing); Kubernetes permissions limited to reading and creating the release namespace's Secrets.

Implemented 2026-11-20:
* **Files:**
  * the image: `deploy/helm/setup/Dockerfile` (Alpine 3.24.4: bash, curl, jq, psql 18; user 10001) and `deploy/helm/setup/refcloud-setup` (bash);
  * the Jobs: `charts/app/templates/apps/setup/predeploy.yaml` (ServiceAccount, Role, RoleBinding and Job) and `postdeploy.yaml`; Hydra's admin rule in `network-policies.yaml`;
  * CI: `push-docker-image-setup.yml`. Static analysis: a `shellcheck` check in `refcloud-static-analysis-helm.sh` (and its CI step), and the setup Dockerfile in `refcloud-static-analysis-deploy-local.sh`'s hadolint and Trivy checks (and its CI path filter);
  * local scripts (`scripts/lib/common.sh`, `refcloud-run-helm.sh`): the image in `REFCLOUD_IMAGES`; `refcloud-db-admin` in `K8S_SECRETS`, created by `apply_k8s_secrets`; the run script prints the Jobs' last lines; `--down` and `--reset` delete the Jobs (`delete_setup_jobs`);
  * docs: the helm README (a "Setup Jobs" section, Secrets, the run steps, static analysis), `deploy/helm/CLAUDE.md` and the root `CLAUDE.md`.
* **Choices made while implementing:**
  * the Jobs keep only `before-hook-creation`, so they and their logs stay until the next deploy; the RBAC hooks also have `hook-succeeded`, so they're gone once the pre hooks have run;
  * on Postgres 16 and later, an admin that isn't a superuser (RDS's master user) can make a role a database's owner only after granting itself the role, so setup does that when it's needed, and logs it as a change;
  * apps read Secrets only when they start, so when predeploy changes anything it says to restart the apps that use what changed;
  * secrets never appear in process arguments: the token reaches curl through a config file descriptor, values go through stdin or the environment (psql's `\getenv`), and names go as psql variables;
  * the RustFS accounts and `refcloud-tls` stay local-only: `refcloud-setup.sh` creates them, and predeploy doesn't check them;
  * locally, `refcloud-db-admin` is the Postgres user setup's own `psql` commands connect as, with `PGPASSWORD` if it's set (local Postgres usually trusts `127.0.0.1`);
  * the bootstrap (decision 4): the Role reads `refcloud-db-bootstrap` too. With the bootstrap login, predeploy runs every check as the master user, then grants each app role to `refcloud_admin` `WITH ADMIN OPTION`. With either login, it stops with an error naming any existing app role the login can't manage. On Postgres 16 and later that needs `ADMIN` on the role; before 16, `CREATEROLE` was enough.
* **Tested 2026-11-20** in minikube, in a throwaway namespace with its own Postgres 17 and an admin like RDS's master user (not a superuser, with `CREATEROLE` and `CREATEDB`). Everything was deleted afterwards:
  1. **A first install from nothing:** predeploy created 9 Secrets, 3 databases and 4 roles, and granted itself the 3 app roles. The migrations ran next, and postdeploy created the Hydra client and ran the seed. The RBAC hooks were gone afterwards. The install took 44 seconds;
  2. **an upgrade with nothing changed:** predeploy's 23 checks and postdeploy's 1 passed, and nothing changed;
  3. **after breaking things on purpose** (a wrong role password, the reader's timeout removed and `CREATEDB` added, a database's owner changed, the Hydra client deleted), the next upgrade fixed all five;
  4. **errors:** each of these stopped predeploy with a message saying what to do. `kratos-secrets` missing while Kratos's database has data; a Secret missing a key; no `refcloud-db-admin`; a wrong admin password. Once restored, it changed nothing;
  5. **static analysis passes:** helm (lint, kubeconform, Trivy, shellcheck), the Dockerfile checks (hadolint, Trivy) and the workflows (actionlint, zizmor);
  6. **the bootstrap (decision 4),** in a new throwaway namespace and Postgres 17, with a stand-in for the master user (not a superuser, `CREATEROLE` and `CREATEDB`):
     * a first install with only `refcloud-db-bootstrap` created `refcloud_admin` (not a superuser), its Secret and everything else, and granted it the 4 app roles;
     * once the copy was deleted, deploys connected as `refcloud_admin`, changed nothing, and fixed a broken app password and a database's owner on their own;
     * these were each repaired by a deploy with a bootstrap copy: a broken `refcloud_admin` password (without a copy, the error names the fix), and an app role it no longer managed;
     * a stale copy was ignored, with a reminder to delete it. A valid leftover copy changed nothing;
     * a final fresh install, then an upgrade without the copy, changed nothing.
* **Local helm mode, against host Postgres (the user, 2026-11-24):** after `./scripts/refcloud-setup.sh` (which creates `refcloud-db-admin`) and `./scripts/refcloud-run-helm.sh`, `setup-predeploy` passed its 23 checks and the Hydra client check passed, with nothing changed; the seed ran. `kubectl logs job/setup-postdeploy` shows only the `seed` container: add `--all-containers` (or `-c hydra-client`) to see the Hydra check too.
* **CI built the image** (2026-11-24, `v0.1.0-66-g6268e39`), with this branch in the image workflows' triggers.
* **Revised 2026-11-24: `setup-postdeploy` updates an existing Hydra client's settings** (the user chose this over deleting and recreating it by hand). Staging's first deploy on the new chart (Task 3) found its hand-made client with redirect URIs the new chart doesn't use, which the existence-only check left as they were. Now:
  * the settings the chart decides (`hydra_client_settings`): the redirect URI (from `baseURL`), the grant and response types, the scope and the auth method, matching `scripts/refcloud-setup.sh`;
  * where an existing client differs, those fields alone are updated, with a JSON Patch (`PATCH /admin/clients/<id>`). Anything else set on the client stays, as does its secret, which still isn't checked (Hydra never returns it). A `PUT` would have reset the rest;
  * lists and the scope compare as sets, so order doesn't count as a difference. Each updated field is logged with its old and new values, and the client is read back to confirm;
  * **tested** with the setup image against a throwaway in-memory Hydra `v26.2.0`:
    * a missing client was created, and a rerun changed nothing;
    * a client like staging's (an old redirect URI, plus `skip_consent` set by hand) got only its redirect URI updated, and `skip_consent` stayed;
    * the same values in another order changed nothing;
    * three differing fields were updated in one patch;
    * the secret still authenticated after the update (`unauthorized_client` for an unallowed grant, against `invalid_client` for a wrong secret). shellcheck and the helm checks pass.

### Task 3: Staging on the current chart, without compute

Bring staging up to this branch's chart and images with compute off, as spec 0010 will do for production. This gives compute a working base, and rehearses the rollout on AWS.

* **Staging's values** (private values repo):
  * `image.registry`, `image.tag` (this branch's build), `image.pullPolicy: IfNotPresent`, and no pull secrets;
  * `networkPolicy.ingressFrom`: the VPC's CIDR;
  * the setup Job's settings (Task 2);
  * the service-info settings (DRS, service registry, compute);
  * drop the old chart's values;
  * the values repo's README: deploy from this repo's `deploy/helm/charts/app`.
* **Secrets:** create `refcloud-db-bootstrap` from staging's master user secret (the helm README's "The bootstrap login"), and check that the existing Secrets have the keys the chart expects (key names only). `setup-predeploy` creates `refcloud_admin` and `refcloud-db-admin`, grants it staging's existing roles, and creates what's missing, including the reader role's Secret. Then delete `refcloud-db-bootstrap`, and rotate the master password only if RDS already rotates it (decision 8).
* **Before deploying** (spec 0010, Task 4): a snapshot of staging's database, the UTC migration's checks, and the one-time `Recreate` patch.
* **Deploy** with `helm upgrade`, and watch the hooks: `setup-predeploy`, then Liquibase, Kratos (from `v0.9.0-alpha.2` to `v26.2.0`) and Hydra, and `setup-postdeploy` after the install.
* **Check** (spec 0010, Task 6, without compute):
  * register, log in, view the passport token, request and approve a visa, use DRS;
  * every pod is ready, kubelet probes included, now that the policies are enforced;
  * the network policy checks.
* **Remove the helper pod** (see Decisions).
* **Write back to spec 0010** whatever its rollout missed: the reader role, and the setup Job.

**Decisions (Task 3):**

Agreed 2026-11-20:
1. **Staging goes onto the current chart first,** with compute off, before any compute work.
2. **Snapshot staging's database before the deploy,** as spec 0010 plans for production.
3. **The helper pod goes once the admin CLI pod runs.** Staging and production each have a hand-made helper pod, used for one-off commands from before the admin CLI was in the chart. It's removed once the chart's admin CLI pod runs: in staging after this task's deploy, and in production at the rollout (spec 0010).

Agreed 2026-11-24:
4. **Images:** CI's build of the branch commit the user pushes (the seven image workflows have this branch in their triggers), with its lockstep tag in staging's values.
5. **Service-info:** the service registry's and compute's follow DRS's pattern in staging's values (IDs ending in `.staging`, `environment: staging`, the same contact).
6. **`networkPolicy.ingressFrom`: only the VPC's public subnets,** where the internet-facing ALB sits (its ingress class sets `scheme: internet-facing`), rather than the whole VPC. On EKS, pods get VPC addresses, so the whole VPC would let every pod in the cluster reach the refcloud's UI, API and Ory public ports. The CIDRs are in the private values repo. This depends on the node's own traffic getting through policies, for kubelet probes, which AWS doesn't document, so it was tested first:
   * **probe test (the user, 2026-11-24):** in a throwaway namespace on staging's cluster, a pod behind a deny-all ingress policy stayed ready on its HTTP readiness probe (106 seconds), while another pod's request to it timed out. So enforcement is on, and kubelet probes pass, as with Calico locally. Kratos's and Hydra's probes on their admin ports, which no rule opens, will work. Task 5's spike can rely on it too;
   * if some nodes run in the public subnets (the cluster's subnets include them), pods on them would also be inside the range. That would weaken the isolation, without breaking anything. **Checked 2026-11-24:** the cluster's only node is in a private subnet. Auto Mode's default node class may still pick a public subnet for a later node; Task 4's node class for compute can select private subnets only.
7. **The values repo's README** deploys staging from this repo's `deploy/helm/charts/app`, with `--kube-context`, `--namespace`, `--wait` and `--timeout 10m`, so the command never depends on the current kubectl context. Production's section stays on the old chart until the rollout.
8. **Pre-flight checks on staging (the user, 2026-11-24; read-only):**
   * **Secrets:** every one the chart expects exists, with its keys, except Data Connect's reader's. `setup-predeploy` creates that, and the `data_connect_reader` role, which doesn't exist yet;
   * **databases:** the old chart's connection strings already name each database after its Secret's `username`, as the new chart does. `kratos`, `hydra` and `refcloudapi` exist, each owned by its role, so setup has no owners to change;
   * **UTC:** the database's time zone is UTC, and the two visa rows' `current_status_at` look right, so the timestamp migration needs no fixes;
   * **the RDS instance is shared:** it also holds other projects' databases and roles. That's what `refcloud_admin`'s limited reach is for (Task 2, decision 4). It also means the bootstrap needs the master user to have `ADMIN` on the refcloud's roles, checked with a query before the deploy. And the master password is rotated afterwards only if RDS already rotates it: another project might connect with the master login. **Checked 2026-11-24:** RDS rotates it, so one more rotation after the bootstrap retires the copy that was in `refcloud-db-bootstrap`.
9. **Staging's values drop** `replicaCount`, `livenessProbe` and `readinessProbe`, which the new chart doesn't read. A render with the draft values gives 30 resources, valid against Kubernetes 1.35 (kubeconform): every Service ClusterIP, both setup Jobs and the migrations, the policies, and nothing for compute.

Found by the deploy (2026-11-24), both of which production's rollout (spec 0010) would have hit too:

10. **The bootstrap worked as planned on RDS** (`v0.1.0-66-g6268e39`), connected as the master user. Its changes:
    * it created the reader's Secret, `refcloud-db-admin`, `refcloud_admin` (`CREATEDB CREATEROLE`) and `data_connect_reader` (read-only, with the 30-second timeout);
    * it granted the four app roles to `refcloud_admin`, `WITH ADMIN OPTION`;
    * every existing role logged in with its Secret's password, and every database already had the right owner.
11. **The hand-made Hydra client had redirect URIs the new chart doesn't use,** and the existence-only check kept them. `setup-postdeploy` now updates the settings the chart decides (Task 2, revised 2026-11-24). The redeploy with `v0.1.0-67-g538762b` fixed staging's client, and the passport token page then worked.
12. **core-api couldn't validate tokens on AWS** (Data Connect's queries failed with a 500: `I/O error on GET request for "http://127.0.0.1:4444/.well-known/jwks.json": Connection refused`):
    * since the chart's first version, it set core-api's signing-key URL (`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`, `http://hydra:4444/...`) only when `cloudEnvironment` is `local`. Elsewhere, core-api fell back to `application.yml`'s native-mode default, `127.0.0.1:4444`;
    * it's now set in every environment. Hydra's policy already accepts core-api on 4444;
    * every other native-mode default in `application.yml` (the database, issuer, CORS, Kratos, DRS, Data Connect, the service registry and compute's URLs) has an override in every environment. This was the only gap;
    * the change is only in the chart, so it's redeployed from the checkout with the same images. Confirmed on staging: the Explore Data page's platform table loads.
13. **Datasets onboarded before per-dataset Data Connect tables existed don't appear on the Explore Data page.** Its dataset dropdown lists only datasets with a Data Connect table the user can read, and `dataset onboard` is what registers them. Staging's 1000 Genomes was onboarded in July, so it had none. The fix, with the admin CLI pod:
    * `refcloud dataset reset`, which keeps the dataset, its tags and its visas, so approvals survive;
    * then `refcloud dataset onboard`.

    Production's datasets will need the same after the rollout. For spec 0010, that's alongside `refcloud seed`, which `setup-postdeploy` now runs itself.
14. **Staging's datasets and visas still had their old dot-and-hyphen IDs**, e.g. `aws.opendata.registry.1000-genomes` and `ga4gh-visa:...`. Spec 0002 (Task 5) moved to snake case (`aws_opendata_registry_1000_genomes`, `ga4gh_visa:...`), because Data Connect table names can't contain hyphens:
    * its in-place migration, `apps/admin-cli/migrations/0001-snake-case-dataset-ids.sql`, was written for production. A later reorganization (`7dcaaab`) deleted it, but it's still in git (`git show d8c5faf:apps/admin-cli/migrations/0001-snake-case-dataset-ids.sql`);
    * it still fits the schema. It moves exactly the five foreign keys that point at datasets and visas (tags, DRS objects, visas, Data Connect tables, visa assertions), and neither table has gained columns. Compute's tables reference only `compute_platform`;
    * it runs as one transaction, skips datasets already renamed, and keeps every approval;
    * **run by hand (the user's choice, 2026-11-24), not as a Liquibase changeset:** from the helper pod, as `refcloudapi` (the tables' owner), with the password piped in on stdin rather than in a command line. Then `dataset reset` and `dataset onboard` under the new ID (decision 13);
    * production's rollout needs the same steps, so they go into spec 0010.
15. **The UI works end to end on staging (the user, 2026-11-24):** after the migration, and onboarding 1000 Genomes and the refget conformance dataset under their new IDs:
    * login, and the passport token;
    * Explore Data, with the platform table and both datasets.
16. **The service registry listed Data Connect and refget as `local`.** The chart set only DRS's and the registry's service-info, and compute's ID suffix. Data Connect and refget kept `application.yml`'s native-mode defaults (`org.ga4gh.refcloud.dataconnect.local`, `...refget.sequence.local`). Consolidated (the user's request, 2026-11-24):
    * one shared value, `ga4gh.referenceCloud.serviceInfo`, replaces the per-service `drs.serviceInfo`, `serviceRegistry.serviceInfo` and `compute.serviceInfo`. It holds `idSuffix`, `environmentName`, `environment`, `organization`, `contactUrl` and `documentationUrl`;
    * a helper (`refcloud.serviceInfoEnv` in `_helpers.tpl`) gives every service its ID (`org.ga4gh.refcloud.<service>.<idSuffix>`), its name (`<service name> (<environmentName>)`), the shared settings, and `image.tag` as its version. That covers DRS, refget, Data Connect and the service registry. Compute's WES and TES get the suffix and the shared settings, since each platform builds its own IDs and names;
    * descriptions, types and timestamps stay each service's own, in core-api. A new service needs one line in the Deployment, and no new values;
    * the environment variables use Spring's canonical names (`..._SERVICEINFO_...`). DRS's had used the legacy `_SERVICE_INFO_` form, which also binds;
    * a guard fails the render if the values still use the old per-service keys, as production's do, rather than ignoring them silently;
    * staging's values now set the shared block. Renders: the local defaults give `.local.helm` IDs and staging's give `.staging`, for all four services. kubeconform and the helm checks pass. Native mode keeps `application.yml`'s defaults.
17. **The after-deploy checks pass on staging (the user, 2026-11-24):**
    * the service registry lists every service with `.staging` IDs, staging names and the image tag as version;
    * every pod is ready, the Services are all ClusterIP, and the six policies exist;
    * from a throwaway pod, Kratos's and Hydra's admin APIs time out;
    * registering a new account works, which also tests Kratos's webhook to core-api through the policies, and so does DRS with a token.
18. **Written back to spec 0010 (2026-11-24),** each finding marked "(staging)" in the task where production needs it, with no private details:
    * Task 2: the policy enforcement and ECR repositories, done;
    * Task 3: values written as staging's (the chart path, `ingressFrom` on the public subnets, the shared service-info, the dropped keys, the setup Jobs' Secrets);
    * Task 4: the read-only checks, the snapshot on a shared instance, the bootstrap login, and the dry run;
    * Task 5: what the setup Jobs' logs should show;
    * Task 6: the bootstrap clean-up and conditional rotation, the seed running by itself, the dataset ID migration with its commands, re-onboarding both datasets, removing the helper pod, then the browser checks, including Explore Data;
    * spec 0010's Future Direction for a staging environment is marked done.

### Task 4: AWS resources (Terraform)

In the private Terraform repo, each change reviewed first:

* **`foundation`** (shared and cluster-wide):
  * **an S3 gateway endpoint** on the VPC's route tables. S3 traffic within the region then stops going through NAT, which is cheaper, and bucket policies can require the endpoint. Check that no other project's bucket policy depends on the NAT gateways' addresses;
  * **an EBS storage class** (`ebs.csi.eks.amazonaws.com`, gp3, encrypted, `WaitForFirstConsumer`). It isn't made the cluster's default.
* **`reference-cloud`, for staging and production:**
  * **a bucket each,** with:
    * public access blocked, encryption and TLS only;
    * a lifecycle rule: incomplete uploads, and how long runs' files are kept;
    * possibly, requests only through the VPC endpoint;
  * **three roles each, with Pod Identity associations** (policies as in spec 0010, Task 2):
    * core-api's, for `refcloud-api` in the release namespace;
    * the run role, which trusts only core-api's role;
    * Funnel's workers', for `funnel-worker` in the compute namespace;
  * **a node pool for compute pods** (an Auto Mode `NodePool`):
    * a taint, so other pods stay off it;
    * on-demand amd64 instances of chosen types, with limits on total CPU and memory;
    * nodes are removed only when empty, so running tasks aren't moved.

    Either one per environment or one shared;
  * **a budget with alarms.**
* **Values from Terraform:** each environment's bucket and role ARNs come from Terraform's outputs and go into the private values files.

**Decisions (Task 4):**

Agreed 2026-11-25:
1. **Read-only checks (the user):**
   * no bucket policy in the account allows by `aws:SourceIp` or `aws:SourceVpc`, so the gateway endpoint can't break one;
   * Auto Mode's `default` node class uses the cluster's Auto Mode node role. Its name is in the private Terraform repo.
2. **Node pools: one per environment.**
   * on-demand amd64, the `c` and `m` families up to `xlarge`;
   * caps of 4 vCPU / 16 GiB for staging and 16 vCPU / 64 GiB for production;
   * a node is removed after 5 minutes empty;
   * their own node class: private subnets only (by their `kubernetes.io/role/internal-elb` tag), tags on instances, and the default node role. Nodes exist only while compute pods run, so two pools cost no more than one, and staging can't starve production.
3. **Compute namespaces:** `reference-cloud-staging-compute` and `reference-cloud-prod-compute`, set by a new chart value (Task 6) and created by the chart, as locally. The Pod Identity associations name them.
4. **Run buckets:**
   * `ga4gh-refcloud-compute-runs-staging` and `-prod`;
   * public access blocked, encryption, TLS only;
   * lifecycle: incomplete uploads and `health/` after 1 day, runs' files after 30 days;
   * no VPC-endpoint-only rule, which would lock out operators' CLI and console.
5. **Remove `reference-cloud`'s unused `helm` provider:** it reads `~/.kube/config`, like `foundation`'s did (Task 1, decision 3).

Also agreed 2026-11-25:
6. **No refcloud-only budget for now.** Compute nodes exist only while compute pods wait for them, so with no runs there's no compute node cost; that holds as long as nothing long-running is scheduled on the compute pools (Task 6 keeps Funnel's server on the general pool). A refcloud-only budget needs a cost-allocation tag that only the Organization's payer account can activate. The node pools' caps bound the worst case: about one `xlarge` node for staging and four for production, plus Auto Mode's per-node fee. Nodes exist only during runs, which need approved visas and are limited by each platform's monthly core-hours. The account-wide budget stays as the backstop.
7. **The S3 gateway endpoint and the EBS storage class go in `foundation`.** They're shared and cluster-wide: the endpoint attaches to the VPC's route tables, which `foundation` owns, and changes S3 traffic for every workload in the VPC. The node pools and node class, buckets, roles and associations are the refcloud's own, so they go in `reference-cloud`.
8. **Production's Pod Identity associations are created now** (the user's choice, 2026-11-25). Production's core-api gets its role from its next restart, while it still runs the old chart. The current code reads public buckets (DRS's) anonymously, so a role in the pod doesn't change those reads. Staging shows that first, through DRS after its next restart.
9. **The node class mirrors Auto Mode's default** (the user pasted its spec): its role, the cluster's security group (by reference), its ephemeral storage (80 GiB, gp3 3000 IOPS, 125 MiB/s), `networkPolicy: DefaultAllow`, event logs off and `snatPolicy: Random`. It differs in two ways:
   * only the VPC's private subnets, by ID, looked up within the cluster's VPC (a tag selector could match another VPC's subnets);
   * the instances carry `Project` and `Component` tags.
10. **Written 2026-11-25** (the user runs `plan` and `apply`):
    * `foundation/main.tf`: the S3 gateway endpoint on every route table, with the default policy, and the `ebs-gp3` storage class. Expected plan: 2 to add;
    * `reference-cloud`: a new `compute.tf`, three variables in `variables.tf` (`compute_environments`, `eks_auto_node_role`, `compute_runs_expiration_days`), and the unused `helm` provider removed from `main.tf`. Expected plan: 29 to add:
      * per environment: 5 bucket resources, 3 roles and their policies, and 2 Pod Identity associations;
      * the node class, and a node pool per environment;
      * an output, `compute`, with each environment's bucket, run role ARN and node pool;
    * both folders pass `terraform validate` (copies with no backend, Terraform 1.15.0). `foundation`'s existing `tags_all` on its Route 53 zone gives a warning, unrelated.
11. **Applied and checked 2026-11-25** (the user ran `apply` in both folders, then the read-only checks, which all passed):
    * the node class is Ready. Its security group is the default node class's, and its three subnets are the VPC's private ones (one per zone, checked against the subnets' ranges and that they don't assign public addresses);
    * both node pools exist, with no nodes;
    * the `ebs-gp3` storage class exists;
    * four Pod Identity associations: `refcloud-api` in both release namespaces, and `funnel-worker` in both compute namespaces;
    * the S3 gateway endpoint is available on all the VPC's route tables;
    * a second `plan` in `reference-cloud` shows no changes.

    Each environment's core-api gets its role at its next restart (decision 8). Both were restarted on 2026-11-25, production's still on the old chart, and both sites work afterwards.

### Task 5: Spike: EKS networking and identity

Run this in staging's cluster before the chart work, with throwaway pods in a throwaway namespace that enforces the restricted Pod Security level. The user runs the commands, and the results go under Decisions.

* **DNS:** in a default-deny namespace, pods allowed only the CoreDNS address on port 53 can resolve names.
* **DNS-based policy:** an `ApplicationNetworkPolicy` that allows only staging's bucket name. Check that:
  * the bucket's objects can be read with temporary credentials;
  * other names don't resolve (or the lookups don't leave the cluster);
  * other buckets and addresses can't be reached;
  * whether the policy also needs the CoreDNS rule.
* **Pod Identity:** a pod with no ServiceAccount token mounted (`automountServiceAccountToken: false`), and egress only to the Pod Identity agent (`169.254.170.23`, port 80) and S3:
  * its credentials arrive;
  * Funnel's image can use them, through the SDK's default chain.
* **The node pool:**
  * a pod with the toleration lands on it, and one without doesn't;
  * a node starts, and is removed once it's empty.
* **EBS per-task claims:** a first pod binds a claim from the new storage class, and a second pod on the same node shares it through `podAffinity`, as Funnel's worker and executor do.
* **The Kubernetes API's addresses on EKS,** from the EndpointSlice the chart looks up: whether they change (e.g. on control-plane updates). If they do, a policy pinned at deploy time would break.

**Decisions (Task 5):**

Agreed 2026-11-25:
1. **The spike's namespace is the real compute namespace, `reference-cloud-staging-compute`.** The spike creates it, enforcing the restricted level, and deletes it afterwards. Funnel's workers' Pod Identity association names it, so the spike tests the real association and its trust conditions, with no temporary AWS changes. Helm won't install over a namespace it didn't create, and in Task 8 the chart creates it, so the deletion matters.
2. **Funnel's image and Pod Identity are checked without AWS.** First Funnel 0.12.3's code: how its S3 backend gets credentials when no keys are set. Then its worker runs locally against a stand-in for the Pod Identity agent's credentials endpoint. On EKS, the spike checks Pod Identity with the AWS CLI, and Task 8 confirms Funnel itself. A Funnel server and worker in the spike would cost about as much as Task 6.
3. **Pod Identity is tested with Funnel's real pod settings.** Funnel's workers mount their ServiceAccount token, because they create the executor Jobs.
   * The worker's settings (ServiceAccount `funnel-worker`, token mounted, restricted security context): credentials arrive; it can write, read and delete under `runs/`, and is refused `health/` and other buckets.
   * The executor's settings (the default ServiceAccount, no token): no credentials.
   * Staging's core-api has Pod Identity's environment variables.
4. **One worker-like pod tests the DNS-based policy and Pod Identity together:** DNS, the bucket by name and the Pod Identity agent are exactly what a worker needs. It also shows whether the `ApplicationNetworkPolicy` needs a separate rule for the node's CoreDNS. S3 serves every bucket from the same addresses, so the spike also tests whether allowing one bucket's name lets the pod reach another bucket. If it does, Task 7 decides what to do about it.
5. **The node pool and EBS are tested together, on the compute pool,** where Funnel's pods will run:
   * a worker-like pod and an executor-like pod share an `ebs-gp3` claim (`podAffinity`, `fsGroup: 10001`);
   * a pod without the toleration stays off the pool;
   * after the cleanup, the claim's volume is deleted and the node goes away (about 5 minutes empty).
6. **The Kubernetes API: both kinds of rule are tested,** the `kubernetes` Service's cluster IP and the EndpointSlice addresses the chart pins today. If the cluster IP works under EKS's policy agent, Task 7 can drop the pinning. The current addresses, and what AWS documents about them changing, are recorded either way.
7. **How it runs:** manifests and a step-by-step runbook from me, run by the user step by step (setup, DNS, the DNS-based policy with Pod Identity, the node pool with EBS, the API, cleanup). The images come from Public ECR, which avoids Docker Hub's limits on the NAT addresses, and run as user 10001 under the restricted level.

Done 2026-11-25, before the spike:

8. **Funnel 0.12.3 picks up Pod Identity's credentials** (decision 2).
   * **Its code:** when its `AmazonS3` config has no key and secret, it loads the AWS SDK for Go v2's default configuration (`LoadDefaultConfig`). That configuration's credential chain includes the container credentials provider, which is how Pod Identity delivers credentials.
   * **Run locally:** `funnel worker run --taskFile`, with no Funnel server, against a throwaway RustFS. The only credentials came from a stand-in for the Pod Identity agent's endpoint: temporary STS credentials served behind an authorization token file, as the agent does. The worker downloaded its input and uploaded its output, and the task ended `COMPLETE`. A stub stood in for `docker`, its default executor.
   * **Negative control:** without the stand-in, Funnel found no credentials and fell through to instance metadata.
   * Everything was deleted afterwards.
   * A token file that ends in a newline is rejected by the SDK. The stand-in's first token had one; Pod Identity's real token file doesn't.
9. **Found in Funnel's code: on AWS, it looks up each bucket's region through us-east-1.**
   * With no endpoint configured, Funnel makes an anonymous `HeadBucket` request through a us-east-1 client before using a bucket, to learn the bucket's region.
   * So workers would also need the bucket's us-east-1 name, reached through NAT: the gateway endpoint covers only the VPC's own region.
   * Configured with the endpoint `s3.<region>.amazonaws.com/` (the form Funnel treats as AWS) and a region, Funnel skips the lookup. It then sends virtual-hosted requests to the bucket's regional name, the same name the Nextflow Jobs use.
   * The spike runs Funnel's own image with both configs, as a worker, under the worker's policies (runbook step 3j). Task 6 configures Funnel from the result.
10. **Also in the spike:** whether a pod that a policy allows to reach instance metadata gets an answer (runbook step 3h). If not, the nodes' hop limit keeps pods away from the node role's credentials, which matters most in namespaces without policies.

Spike results (2026-11-25, the user ran the runbook):

11. **Auto Mode's DNS-based policy limits connections, not lookups.** Tested on a worker-like pod whose `ApplicationNetworkPolicy` allows only staging's bucket by name:
    * **with DNS allowed by a separate NetworkPolicy:** the bucket's name resolves, and so do every other name tried (an unrelated domain, production's bucket, S3's regional name);
    * **with DNS allowed inside the same `ApplicationNetworkPolicy`, as in AWS's example:** the same result;
    * in both cases, a connection to an unrelated name's address is refused (`000`).

    So a pod allowed DNS can look up any name. A lookup can carry data out through the cluster's resolvers, which is why workflow pods get no DNS today (spec 0011). The policy still keeps a pod's connections to the allowed names' addresses, so it fits Funnel's workers, which run Funnel's own code. For the Nextflow Jobs, where workflow code runs, it isn't enough on its own. Task 7 decides how they reach S3.
12. **Pod Identity works with the worker's settings** (ServiceAccount `funnel-worker`, token mounted).
    * The role's credentials arrive.
    * Writing, reading and listing under `runs/` work.
    * Writing outside `runs/` is refused (`AccessDenied`: no identity-based policy allows it).
    * Reading a missing key outside `runs/` returns `404`, not `403`: the role may list the bucket, and S3 then reports missing keys as missing. So tests of what the role may not do use writes.
13. **The executor's settings get no credentials** (the default ServiceAccount, no token): no Pod Identity variables, no `/var/run/secrets`, and the AWS CLI finds no credentials.
14. **A pod allowed one bucket by name can reach any bucket in the region.** S3 answered a request for production's bucket sent to an address the pod had learned from staging's bucket name. An address the pod never resolved timed out.
    * So no name-based rule can keep a pod to our bucket.
    * For Funnel's workers that's acceptable: they transfer only files whose URLs our TES has checked belong to the run.
    * For the Nextflow Jobs, it's a second reason, after decision 11, to weigh other ways of reaching S3 in Task 7.
15. **Pods can't get the node role's credentials from instance metadata**, even with a policy that allows the address.
    * A token request (IMDSv2) times out: the node's hop limit keeps the answer from reaching the pod.
    * A request without a token (IMDSv1) is refused (`401`).
    * So this holds in namespaces without policies too.
16. **Staging's core-api has Pod Identity's variables** (the agent's URL and the token file) since its restart.
17. **DNS under default-deny (step 2), and the worker's credentials (3b), matched what was expected:**
    * a pod's nameserver is the `.10` address of the service range;
    * under default-deny, lookups fail;
    * with a NetworkPolicy allowing that address on port 53, lookups work, and connections stay blocked;
    * the worker-like pod has Pod Identity's variables and token file, and the AWS CLI finds its credentials (`container-role`).
18. **Funnel's own image works as a worker on EKS** with the endpoint `s3.us-east-2.amazonaws.com/` and the region set (decision 9).
    * Same ServiceAccount and policies as the worker-like pod: Pod Identity, and the bucket's regional name only.
    * Its task, run from a file, downloaded its input and uploaded its output, and ended `COMPLETE`.
    * With no endpoint, the run didn't finish and the user interrupted it: its region lookup goes to the bucket's us-east-1 name, whose connections the policy refuses.
    * So Task 6 configures Funnel with the regional endpoint.
19. **The compute node pool works.**
    * Pods with the toleration got a new on-demand node from `refcloud-compute-staging`, a `c6a.large` in another zone, within about a minute. The other spike pods stayed on the general-purpose node.
    * A pod that selects the pool without tolerating its taint stayed Pending, and Auto Mode started no node for it.
    * Auto Mode's message for that pod also reported the staging pool as over its limits, while the first node was still starting. To watch in Task 8: whether the 4 vCPU cap holds back a second node while one is starting.
20. **A per-task EBS claim can be shared by a worker and its executors only with one SELinux level for both.** Found in step 4; this would have failed every Funnel task on EKS.
    * The claim itself works: `ebs-gp3` bound it once the first pod was scheduled, in that pod's zone, from Auto Mode's EBS driver. `fsGroup: 10001` made it writable for user 10001.
    * **The problem:** Auto Mode's nodes enforce SELinux, and give each pod random categories (e.g. `s0:c47,c850` and `s0:c535,c959`). Kubernetes relabels a volume for the pod that mounts it, so only the last pod to mount it can use it.
      * The worker-like pod's first run was refused its own volume, which still carried the executor-like pod's label.
      * After the worker restarted, the volume carried the worker's label, and the executor was refused even a listing.
      * Unix permissions allowed both: it was SELinux that refused them.
    * **The fix, verified:** the same `seLinuxOptions.level` (`s0:c123,c456`) on both pods. The restricted level allows setting it. Both pods then ran without a restart and used the volume together, and the volume was relabelled to that level (same claim, same node).
    * **For Task 6:** one SELinux level on Funnel's worker and executor templates, per environment.
      * Every compute pod then shares one level, as OpenShift gives each namespace one. Tasks stay apart because each task's claim is mounted only into its own pods.
      * Nodes without SELinux, such as minikube's, ignore the setting.
      * Funnel's server and its own claim (`funnel-db`) are a single pod, so they don't need it.
21. **On EKS, policies see the Kubernetes API at the `kubernetes` Service's cluster IP, not at the API server's own addresses: the opposite of minikube's Calico.** From a pod with a ServiceAccount token, calling `/version` through the Service:
    * a rule on the cluster IP (port 443): `200`;
    * no rule: refused;
    * a rule on the API server's addresses from the EndpointSlice (the control plane's two network interfaces, port 443), as the chart pins them today: refused.

    The cluster IP is the first address of the service range, fixed for the cluster's lifetime, so on EKS the question of the API server's addresses changing goes away. For Task 7: allow both the cluster IP and the EndpointSlice's addresses. Each environment matches one, and the other does no harm.
22. **Cleaned up (step 6):**
    * the namespace is deleted, and with it the claim's PersistentVolume;
    * the EBS volume itself is gone (`InvalidVolume.NotFound`);
    * no test files remain in the bucket;
    * the compute node went away once empty, leaving only the general-purpose node.

    The spike's files stayed in my session's scratchpad, outside every repo. **Task 5 is done.**

### Task 6: The chart's compute on AWS

* **The compute namespace's name:** a new value, defaulting to `<release name>-compute`, so local stays `refcloud-compute`. Each environment's values set its own.
* **The AWS connection,** defined by the chart from values:
  * `s3` storage with the bucket, region and run role, and no endpoint or keys (Pod Identity);
  * limits sized for the node pool;
  * `max-run-duration-seconds` of 2700 (spec 0012).

  The quota and the LimitRange follow from it.
* **Funnel on AWS:**
  * `AmazonS3` with no endpoint and no keys: the workers use Pod Identity, so there's no `funnel-s3` Secret;
  * task claims and `funnel-db` come from the new storage class (a value).
* **Compute pods on the node pool:**
  * tolerations and node selection for Funnel's workers and executors (`files/funnel/`);
  * the same for the Nextflow Jobs, which core-api creates, so this is a connection setting (`NextflowKubernetesEngine`);
  * whether Funnel's server goes there too;
  * pods that mustn't be moved mid-run carry `karpenter.sh/do-not-disrupt`.
* **RustFS stays local only.**
* **Static analysis:** render and check the chart with sample AWS values too, not only the local defaults.

**Decisions (Task 6):**

Agreed 2026-11-25. Not questions:
* **The AWS connection** (`nextflow-funnel-kubernetes-aws`) is built by the chart from a block of values: bucket, region, run role, limits. With no bucket, compute is off.
* **New values:**
  * the compute namespace's name, defaulting to `<release>-compute`. Each environment's must match its Pod Identity association;
  * a storage class for task claims and `funnel-db` (`ebs-gp3` on AWS).
* **Funnel on AWS:**
  * the regional endpoint and no keys, so no `funnel-s3` Secret (Task 5, decision 18);
  * its server on the general pool;
  * its workers and executors on the compute pool, with the toleration, node selector, shared SELinux level and Auto Mode's do-not-disrupt annotation.
* **Unchanged:**
  * core-api's and Funnel's permissions in the compute namespace carry over to AWS;
  * RustFS stays local only;
  * network policies are Task 7;
  * nothing is deployed to staging until Task 8.

Answers:
1. **The Nextflow Jobs run on the compute pool too.** Workflow code stays off the nodes running the refcloud's own services, and all compute cost sits in the pool with the cost cap.
2. **The connection's engine block gets general Kubernetes settings,** which core-api applies to each Job: `node-selector`, `tolerations` and `selinux-level`. Every Job also gets Auto Mode's do-not-disrupt annotation; minikube ignores it. Rejected: a single node-pool setting, which would tie core-api to our pool's label and taint.
3. **Each environment's SELinux level is derived by the chart from the compute namespace's name,** so staging and production differ with no new required value. Values can override it, and minikube ignores it.
4. **Staging's limits:**
   * one run at a time;
   * the Nextflow Job at 1 CPU / 2 GB;
   * up to 2 tasks in flight, 2 CPUs / 4 GB in all;
   * each task up to 2 CPUs / 4 GB / 20 GB of disk, defaulting to 1 CPU / 1 GB;
   * runs end after 2700 seconds;
   * up to 10 GB of inputs per run.

   That's about 3.1 CPUs at full load, under the pool's 4 vCPU cap. Production's limits are written in spec 0010.
5. **How the Nextflow Jobs reach S3** (Task 7's question, settled here because it changes the engine). **Decided 2026-11-25: a private S3 endpoint for compute, in all three zones.** Of the options, it's the only one that closes both gaps the spike found (Task 5, decisions 11 and 14):
   * **an S3 interface endpoint used only by compute,** in the cluster's private subnets, at fixed addresses. Its private DNS is off, so the VPC's other S3 traffic keeps using the gateway endpoint. Its security group accepts HTTPS only from the cluster's security group;
   * **its endpoint policy** allows only the run buckets, and only the refcloud's compute roles. No other bucket is reachable through it, whatever the credentials;
   * **no DNS anywhere in the compute namespace.** The Nextflow Jobs and Funnel's workers get the endpoint's names pinned to its addresses through the pod's `hostAliases` (`/etc/hosts`), which works for Java, Go and curl alike. Their policies allow only those addresses, on port 443;
   * **the bucket policies refuse the run role's and the workers' role's credentials anywhere but through the endpoint,** so leaked ones are useless outside the VPC. Operators and core-api's own role aren't affected;
   * **for S3, core-api keeps the endpoint's name and adds `hostAliases`,** instead of swapping the name for an address as it does for RustFS, which would break TLS on AWS.

   What it leaves open is by design: a run's outputs, downloaded by the researcher, and the TES calls to core-api. It costs about $22 a month in three zones, plus $0.01 per GB.

   Rejected:
   * no DNS but public S3 addresses: closes the DNS leak, not other buckets;
   * accepting DNS for the Jobs: closes neither, and leaves the workflow allowlist as the only boundary.

   First, a short check on EKS that Nextflow's AWS plugin, Funnel's Go SDK and the AWS CLI work through the endpoint with no DNS. It also settles whether the bucket is addressed in the path or in the host name, which decides the names pinned in `hostAliases`.
6. **The endpoint's Terraform, written 2026-11-25** (`reference-cloud/compute.tf`, validated; the user runs `plan` and `apply`):
   * the interface endpoint in the three private subnets, at fixed addresses (each subnet's `.250`, so they're known before it exists, and never change), private DNS off;
   * its security group (HTTPS from the cluster's security group only);
   * its policy: the run buckets, for the six compute roles (core-api's, the run role and the workers', per environment);
   * a second statement in each run bucket's policy, refusing the run role and the workers' role except through the endpoint (`aws:SourceVpce`);
   * the `compute` output gains the endpoint's URL (`https://bucket.vpce-….s3.us-east-2.vpce.amazonaws.com`) and its addresses.

   Expected plan: 2 to add, 2 to change (the bucket policies, in place). The endpoint check comes next, run by the user: Pod Identity, no DNS, `hostAliases`; the AWS CLI, curl as the launcher signs, Funnel, and Nextflow in each addressing style; and the bucket policy from a pod away from the endpoint.
7. **Applied, and the endpoint check passed (2026-11-25, the user).** Pods with no DNS, Funnel's workers' role through Pod Identity, and egress only to the endpoint's three addresses and the Pod Identity agent. The endpoint's names were pinned in their `/etc/hosts`:
   * **the AWS CLI** wrote and read the bucket in both addressing styles. TLS accepted both the endpoint's own name (path-style) and the bucket's name under it (virtual-hosted). Production's bucket was refused (by IAM: the endpoint policy allows it, for production's roles);
   * **curl, signing as the run's launcher does:** `200` in both styles;
   * **Funnel's image** (`Endpoint` set to the endpoint's URL, so Funnel uses path-style): `COMPLETE`, input downloaded and output uploaded;
   * **Nextflow** (staging's image from ECR, nf-amazon, the AWS SDK for Java v2 as in core-api) wrote and read the bucket in both styles;
   * **away from the endpoint** (a pod with DNS and any address): the workers' role was refused through S3's public name, by the bucket policy's explicit deny, and allowed through the endpoint.

   **Path-style addressing is chosen.** Funnel uses it anyway with an endpoint, core-api's S3 client already uses it when a connection names an endpoint, and it needs only the endpoint's own name pinned. Virtual-hosted works too, if AWS ever retires path-style.

   Cleaned up afterwards: the test files, and the namespace.
8. **Funnel's workers keep DNS, as they do locally** (the user, 2026-11-25; a refinement of decision 5's "no DNS anywhere").
   * **The reason:** workers find Funnel's server by its name (`funnel:9090`). Its Service's address isn't known until the Service exists, so it can't be pinned.
   * **Why the risk is small:** the names workers look up are only ones Funnel chooses: the server, and the endpoint. A workflow can't make them look up anything else, because our TES checks every task's file URLs before Funnel sees them. Their connections stay limited by policy (Task 7) to the endpoint, the server, the Kubernetes API and the Pod Identity agent.
   * **So:** workers resolve the endpoint's name themselves (it resolves to its private addresses), and need no pinned names.
   * **Unchanged:** the Nextflow Jobs and the executors, where workflow code runs, get no DNS.
   * **Rejected:** a fixed cluster IP for Funnel's Service, set per environment. That's brittle: a wrong or taken address fails the deploy.

Built 2026-11-25:

9. **core-api's engine** (`ComputeConfig.Engine`, `NextflowKubernetesEngine`):
   * **new engine settings:** `node-selector`, `tolerations` and `selinux-level`, all optional and validated (label keys and values, `Equal`/`Exists`, the effects, an SELinux level). A toleration must name a key, and one with `Exists` takes no value.
     * The node selector is a list of key and value pairs, not a map: Spring drops a map key's slashes unless it's written in brackets, and `refcloud.ga4gh.org/compute` has one.
     * The admin CLI reads only a connection's type and limits, so it's unaffected.
   * **the Job's pod** gets the node selector, the tolerations, `seLinuxOptions.level`, and Auto Mode's `karpenter.sh/do-not-disrupt` annotation (always; elsewhere it means nothing);
   * **a storage endpoint keeps its name**, which the Job's `hostAliases` pins to the address core-api resolves it to at launch. That replaces swapping the name for its address, which works for RustFS's plain HTTP but would break TLS on AWS. An endpoint given as an address gets no alias;
   * **tests:** 326, all pass, including new ones for the placement, the pinned name (RustFS, and an interface endpoint) and binding and validation. `./gradlew staticAnalysis` passes. SpotBugs logs an internal error in Find Security Bugs' CORS detector on `CorsConfig`, which predates this work and doesn't fail the build.
10. **The chart:**
    * **new values** under `ga4gh.referenceCloud.compute`:
      * `namespace` (default `<release>-compute`);
      * `storageClass` (the cluster's default when empty);
      * `nodeSelector` and `tolerations` (the compute pods' nodes);
      * `seLinuxLevel` (derived when empty: two categories from the compute namespace's name's checksums);
      * `aws`: bucket, region, run role, endpoint URL and addresses, plus the engine's size and limits, which default to staging's (decision 4).
    * **`refcloud.computeEnabled`:** compute renders locally, or on AWS when `aws.bucket` is set. The namespace, quota, limit range, Funnel and core-api's Role in the compute namespace all follow it. RustFS, Funnel's RustFS Secret and core-api's RustFS keys stay local only;
    * **one connection, `refcloud.computeConnection`:** the local one, or the AWS one, `nextflow-funnel-kubernetes-aws`, with `s3` storage through the endpoint, path-style, no keys, and run credentials for up to an hour. Both share `refcloud.computeEngine`: the versions, the image, the namespace, the placement and the SELinux level. The connections file, the quota, the limit range and Funnel's config all read it. On AWS the chart refuses to render without the region, run role, endpoint and its addresses;
    * **Funnel:**
      * its `AmazonS3` endpoint and region come from the connection;
      * its templates gain placeholders, filled by `refcloud.funnelTemplate`, which fails if any is left: the placement and SELinux level on the worker and executors, their do-not-disrupt annotation, the storage class on the task claim, and the workers' RustFS credentials, local only;
      * `funnel-db` takes the storage class. The server stays on the general pool;
    * **checked:**
      * the chart renders with its defaults and with sample AWS values (new: `deploy/helm/static-analysis/aws-values.yaml`, examples only);
      * `./scripts/refcloud-static-analysis-helm.sh` now lints, validates and scans both, and all checks pass;
      * Funnel's rendered worker and executor templates (Go expressions filled with sample values) are valid Jobs in both;
      * minikube accepts a pod with an SELinux level and a host alias (a throwaway namespace, deleted).
11. **Docs:** `deploy/helm/CLAUDE.md` (compute), `apps/core-api/CLAUDE.md` (engines), and the comments in the compute network policies, the connections and Funnel's templates. The README's values and the AWS deploy are Task 9.

    Staging's values, and its deploy, are Task 8, after Task 7's network policies.
12. **The local helm-mode demo passed (2026-11-25, the user):** `./scripts/refcloud-compute-demo.sh` on `nextflow-demo-kubernetes`, rebuilt images: submitted, `COMPLETE` in about 20 seconds with one TES task, and the report's MD5 matched the DRS object's checksum. So locally, the Nextflow Job reached RustFS by its pinned name with no DNS, and Funnel's worker and executor ran with the shared SELinux level. **Task 6 is done.**

### Task 7: Network policies on EKS

The compute namespace's policies on AWS, from Task 5's findings:

* **DNS:** through the node's CoreDNS address (a value, or derived), wherever pods get DNS today;
* **S3 by name** for the Nextflow Jobs and Funnel's workers: the bucket's virtual-hosted name. Ideally this goes through an `ApplicationNetworkPolicy`, so workflow pods still can't look up other names. The fallback is S3's published address ranges;
* **the Pod Identity agent** for Funnel's workers;
* **the Kubernetes API,** as Task 5 found;
* **the table in the template's header,** updated for both modes.

Nothing changes locally.

**Decisions (Task 7):**

Agreed 2026-11-25:
* **Not questions** (settled by the spike, Task 5, and Task 6):
  * the Nextflow Jobs: core-api's TES API, and the S3 endpoint's addresses on 443; no DNS;
  * Funnel's workers: Funnel's server (RPC), the endpoint's addresses, the Pod Identity agent (`169.254.170.23:80`), the Kubernetes API and DNS;
  * Funnel's server: the Kubernetes API and DNS;
  * the executors: nothing;
  * the Kubernetes API and DNS rules name both address forms, so they work under Calico and EKS alike: the `kubernetes` Service's cluster IP and the API server's own addresses, and CoreDNS's address next to kube-dns's pods;
  * core-api accepts the Nextflow Jobs whenever compute is on. RustFS's rules stay local only;
  * the outgoing allowlist works the same on AWS;
  * the template's header table covers both modes.
1. **CoreDNS's address is derived when the chart is deployed:** the `.10` of the `kubernetes` Service's cluster IP, the service range's `.1`, which the chart already looks up. It's right in minikube too (kube-dns is `10.96.0.10`), and needs no new value.
2. **Whether rules on pods also cover traffic to their Service's address** (Nextflow → core-api, workers → Funnel's server) is checked in Task 8, which deploys these policies to staging anyway. If it fails, the fallback is a rule on each Service's cluster IP, looked up when the chart is deployed: a chart change only, no new images.

Built 2026-11-25:

3. **The compute namespace's policies render whenever compute is on** (`networkPolicy.enabled` and `refcloud.computeEnabled`), not only locally:
   * **`refcloud.computeClusterAddresses`** looks up, when the chart is deployed, the `kubernetes` Service's cluster IP and port, the API server's own addresses and ports (its EndpointSlices, as before), and CoreDNS's address (the cluster IP's `.10`; IPv4 only). A deploy that reaches a cluster but finds no API server fails, as before;
   * **the API and DNS rules name both forms** (`refcloud.computeEgressToKubernetesApi`, `refcloud.computeEgressToDns`): the Service's address and the API server's, kube-dns's pods and CoreDNS's address;
   * **`refcloud.computeEgressToRunStorage`:** RustFS locally, or the endpoint's addresses on 443 on AWS, for the Nextflow Jobs and the workers;
   * **the workers** may also reach the Pod Identity agent on AWS;
   * **core-api** accepts the Nextflow Jobs whenever compute is on;
   * **the header table** covers both modes.
4. **Checked:**
   * **against minikube:** rendered with real lookups (`helm template --dry-run=server`), the derived DNS address is kube-dns's actual address (`10.96.0.10`). The API rules name the Service (`10.96.0.1:443`) and the API server (`192.168.49.2:8443`);
   * **locally:** without lookups, the policies are identical to before, comments aside. With lookups they only gain the extra address forms, so local behaviour can only allow more;
   * **the AWS render:** the Nextflow Jobs reach only core-api and the endpoint's three addresses (no DNS); the workers reach Funnel's server, the endpoint, the Pod Identity agent, the API and DNS;
   * **static analysis:** `./scripts/refcloud-static-analysis-helm.sh` passes with both sets of values (the AWS render now has 49 resources);
   * **docs:** `deploy/helm/CLAUDE.md`.

   Still to check on EKS, in Task 8: decision 2, and the whole compute stack under these policies.

### Task 8: Compute in staging, end to end

* **Deploy:** staging's values get the AWS connection, the compute namespace and the compute service info.
* **Set up the platform:**
  * register and enable a staging demo platform with the admin CLI, with the demo workflow on its allowlist and limits set;
  * approve a test account's visas.
* **The end-to-end checks,** adapted from spec 0012, Task 8:
  * the demo through WES, then `REFCLOUD_DEMO_BASE_URL=<staging> ./scripts/refcloud-compute-demo.sh <token>`;
  * outputs download, and the files are under the run's prefix in the bucket;
  * the credential check: own run 200, another run 403, bucket listing 403;
  * cancel;
  * a core-api restart mid-run, which fails the run on nf-ga4gh 1.5.0 (D23);
  * the network policy checks from stand-in pods;
  * task pods ran on the compute node pool, and its nodes went away afterwards;
  * the cost shows up where the budget expects it.
* **The kill switch** works: `refcloud compute-platform disable`.

**Decisions (Task 8):**

Agreed 2026-11-25:
1. **The platform is `nextflow-aws-us-east-2`, named "Nextflow on AWS (us-east-2)"** (the user, 2026-11-25). There's no "demo" in it, since it will be production's too.
   * It's a `nextflow-funnel-kubernetes` platform: a platform record on the `nextflow-funnel-kubernetes-aws` connection, which gives it its type.
   * **Visas are named after the ID, so it's permanent once researchers hold them.** It names only what stays true for the platform's life:
     * the workflow engine researchers choose (Nextflow);
     * the cloud and region, which say where runs happen and their files sit, and leave room for platforms elsewhere. Moving region would be a new platform anyway.
   * **Left out:**
     * Funnel, an implementation choice behind the platform's TES API (researchers submit through WES and only read TES), so replacing it wouldn't change the platform;
     * Kubernetes;
     * the owning organization, already in the URL's domain, in service-info's IDs (`org.ga4gh.refcloud.wes.<id>.<suffix>`) and in its `organization`. Platforms run by other organizations, if the catalog ever lists them (discussion 0001), could carry their own prefix.
   * Production registers the same ID, so WES URLs (`/ga4gh/wes/nextflow-aws-us-east-2/v1`) and visa names (`ga4gh_visa:compute_platform:nextflow-aws-us-east-2`) match across environments.
2. **Per-user limits:** only a monthly cap of 10 core-hours per researcher, as helm mode's demo platform has. Everything else follows the connection's limits.
3. **The platform stays enabled after Task 8.** Access needs an approved visa.
   * **Idle cost:** compute nodes exist only while a run's pods need them. Auto Mode starts one only for a pod tolerating the compute pool's taint, and the spike saw it go about 5 minutes after the last pod ended. A run's deadline bounds a stuck pod, and the pool's cap the worst case.
   * **Always-on costs:** the private S3 endpoint (about $22 a month), Funnel's server (a small pod on the general pool), its 1 GiB database volume, and core-api's health check objects.
4. **Planned:**
   * staging's values get the new image tag and the compute block (namespace, storage class, node pool, and the Terraform output's bucket, run role and endpoint);
   * a server-side dry run, then the upgrade;
   * the platform registered with the bundled `drs-checksum` allowlisted; the user's staging account requests access, and it's approved with the admin CLI;
   * the demo script builds its DRS input from the target deployment's host (it used the local one), and also tries the AWS platform's ID;
   * the end-to-end checks as a runbook. The budget check becomes a check that the compute nodes carry the `Project` and `Component` tags (Task 4, decision 6: no refcloud-only budget).
5. **Written 2026-11-25:**
   * **staging's values** (private values repo): image tag `v0.1.0-71-g4901114`, and the compute block: the compute namespace, `ebs-gp3`, the staging node pool's label and toleration, and the bucket, region, run role and private endpoint (URL and addresses) from the Terraform output. The engine's size and the limits are the chart's defaults. The chart renders with it;
   * **the demo script:** pointed at another deployment, it now submits that deployment's own copy of the demo object (`drs://<its host>/…`; it used the local host), and without `-p` it also tries `nextflow-aws-us-east-2` (`AWS_COMPUTE_PLATFORM_ID` in `scripts/lib/common.sh`);
   * **the runbook,** in my session's scratchpad, run by the user:
     * deploy (server-side dry run first) and check the compute namespace;
     * register and enable the platform; the user's access;
     * the demo, the bucket's files and the compute nodes' tags;
     * a run's credentials from inside its Job (a script that waits for the Job; status codes only);
     * cancel, and a core-api restart mid-run;
     * the network policies from stand-in pods: each component's label and none, every allowed and refused connection, including Service addresses (decision 2 of Task 7);
     * the kill switch, and the compute node going away.

Staging, 2026-11-25:

6. **Found on the first deploy: the platform was unhealthy, because core-api sent STS requests to the S3 endpoint.**
   * **What happened:** the deploy and the registration went through, but the connection's health check failed: `could not issue the run's storage credentials: (Service: Sts, Status Code: 400)`, with no message. The S3 half of the check had passed: the probe object was written, read and deleted through the private endpoint, with core-api's Pod Identity role.
   * **The cause:** `RunStorages.stsClient` overrode STS's endpoint with the storage `endpoint` whenever a connection named one. That was right for RustFS, which takes STS requests at its S3 address. On AWS the endpoint is now the S3 interface endpoint (decision 5), which answers an AssumeRole request with an S3 error the SDK can't read.
   * **Fixed:** a separate, optional `sts-endpoint` connection setting. STS goes there when it's set, and otherwise to AWS's regional STS; never to the storage endpoint. The chart's local connection sets it to RustFS's address, so local behaviour is unchanged; the AWS connection leaves it unset.
   * **Tests:** a connection through an interface endpoint binds and validates, with no `sts-endpoint`. The S3 client goes to the endpoint and the STS client to AWS's own, or to RustFS's when it's named. 329 tests and `./gradlew staticAnalysis` pass; the chart renders and passes lint and kubeconform with both sets of values.
   * **Next:** a new image (commit and push), staging's image tag, and the runbook again from the deploy. The platform is already registered.

### Task 9: Docs and the rollout spec

* `deploy/helm/README.md` (Remote Deployment: the setup Job, compute on AWS, the new values) and `deploy/helm/CLAUDE.md`.
* Spec 0010, what's left for production:
  * its Task 2 shrinks to what wasn't applied;
  * its Task 3 gets the new values;
  * registering production's platform.
* The milestones doc (item 3), and the discussion doc's To Verify list.
* The docs site: nothing until production has compute (spec 0010, Task 6), except anything staging's tests show is wrong.

**Decisions (Task 9):**

## Future Directions

* **A one-click AWS deploy: Terraform, then Helm, with nothing by hand in between** (raised 2026-11-20). Today, an environment's first deploy needs `refcloud-db-bootstrap` created by hand (Task 2, decision 4), and Helm's AWS values copied from Terraform. Terraform can't create the Postgres role itself, because RDS is in private subnets. Proposed:
  * **the bootstrap through Pod Identity:**
    * Terraform creates an IAM role per environment, allowed only `secretsmanager:GetSecretValue` on that RDS instance's master user secret, and associated with the `setup-predeploy` ServiceAccount in the release namespace;
    * the chart takes the secret's ARN and the database's endpoint as values;
    * when `refcloud-db-admin` is missing or can't connect, and there's no `refcloud-db-bootstrap`, `setup-predeploy` reads the master password from Secrets Manager (the AWS CLI in the image, or curl's SigV4 signing) and bootstraps as it does today;
    * the master password then never sits in the cluster or in Terraform's state. But the IAM permission is permanent: anyone who can run a pod as `setup-predeploy` in the namespace could read the master password. Weigh that, especially if other projects share the RDS instance;
    * `refcloud-db-bootstrap` stays, for minikube tests and clusters without Pod Identity;
    * it can only be tested on EKS: delete `refcloud-db-admin` in staging, and deploy;
  * **Helm's AWS values from Terraform's outputs:** the database endpoint, the certificate ARN, the VPC CIDR, and the compute bucket and IAM roles (Task 4). For example, a values file Terraform writes, or `helm upgrade` run with `-f` on Terraform's output.
