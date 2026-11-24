# ga4gh-reference-cloud-helm

Helm chart for the GA4GH Reference Cloud. The same chart deploys the refcloud to a local minikube cluster ("helm mode") or to a remote Kubernetes cluster (e.g. AWS EKS); environment-specific settings are passed as values at deploy time.

## Local Development (minikube)

Local helm mode is driven by scripts at the repo root, run from the repo root. It shares the host Postgres databases and OAuth client with native mode, so the same one-time setup works for both.

### Prerequisites

In addition to the native-mode requirements in the [root README](../../README.md#getting-started), install:

* `minikube`, `kubectl`, `helm`, `mkcert`
* Docker Desktop (minikube runs with the docker driver)

### Setup

```sh
./scripts/refcloud-setup.sh          # safe to re-run
./scripts/refcloud-setup.sh --reset  # start fresh (drops databases, re-onboards data)
```

Alongside the database, OAuth client and dataset setup, the setup script's Kubernetes section:

* starts minikube (docker driver) if it isn't running and enables the `ingress` addon. A new cluster gets the **Calico** network plugin, which enforces the chart's [NetworkPolicies](#network-policies). A cluster created before Calico was added makes setup and the run script stop with instructions: recreate it with `minikube delete`, then run setup again. Your databases are in host Postgres, so they're unaffected
* switches your `kubectl` context to `minikube` (every script command also passes `--context minikube --namespace default` explicitly, so it never touches another cluster)
* installs the mkcert local CA and generates a TLS certificate for `refcloud.ga4gh.localhost` and `*.refcloud.ga4gh.localhost` in `deploy/helm/.certs/` (git-ignored)
* creates or updates every secret the chart needs (see [Secrets](#secrets)), using the same values as native mode where both use them. RustFS's three are helm-only: random, and created only if they don't exist yet, since they go with RustFS's data (see Compute). `refcloud-db-admin` is the login setup's own `psql` commands use, for the chart's [setup Jobs](#setup-jobs); if your Postgres asks for a password on connections from `127.0.0.1`, set `PGPASSWORD` when running setup

`--reset` also deletes the TLS certificate, uninstalls the helm release, and deletes the refcloud secrets and RustFS's data (compute runs' files) before recreating the secrets (starting minikube first if needed). The cluster itself is not deleted.

The Kubernetes section is skipped with a warning if `minikube`, `kubectl`, `helm` or `mkcert` is missing, so native-only contributors don't need them.

### Run

```sh
./scripts/refcloud-run-helm.sh
```

The run script:

1. fails fast if port 443 is in use (e.g. native mode's Caddy, or another `minikube tunnel`)
2. computes the lockstep image tag: `git describe --tags --match 'v[0-9]*' --always`, plus `-uncommitted` if the working tree has any changes (including untracked files)
3. builds all app images, the Nextflow image compute runs and the setup Jobs' image into minikube (`minikube image build`); build logs go to `$TMPDIR/refcloud-build-logs/`. core-api and the admin CLI build from the repo root, so they can bundle `compute/workflows/`
4. runs `helm upgrade --install refcloud` with that tag. The [setup Jobs](#setup-jobs) and the database migrations (Liquibase, Kratos, Hydra) run as Helm hooks: `setup-predeploy` first, then the migrations, and once the release is applied, `setup-postdeploy` and RustFS's setup (its bucket and users). It prints each setup Job's last line: setup has prepared everything, so they should report nothing changed (except `refcloud seed`, which always rewrites its rows)
5. restarts the app deployments (so rebuilt images with an unchanged tag are picked up) and waits for them to be ready
6. registers the `nextflow-demo-kubernetes` compute platform, if it doesn't exist yet (see Compute below)
7. prints the service URLs and runs `minikube tunnel` in the foreground, which prompts for your password to bind port 443

The refcloud is then available at `https://refcloud.ga4gh.localhost` and `https://docs.refcloud.ga4gh.localhost`.

Ctrl+C stops the tunnel only; the release keeps running, so the next run is fast. Run the script again to reconnect.

### Compute

Helm mode runs compute (WES and TES) in the cluster ([spec 0011](../../claude/specs/archive/0011-add-compute-nextflow-funnel-kubernetes.md)), with each run's files in S3 storage ([spec 0012](../../claude/specs/archive/0012-add-compute-s3-storage.md)):

* core-api runs each workflow run as a Kubernetes Job with Nextflow (the `refcloud-nextflow` image, from `compute/images/nextflow/`), in the release's compute namespace, `refcloud-compute`;
* Nextflow sends each task to core-api's TES API, which passes it to Funnel; Funnel runs it as Jobs in the same namespace;
* the run's files (its workflow, staged inputs, Nextflow's work directory, outputs and logs) are in **RustFS**, the S3-compatible server that stands in for Amazon S3 locally: core-api and Funnel's workers each use an account of their own, and each run's Nextflow Job gets temporary credentials for its own run only. Funnel's workers copy each task's files in and out of a claim of its own, so the task containers never reach RustFS;
* the chart's connection, `nextflow-funnel-kubernetes-local`, is sized for minikube's default container (2 CPUs, about 6 GB): **one run at a time**, up to 1 GB of tasks in flight, and 1 CPU and 1 GB per task.

**The demo platform:** the run script registers `nextflow-demo-kubernetes` on that connection, with the bundled [`drs-checksum`](../../compute/workflows/nextflow/drs-checksum/README.md) workflow on its allowlist. It sets no per-user limits of its own, so the connection's apply, except 10 core-hours a month. An existing platform is left as it is; to recreate it, `kubectl exec deploy/refcloud-admin-cli -- refcloud compute-platform delete -p nextflow-demo-kubernetes`, then run the script again. Native mode has its own platform, `nextflow-demo`; both modes share the databases, so each shows the other's as unavailable.

**Running the demo:**

1. `./scripts/refcloud-run-helm.sh` (it lists `nextflow-demo-kubernetes`'s WES and TES endpoints).
2. In the researcher UI, request access to **1000 Genomes** and to **Nextflow Demo (Kubernetes)**, then approve both:

   ```bash
   kubectl exec deploy/refcloud-admin-cli -- refcloud visa list-requests
   kubectl exec deploy/refcloud-admin-cli -- refcloud visa approve -u <user id> -v ga4gh_visa:aws_opendata_registry_1000_genomes
   kubectl exec deploy/refcloud-admin-cli -- refcloud visa approve -u <user id> -v ga4gh_visa:compute_platform:nextflow-demo-kubernetes
   ```

3. Copy a passport token from **View Passport Token**, and run `./scripts/refcloud-compute-demo.sh <passport token>` from the repo root. Without `-p`, it uses the healthy demo platform, `nextflow-demo-kubernetes` in helm mode, and trusts both local certificate authorities (Caddy's and mkcert's).

**No internet:** the Nextflow Jobs and task containers can't reach anything outside the cluster (see Network Policies). Workflows that download plugins or files at run time fail here, nf-core/demo's `test` profile among them; `drs-checksum` works. Container images still load, since the node pulls them.

**Run files** live in RustFS, the S3-compatible server in the release's namespace, under `runs/<run_id>/` in its `refcloud-compute` bucket ([spec 0012](../../claude/specs/archive/0012-add-compute-s3-storage.md)). Outputs download through WES. To look around, list them with RustFS's own CLI in its pod: `kubectl exec deploy/rustfs -- sh -c 'HOME=/tmp RC_HOST_l="http://$RUSTFS_ACCESS_KEY:$RUSTFS_SECRET_KEY@127.0.0.1:9000" rc object list l/refcloud-compute/runs/ --recursive'`. They survive `--down`; `./scripts/refcloud-setup.sh --reset` deletes them. Each task's working files are on a claim of its own, deleted when the task ends.

**Following a run:** `kubectl -n refcloud-compute get jobs,pods` shows each run's Nextflow Job (`nextflow-<run id>`) and its tasks' Funnel worker and executor Jobs. The run's own logs are in its run directory (`nextflow.log`, `engine.stdout.log`, `engine.stderr.log`), and through WES.

**Health:** each compute platform's WES service-info reports its health. When it's unhealthy, `kubectl logs deploy/refcloud-api` says why: Funnel unreachable, the storage not writable, or core-api missing a permission in the compute namespace.

### Network Policies

The chart's NetworkPolicies (`charts/app/templates/common/network-policies.yaml`) let each pod accept only the traffic it needs. The researcher UI, docs, core-api and the Kratos and Hydra public APIs accept traffic from the ingress and from the pods that call them. Hydra's admin API accepts only the researcher UI and the `setup-postdeploy` Job, and Kratos's admin API nothing but health probes. The template's header has the full table. A new in-cluster caller (a new internal URL) needs a rule there, or its requests time out.

In minikube, Calico enforces them. To check that a pod can't reach an admin API:

```sh
kubectl run np-check --rm -it --restart=Never --image=busybox:1.37 -- wget -T 5 -qO- http://hydra:4445/health/ready
# times out: blocked (from the researcher UI's pod it would answer)
```

**The compute namespace** (`refcloud-compute`, `charts/app/templates/common/compute-network-policies.yaml`) denies everything, in and out, then allows only what the compute stack needs:

* Funnel's TES API accepts core-api only; its RPC port accepts its workers only;
* Funnel and its workers reach the Kubernetes API and DNS; workers also reach Funnel and RustFS;
* each run's Nextflow Job reaches core-api's port 8080 and RustFS's port 9000, and nothing else, not even DNS (core-api gives it their addresses rather than their names);
* the task containers (Funnel's executors) reach nothing.

**RustFS** (the S3-compatible server that holds compute runs' files; [spec 0012](../../claude/specs/archive/0012-add-compute-s3-storage.md)) runs in the release's namespace and accepts only core-api, its setup Job, and the compute namespace's Nextflow Jobs and Funnel workers.

To check from inside the compute namespace (which enforces the restricted Pod Security level, so the pod needs a restricted security context):

```sh
kubectl -n refcloud-compute run np-check --rm -i --restart=Never --image=busybox:1.37 --overrides='{"spec":{"securityContext":{"runAsNonRoot":true,"runAsUser":65534,"seccompProfile":{"type":"RuntimeDefault"}},"containers":[{"name":"np-check","image":"busybox:1.37","command":["nc","-z","-w","3","1.1.1.1","443"],"securityContext":{"allowPrivilegeEscalation":false,"capabilities":{"drop":["ALL"]}}}]}}'
# fails: blocked
```

So workflow code can't send data out of the cluster, including through DNS lookups. To let a platform's workflows reach specific destinations (e.g. a federated-learning server), list them in `ga4gh.referenceCloud.compute.outgoingAllowlist`, as NetworkPolicy egress rules:

```yaml
# charts/app/values.yaml, then ./scripts/refcloud-run-helm.sh
ga4gh:
  referenceCloud:
    compute:
      outgoingAllowlist:
        - to: [{ipBlock: {cidr: 203.0.113.10/32}}]
          ports: [{protocol: TCP, port: 443}]
```

The Nextflow Jobs and task containers then reach those destinations, and DNS. Locally, destinations are IP ranges (Calico doesn't filter by name).

The namespace also has a ResourceQuota and a LimitRange, computed from the local connection's limits. Every pod there must set CPU and memory requests and limits (the LimitRange gives defaults to pods that don't).

### Teardown

```sh
./scripts/refcloud-run-helm.sh --down   # uninstall the helm release
minikube stop                           # optional: stop the cluster
```

`--down` keeps compute runs' files (RustFS's data claim, `rustfs-data`, and its secrets), as the databases keep the runs: the next install picks them up. `./scripts/refcloud-setup.sh --reset` deletes them. It also deletes the [setup Jobs](#setup-jobs), which uninstalling a release leaves behind, as Helm does with hooks.

### One mode at a time

Native mode (Caddy) and helm mode (`minikube tunnel`) both bind port 443, so only one can run at a time. Stop native mode (Ctrl+C) before starting helm mode, and stop the tunnel before starting native mode. Both modes share the same host databases, so data created in one is visible in the other.

### Admin CLI

The admin CLI runs as an idle pod in the cluster. Run commands with:

```sh
kubectl exec deploy/refcloud-admin-cli -- refcloud <command>
# e.g. kubectl exec deploy/refcloud-admin-cli -- refcloud dataset onboard -d <dataset_id>
```

### Troubleshooting

* **minikube won't start / `kubelet: Stopped`, `apiserver: Stopped`:** the cluster state is broken (e.g. after a Docker Desktop restart or upgrade). Recreate it with `minikube delete && minikube start --driver=docker`, then re-run `./scripts/refcloud-setup.sh`.
* **A pod is refused in the compute namespace (`violates PodSecurity "restricted:latest"`):** the namespace enforces the strictest Pod Security level. A pod you start there yourself, e.g. to check the NetworkPolicies, needs a restricted security context, as in the `np-check` example under Network Policies.
* **New pods stay in `ContainerCreating`, and `kubectl describe pod` shows `plugin type="calico" failed ... connection is unauthorized: Unauthorized`** (e.g. the deploy times out in a migration Job): Calico's network plugin token expired without being renewed, which can happen after the laptop sleeps. Running pods are unaffected. Restart Calico's node agent, which writes a new token: `kubectl --context minikube -n kube-system rollout restart daemonset calico-node`.
* **An image build fails:** see the log path printed by the run script in `$TMPDIR/refcloud-build-logs/`.
* **The deploy fails:** check `kubectl get pods`, `kubectl get jobs` and `kubectl logs <pod>`. A failed migration Job is kept for inspection and replaced on the next deploy. If a setup Job failed, its log ends with an `[ERROR]` line saying what to fix: `kubectl logs job/setup-predeploy`, or `kubectl logs job/setup-postdeploy --all-containers`.
* **Liquibase migration fails with `relation "..." already exists`:** your local database recorded migrations under the old changelog path. Run this once (or `./scripts/refcloud-setup.sh --reset`):
  ```sh
  psql -d refcloudapi -c "UPDATE databasechangelog SET filename = 'dbchangelog.xml' WHERE filename = 'apps/core-api/liquibase/dbchangelog.xml';"
  ```

## Setup Jobs

The chart prepares what it needs itself, on every install and upgrade ([spec 0013](../../claude/specs/active/0013-add-compute-aws-platform.md), Task 2). Two Helm hook Jobs run `refcloud-setup` (`setup/refcloud-setup`, in the `refcloud-setup` image built from `setup/`):

| Job | When | What it checks |
|-|-|-|
| `setup-predeploy` | before the database migrations (`pre-install`, `pre-upgrade`) | the [Secrets](#secrets) the chart expects; each app's database and the login role that owns it, both named after the `username` in its Secret, with the role logging in with the Secret's password; Data Connect's role (read-only, a 30-second statement timeout, no `CREATEDB` or `CREATEROLE`) |
| `setup-postdeploy` | once the release is applied (`post-install`, `post-upgrade`) | the researcher UI's OAuth client in Hydra (it waits for Hydra to be ready). Its last container then runs `refcloud seed` with the admin CLI image, which upserts the Data Connect table types and platform tables |

Each check changes something only when it fails, so on a deployment where everything is intact nothing changes:

* a missing Secret is created with random values. An existing Secret is never changed: one that's missing a key stops the Job, with the key named;
* `kratos-secrets`, `hydra-secrets` and `refcloud-ui-hydra-researcher-client` are created only with a new database. If one is missing while Kratos's or Hydra's database has data, the Job stops and asks for it to be restored: Kratos and Hydra encrypt what they store with the first two, and Hydra keeps the OAuth client's secret;
* a missing database or role is created; a database with another owner gets its role as owner; a role that can't log in with its Secret's password gets that password;
* a missing Hydra client is created from its Secret. For an existing one, the settings the chart decides are updated where they differ (with a JSON Patch, so nothing else on the client changes): the redirect URI, which follows `baseURL` (e.g. after a hostname change), the grant and response types, the scope and the auth method. Lists and the scope compare as sets, so order doesn't count. Its secret isn't checked: Hydra never returns it.

Nothing is ever deleted. Each check is logged as `[OK]` or `[CHANGE]`, a problem setup can't fix as `[ERROR]` (and the deploy fails, before the migrations if it's `setup-predeploy`), and the last line sums up. The Jobs stay until the next deploy replaces them, so their logs can be read:

```sh
kubectl logs job/setup-predeploy
kubectl logs job/setup-postdeploy --all-containers
```

Apps already running read their Secrets only when they start. If `setup-predeploy` created or changed a Secret or a password an app uses (it says so), restart that app once the deploy is done: `kubectl rollout restart deployment/<name>`.

`setup-predeploy` connects to Postgres with the admin login in `refcloud-db-admin`:

* **locally,** `refcloud-setup.sh` creates it with your local Postgres login;
* **elsewhere (AWS),** it's `refcloud_admin`'s login: a role that can create databases and roles, but isn't a superuser, and (from Postgres 16) manages only the roles it creates or is granted. `setup-predeploy` creates the role and the Secret itself, with a random password nobody needs to know, on a deploy where the **bootstrap login** is there: the master user's credentials, in `refcloud-db-bootstrap` ([below](#the-bootstrap-login)). After that deploy, delete `refcloud-db-bootstrap`. Every later deploy connects as `refcloud_admin`.

Whenever `refcloud-db-bootstrap` exists and can connect, `setup-predeploy` connects as the master user and creates or repairs `refcloud_admin`: its password, its `CREATEDB` and `CREATEROLE`, and its `ADMIN` on every app role. A copy that can't connect (e.g. one whose password RDS has rotated since) is ignored, with a reminder to delete it. So the same step repairs the admin login if `refcloud-db-admin` is lost, or if setup reports a role `refcloud_admin` can't manage.

`setup-predeploy` also needs permission to read and create the release's Secrets: its ServiceAccount, Role and RoleBinding are hooks too, and Helm deletes them once the migrations have run. Hydra's admin API accepts `setup-postdeploy` ([Network Policies](#network-policies)).

Datasets aren't onboarded by setup: after a first deploy, onboard them with the [admin CLI](#admin-cli) pod (`refcloud dataset init` and `refcloud dataset onboard`).

## Static Analysis

The chart is checked on every pull request that changes `deploy/helm/` (`.github/workflows/static-analysis-helm.yml`), rendered with its default (local) values. Each tool runs from a Docker image pinned by version and digest, so the only requirement is Docker. Run from the repo root:

| Check | Command |
|-|-|
| All checks | `./scripts/refcloud-static-analysis-helm.sh` |
| Chart linting (`helm lint --strict`) | `./scripts/refcloud-static-analysis-helm.sh lint` |
| Rendered manifests against the Kubernetes API schemas ([kubeconform](https://github.com/yannh/kubeconform)) | `./scripts/refcloud-static-analysis-helm.sh kubeconform` |
| Security misconfigurations ([Trivy](https://trivy.dev/), HIGH and CRITICAL) | `./scripts/refcloud-static-analysis-helm.sh trivy-config` |
| The setup Jobs' script ([shellcheck](https://www.shellcheck.net/)); its Dockerfile is checked with the others (`./scripts/refcloud-static-analysis-deploy-local.sh`) | `./scripts/refcloud-static-analysis-helm.sh shellcheck` |

Accepted Trivy findings are listed in `.trivyignore.yaml`, each scoped to the files it applies to and with a reason.

Every pod runs as a non-root user with a read-only root filesystem (writable `emptyDir` volumes at `/tmp` and wherever else an app writes), no privilege escalation and no Linux capabilities. Every long-running app has liveness and readiness probes.

## Remote Deployment (AWS)

The chart's defaults target local minikube. For AWS, supply overrides at deploy time. For an environment's first deploy, also put RDS's master credentials in the `refcloud-db-bootstrap` Secret ([The bootstrap login](#the-bootstrap-login)): the [setup Jobs](#setup-jobs) then create the admin login, the other secrets, the databases and roles, and the OAuth client.

### Values

| Value | Local default | AWS |
|---|---|---|
| `image.registry` | `""` (images built into minikube) | `<account>.dkr.ecr.<region>.amazonaws.com` |
| `image.tag` | required, no default | lockstep release tag, e.g. `v0.2.0` |
| `image.pullPolicy` | `Never` | `IfNotPresent` (or `Always`) |
| `image.pullSecrets` | `[]` | `[ecr-registry-key]` if nodes can't pull from ECR via IAM |
| `ga4gh.referenceCloud.baseURL` | `refcloud.ga4gh.localhost` | public hostname |
| `ga4gh.referenceCloud.cloudEnvironment` | `local` | `aws` |
| `ga4gh.referenceCloud.aws.certificateARN` | n/a | ACM certificate ARN for the ALB |
| `ga4gh.referenceCloud.compute.connections` | `{}` (compute is off) | the deployment's compute connections, written as in `deploy/local/compute/connections.yml` (credentials named by environment variable, never inline) |
| `ga4gh.referenceCloud.compute.serviceInfo.idSuffix` | `local.helm` | the deployment's own, so WES and TES service-info IDs differ from local ones |
| `ga4gh.referenceCloud.compute.serviceInfo.environment` | `local` | e.g. `prod` |
| `networkPolicy.ingressFrom` | `[]` (the `ingress-nginx` namespace) | **required**: where the ALB connects from. It targets pod IPs from its subnets, e.g. `[{ipBlock: {cidr: <VPC CIDR>}}]` |

**NetworkPolicies on AWS:** the chart's policies only take effect if the cluster enforces them. On EKS Auto Mode, turn on its Network Policy Controller once, with the ConfigMap from [AWS's guide](https://docs.aws.amazon.com/eks/latest/userguide/auto-net-pol.html) (`amazon-vpc-cni` in `kube-system`, `enable-network-policy-controller: "true"`). Deploy the chart (with `networkPolicy.ingressFrom`) before or after; then check that the site still works and the pods stay ready. If something breaks, `--set networkPolicy.enabled=false` removes the policies.

Images are pulled from the ECR repositories CI pushes to: `refcloud-core-api`, `refcloud-core-api-liquibase`, `refcloud-researcher-ui`, `refcloud-docs`, `refcloud-admin-cli`, `refcloud-nextflow` (the Nextflow image compute Jobs run; `compute/images/nextflow/`), and `refcloud-setup` (the setup Jobs' image; `setup/`). The Ory Kratos and Hydra versions are pinned in `charts/app/templates/_helpers.tpl`.

Example:

```sh
helm upgrade --install refcloud charts/app \
  --set image.registry=<account>.dkr.ecr.<region>.amazonaws.com \
  --set image.tag=<tag> \
  --set image.pullPolicy=IfNotPresent \
  --set ga4gh.referenceCloud.baseURL=<host> \
  --set ga4gh.referenceCloud.cloudEnvironment=aws \
  --set ga4gh.referenceCloud.aws.certificateARN=<arn> \
  --set-json 'networkPolicy.ingressFrom=[{"ipBlock":{"cidr":"<VPC CIDR>"}}]'
```

If the cluster needs an image pull secret for ECR, create it and add `--set 'image.pullSecrets={ecr-registry-key}'`:

```sh
kubectl create secret docker-registry ecr-registry-key \
  --docker-server=<account>.dkr.ecr.<region>.amazonaws.com \
  --docker-username=AWS \
  --docker-password=$(aws ecr get-login-password --region <region>) \
  --namespace=<namespace>
```

### Secrets

The chart expects these secrets in the release namespace. Locally, `refcloud-setup.sh` creates them all. Everywhere, `setup-predeploy` creates any that are missing, except `refcloud-db-bootstrap` and the local-only ones, with random values, and never changes an existing one (see [Setup Jobs](#setup-jobs)).

| Secret | Keys |
|---|---|
| `refcloud-db-bootstrap` | `host`, `port`, `username`, `password`: the master user's login, for one deploy only ([The bootstrap login](#the-bootstrap-login)). Never needed locally |
| `refcloud-db-admin` | `host`, `port`, `username`, `password`: the admin login `setup-predeploy` connects as: `refcloud_admin`, created with the bootstrap login (locally, your Postgres login) |
| `common-db-info` | `host`, `port` |
| `kratos-db-credentials` | `username`, `password` |
| `hydra-db-credentials` | `username`, `password` |
| `refcloud-api-db-credentials` | `username`, `password` |
| `refcloud-api-data-connect-db-credentials` | `username`, `password` (read-only `data_connect_reader` role) |
| `kratos-secrets` | `cookie`, `cipher` |
| `hydra-secrets` | `system-secret`, `pairwise-salt` |
| `refcloud-ui-hydra-researcher-client` | `id`, `secret` |
| `refcloud-api-kratos-webhook` | `username`, `password` |
| `refcloud-tls` | TLS secret (local only; AWS uses the ACM certificate) |
| `refcloud-compute-s3-root` | `access-key-id`, `secret-access-key`: RustFS's root account, used only by its setup Job (local only) |
| `refcloud-compute-s3-core-api` | `access-key-id`, `secret-access-key`: core-api's RustFS user (local only) |
| `refcloud-compute-s3-funnel` | `access-key-id`, `secret-access-key`: Funnel's RustFS user, which the chart copies into the compute namespace for its workers (local only) |

### The bootstrap login

Once per environment, for its first deploy, and again only to repair the admin login, give `setup-predeploy` the RDS master user's credentials. RDS keeps them in Secrets Manager (and rotates the password), so copy them straight from there; the password never shows in your terminal:

```sh
aws secretsmanager get-secret-value --secret-id <master user secret ARN> --query SecretString --output text \
  | jq --arg host '<RDS endpoint>' '{apiVersion: "v1", kind: "Secret", type: "Opaque",
      metadata: {name: "refcloud-db-bootstrap"},
      stringData: {host: $host, port: "5432", username: .username, password: .password}}' \
  | kubectl create --namespace=<namespace> -f -
```

(`aws rds describe-db-instances --db-instance-identifier <instance> --query 'DBInstances[0].MasterUserSecret.SecretArn'` gives the secret's ARN.)

Deploy, check `setup-predeploy`'s log (`kubectl logs job/setup-predeploy`: it ends with what it created), then delete the copy:

```sh
kubectl delete secret refcloud-db-bootstrap --namespace=<namespace>
```

Setup never needs the master password again, so rotating it now (`aws secretsmanager rotate-secret --secret-id <master user secret ARN>`) makes any copy useless. Do that only if nothing else connects with the master login, e.g. another project's app on the same instance: check that RDS already rotates it (`aws secretsmanager describe-secret --secret-id <ARN> --query RotationEnabled`), since then anything using it reads it from Secrets Manager.

The admin CLI pod in AWS is connected to the production database. Follow the guardrails in [`apps/admin-cli/CLAUDE.md`](../../apps/admin-cli/CLAUDE.md) before running destructive commands.
