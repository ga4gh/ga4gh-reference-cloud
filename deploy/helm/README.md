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

* starts minikube (docker driver) if it isn't running and enables the `ingress` addon
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
3. builds all app images into minikube (`minikube image build`); build logs go to `$TMPDIR/refcloud-build-logs/`
4. runs `helm upgrade --install refcloud` with that tag. Database migrations (Liquibase, Kratos, Hydra) run first as Helm hooks
5. restarts the app deployments (so rebuilt images with an unchanged tag are picked up) and waits for them to be ready
6. prints the service URLs and runs `minikube tunnel` in the foreground, which prompts for your password to bind port 443

The refcloud is then available at `https://refcloud.ga4gh.localhost` and `https://docs.refcloud.ga4gh.localhost`.

Ctrl+C stops the tunnel only; the release keeps running, so the next run is fast. Run the script again to reconnect.

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
* **An image build fails:** see the log path printed by the run script in `$TMPDIR/refcloud-build-logs/`.
* **The deploy fails:** check `kubectl get pods`, `kubectl get jobs` and `kubectl logs <pod>`. A failed migration Job is kept for inspection and replaced on the next deploy.
* **Liquibase migration fails with `relation "..." already exists`:** your local database recorded migrations under the old changelog path. Run this once (or `./scripts/refcloud-setup.sh --reset`):
  ```sh
  psql -d refcloudapi -c "UPDATE databasechangelog SET filename = 'dbchangelog.xml' WHERE filename = 'apps/core-api/liquibase/dbchangelog.xml';"
  ```

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

Images are pulled from the ECR repositories CI pushes to: `refcloud-core-api`, `refcloud-core-api-liquibase`, `refcloud-researcher-ui`, `refcloud-docs`, `refcloud-admin-cli`. The Ory Kratos and Hydra versions are pinned in `charts/app/templates/_helpers.tpl`.

Example:

```sh
helm upgrade --install refcloud charts/app \
  --set image.registry=<account>.dkr.ecr.<region>.amazonaws.com \
  --set image.tag=<tag> \
  --set image.pullPolicy=IfNotPresent \
  --set ga4gh.referenceCloud.baseURL=<host> \
  --set ga4gh.referenceCloud.cloudEnvironment=aws \
  --set ga4gh.referenceCloud.aws.certificateARN=<arn>
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
