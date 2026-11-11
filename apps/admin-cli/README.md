# GA4GH Reference Cloud: Admin CLI

Admin CLI for the GA4GH Reference Cloud.

**For instructions on how to run the full reference cloud, please see the main [README](../../README.md).**

To run the admin CLI by itself, see below.

**Note:** you MUST install all [required prerequisites](../../README.md#getting-started) AND run the [setup script](../../scripts/refcloud-setup.sh) first to create required resources

## Run

* Install python environment and dependencies: `uv sync`
* Run admin CLI: `uv run refcloud`

### Seed Command

First, run `uv run refcloud seed` to seed the database with preliminary data (e.g. data models and internal queries for data connect API)

### Dataset Commands

The dataset commands can be used to manage the lifecycle of preconfigured datasets. Commands include:

| Command | Example | Explanation |
|-|-|-|
| `init` | `uv run refcloud dataset init -d {dataset_id}` | Initializes a dataset within the database. Must be run first. |
| `onboard` | `uv run refcloud dataset onboard -d {dataset_id}` | Batch loads data objects (e.g. DRS Objects, Data Connect Tables) into the specified dataset. Data is pre-defined per dataset. |
| `reset` | `uv run refcloud dataset reset -d {dataset_id}` | Removes data from an existing dataset, preserving the dataset itself as well as the researchers authorized to access it. |
| `delete` | `uv run refcloud dataset delete -d {dataset_id}` | Deletes a dataset, including all associated data objects and researcher authorizations. |

### Compute Platform Commands

A compute platform is a visa-gated WES/TES service that researchers can request access to, backed by a **connection** (an engine + executor setup and its pooled limits) defined in the connections file (see Configuration). The commands check every platform against that file: an unknown connection, or a per-user limit above the connection's ceiling, is refused.

| Command | Example | Explanation |
|-|-|-|
| `init` | `uv run refcloud compute-platform init -p nextflow-demo --name "Nextflow Demo" --description "..." --connection nextflow-funnel-docker-local --max-concurrent-runs 2` | Creates a platform and its visa (`ga4gh_visa:compute_platform:{id}`). It starts **disabled**. Per-user limits are optional (unset = only the connection's ceiling applies): `--max-concurrent-runs`, `--max-task-cpu-cores`, `--max-task-memory-gb`, `--max-task-disk-gb`, `--max-run-duration-seconds`, `--monthly-core-hours`. |
| `list` | `uv run refcloud compute-platform list` | Lists platforms with their connection (flagged `MISSING` if it's no longer in the connections file), enabled state, allowlist size, per-user limits and visa. |
| `update` | `uv run refcloud compute-platform update -p nextflow-demo --max-task-memory-gb 2 --max-concurrent-runs none` | Changes the name (its visa is renamed too), description, connection or per-user limits. `none` clears a limit. |
| `enable` / `disable` | `uv run refcloud compute-platform enable -p nextflow-demo` | Offers or stops offering a platform to researchers. Enabling needs the platform's connection in the connections file and at least one allowlisted workflow. |
| `delete` | `uv run refcloud compute-platform delete -p nextflow-demo` | Deletes a platform with its visa, every researcher's access request for it, and its allowlist (asks for confirmation; `-y` skips it). |
| `allowlist add` | see below | Allowlists a workflow, pinned to a commit. |
| `allowlist list` | `uv run refcloud compute-platform allowlist list -p nextflow-demo` | Lists allowlisted workflows, what each is pinned to, its engine settings, and the exact `workflow_url` researchers submit. |
| `allowlist remove` | `uv run refcloud compute-platform allowlist remove -p nextflow-demo -w 3` | Removes an entry by its ID (from `allowlist list`). |

Researchers can only run allowlisted workflows, and a WES submission's `workflow_url` must match an entry exactly. Each entry comes from exactly one source:

| Source | Example | `workflow_url` researchers submit |
|-|-|-|
| git (GitHub) | `--git-repository https://github.com/nf-core/demo --ref 1.0.2` | `https://github.com/nf-core/demo/blob/{commit}/main.nf` |
| TRS (e.g. Dockstore; GitHub-hosted workflows only) | `--trs-descriptor-url 'https://dockstore.org/api/ga4gh/trs/v2/tools/%23workflow%2Fgithub.com%2Fnf-core%2Fdemo/versions/1.0.2/NFL/descriptor'` | the descriptor URL |
| bundled (shipped in `compute/workflows/nextflow/`) | `--bundled drs-checksum` | `refcloud:bundled/drs-checksum` |

* `--ref` is a tag or a full 40-character commit sha. Tags (and TRS versions) are resolved to their commit when the entry is added, using `git ls-remote`, so **git must be installed**. Branches are refused because they move.
* `--main-script` (default `main.nf`) sets the main script for git and TRS sources.
* Engine settings are fixed by the admin, not the researcher: repeat `--profile test` for engine profiles, and `--engine-env NXF_SYNTAX_PARSER=v1` for engine environment variables.
* Only GitHub-hosted repositories are currently supported; other repository systems (e.g. GitLab, Bitbucket) are refused with an error.

### Visa Commands

Researchers request access to a controlled resource (e.g. a dataset) through the researcher UI. Each request is tied to the resource's passport visa and starts as `Requested`. The visa commands let an admin see every visa in the refcloud and process those requests:

| Command | Example | Explanation |
|-|-|-|
| `list` | `uv run refcloud visa list` | Lists every visa: its ID, name, description, the resource it gates, and how many access requests it has in each status. |
| `list-requests` | `uv run refcloud visa list-requests` | Lists access requests (user ID, visa, status, and when the status last changed). Shows `Requested` (pending) requests by default; filter with `-s {status\|all}` and `-v {visa_id}`. |
| `approve` | `uv run refcloud visa approve -u {user_id} -v {visa_id}` | Approves a pending request. Also restores access after a request was denied or revoked (researchers can't re-request on their own). |
| `deny` | `uv run refcloud visa deny -u {user_id} -v {visa_id}` | Denies a pending request. |
| `revoke` | `uv run refcloud visa revoke -u {user_id} -v {visa_id}` | Revokes an approved request. |

Users are identified by their user ID (the `sub` of their token). A transition that doesn't apply to a request's current status (e.g. revoking a request that was never approved) is refused with an explanation, and nothing changes.

#### Available Datasets

The following preconfigured datasets are currently available:

| Dataset ID | Description |
|-|-|
| `aws_opendata_registry_1000_genomes` | Sourced from AWS Registry of Open Data. The 1000 Genomes Project is an international collaboration which has established the most detailed catalogue of human genetic variation, including SNPs, structural variants, and their haplotype context. The final phase of the project sequenced more than 2500 individuals from 26 different populations around the world and produced an integrated set of phased haplotypes with more than 80 million variants for these individuals. |
| `ga4gh_refget_sequences_v2_conformance` | Test sequences for GA4GH Refget Sequences v2 Conformance |

## Static Analysis

Formatting, linting (including security rules), type checks, and a dependency vulnerability audit run in CI on every pull request that changes `apps/admin-cli/`, and weekly. The Python tools are pinned in the `dev` dependency group, so `uv sync` installs the same versions CI uses; the audit runs Trivy from a pinned Docker image, so it needs only Docker. Run them locally before opening a PR:

| Check | Command |
|-|-|
| Formatting ([Ruff](https://docs.astral.sh/ruff/formatter/)) | `uv run ruff format --check` |
| Linting and security rules ([Ruff](https://docs.astral.sh/ruff/linter/)) | `uv run ruff check` |
| Type checking ([Pyright](https://microsoft.github.io/pyright/)) | `uv run pyright` |
| Dependency vulnerabilities in `uv.lock` ([Trivy](https://trivy.dev/), HIGH and CRITICAL) | `./scripts/audit-deps.sh` |

To fix formatting and auto-fixable lint errors: `uv run ruff format && uv run ruff check --fix`

To fix a vulnerable dependency, upgrade it in the lock: `uv lock --upgrade-package <name>`. A vulnerability with no usable fix can be accepted in `.trivyignore`, with a reason.

## Configuration

Configure the CLI via the following environment variables:

| Variable Name | Description | Default |
|---------------|-------------|---------|
| `POSTGRES_HOST` | PostgreSQL host | `127.0.0.1` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` |
| `POSTGRES_DB` | PostgreSQL database name | `refcloudapi` |
| `POSTGRES_USER` | PostgreSQL user name | `refcloudapi` |
| `POSTGRES_PASSWORD` | PostgreSQL user password | `secret` |
| `REFCLOUD_COMPUTE_CONNECTIONS_FILE` | Compute connections file, shared with core-api | `../../deploy/local/compute/connections.yml` |
