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
* creates or updates every secret the chart needs (see [Secrets](#secrets)), using the same values as native mode

`--reset` also deletes the TLS certificate, uninstalls the helm release and deletes the refcloud secrets before recreating them (starting minikube first if needed). The cluster itself is not deleted.

The Kubernetes section is skipped with a warning if `minikube`, `kubectl`, `helm` or `mkcert` is missing, so native-only contributors don't need them.

### Run

```sh
./scripts/refcloud-run-helm.sh
```

The run script:

1. fails fast if port 443 is in use (e.g. native mode's Caddy, or another `minikube tunnel`)
2. computes the lockstep image tag: `git describe --tags --match 'v[0-9]*' --always`, plus `-uncommitted` if the working tree has any changes (including untracked files)
3. builds all app images, and the Nextflow image compute runs, into minikube (`minikube image build`); build logs go to `$TMPDIR/refcloud-build-logs/`. core-api and the admin CLI build from the repo root, so they can bundle `compute/workflows/`
4. creates the compute run storage directory on the minikube node (`/data/refcloud-compute/refcloud`, owned by user 10001), which core-api mounts
5. runs `helm upgrade --install refcloud` with that tag. Database migrations (Liquibase, Kratos, Hydra) run first as Helm hooks
6. restarts the app deployments (so rebuilt images with an unchanged tag are picked up) and waits for them to be ready
7. prints the service URLs and runs `minikube tunnel` in the foreground, which prompts for your password to bind port 443

The refcloud is then available at `https://refcloud.ga4gh.localhost` and `https://docs.refcloud.ga4gh.localhost`.

Ctrl+C stops the tunnel only; the release keeps running, so the next run is fast. Run the script again to reconnect.

### Network Policies

The chart's NetworkPolicies (`charts/app/templates/common/network-policies.yaml`) let each pod accept only the traffic it needs. The researcher UI, docs, core-api and the Kratos and Hydra public APIs accept traffic from the ingress and from the pods that call them. Hydra's admin API accepts only the researcher UI, and Kratos's admin API nothing but health probes. The template's header has the full table. A new in-cluster caller (a new internal URL) needs a rule there, or its requests time out.

In minikube, Calico enforces them. To check that a pod can't reach an admin API:

```sh
kubectl run np-check --rm -it --restart=Never --image=busybox:1.37 -- wget -T 5 -qO- http://hydra:4445/health/ready
# times out: blocked (from the researcher UI's pod it would answer)
```

**The compute namespace** (`refcloud-compute`, `charts/app/templates/common/compute-network-policies.yaml`) denies everything, in and out, then allows only what the compute stack needs:

* Funnel's TES API accepts core-api only; its RPC port accepts its workers only;
* Funnel and its workers reach the Kubernetes API and DNS; workers also reach Funnel;
* each run's Nextflow Job reaches core-api's port 8080 and nothing else, not even DNS (core-api gives it its own address rather than its name);
* the task containers (Funnel's executors) reach nothing.

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
* **`helm upgrade` warns `would violate PodSecurity "restricted:latest": ... hostPath`:** expected. Funnel's Deployment in the compute namespace (`refcloud-compute`) uses the compute storage on the minikube node, which the strictest Pod Security level doesn't allow, so that namespace warns rather than enforces.
* **New pods stay in `ContainerCreating`, and `kubectl describe pod` shows `plugin type="calico" failed ... connection is unauthorized: Unauthorized`** (e.g. the deploy times out in a migration Job): Calico's network plugin token expired without being renewed, which can happen after the laptop sleeps. Running pods are unaffected. Restart Calico's node agent, which writes a new token: `kubectl --context minikube -n kube-system rollout restart daemonset calico-node`.
* **An image build fails:** see the log path printed by the run script in `$TMPDIR/refcloud-build-logs/`.
* **The deploy fails:** check `kubectl get pods`, `kubectl get jobs` and `kubectl logs <pod>`. A failed migration Job is kept for inspection and replaced on the next deploy.
* **Liquibase migration fails with `relation "..." already exists`:** your local database recorded migrations under the old changelog path. Run this once (or `./scripts/refcloud-setup.sh --reset`):
  ```sh
  psql -d refcloudapi -c "UPDATE databasechangelog SET filename = 'dbchangelog.xml' WHERE filename = 'apps/core-api/liquibase/dbchangelog.xml';"
  ```

## Static Analysis

The chart is checked on every pull request that changes `deploy/helm/` (`.github/workflows/static-analysis-helm.yml`), rendered with its default (local) values. Each tool runs from a Docker image pinned by version and digest, so the only requirement is Docker. Run from the repo root:

| Check | Command |
|-|-|
| All checks | `./scripts/refcloud-static-analysis-helm.sh` |
| Chart linting (`helm lint --strict`) | `./scripts/refcloud-static-analysis-helm.sh lint` |
| Rendered manifests against the Kubernetes API schemas ([kubeconform](https://github.com/yannh/kubeconform)) | `./scripts/refcloud-static-analysis-helm.sh kubeconform` |
| Security misconfigurations ([Trivy](https://trivy.dev/), HIGH and CRITICAL) | `./scripts/refcloud-static-analysis-helm.sh trivy-config` |

Accepted Trivy findings are listed in `.trivyignore.yaml`, each scoped to the files it applies to and with a reason.

Every pod runs as a non-root user with a read-only root filesystem (writable `emptyDir` volumes at `/tmp` and wherever else an app writes), no privilege escalation and no Linux capabilities. Every long-running app has liveness and readiness probes.

## Remote Deployment (AWS)

The chart's defaults target local minikube. For AWS, supply overrides at deploy time and create the secrets yourself.

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

Images are pulled from the ECR repositories CI pushes to: `refcloud-core-api`, `refcloud-core-api-liquibase`, `refcloud-researcher-ui`, `refcloud-docs`, `refcloud-admin-cli`, and `refcloud-nextflow` (the Nextflow image compute Jobs run; `compute/images/nextflow/`). The Ory Kratos and Hydra versions are pinned in `charts/app/templates/_helpers.tpl`.

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

The chart expects these secrets to exist in the release namespace. Locally, `refcloud-setup.sh` creates them; in AWS, create them with production values.

| Secret | Keys |
|---|---|
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

The admin CLI pod in AWS is connected to the production database. Follow the guardrails in [`apps/admin-cli/CLAUDE.md`](../../apps/admin-cli/CLAUDE.md) before running destructive commands.
