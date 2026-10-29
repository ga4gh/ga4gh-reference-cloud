# GA4GH Reference Cloud: Admin CLI

Admin CLI for the GA4GH Reference Cloud.

**For instructions on how to run the full reference cloud, please see the main [README](../../README.md).**

To run the admin CLI by itself, see below.

* Install python environment and dependencies: `uv sync`
* Run admin CLI: `uv run refcloud`

## Available Commands

### Seed Command

First, run `uv run refcloud seed` to seed the database with preliminary data (e.g. data models and internal queries for data connect API)

### Dataset Commands

The dataset commands can be used to manage the lifecycle of preconfigured datasets. Commands include:

| Command | Example | Explanation |
|-|-|-|
| `init` | `uv run refcloud dataset init -d {dataset_id}` | Initializes a dataset within the database. Must be run first. |
| `onboard` | `uv run refcloud dataset onboard -d {dataset_id}` | Batch loads data objects (e.g. DRS Objects, Data Connect Tables) into the specified dataset. Data is pre-defined per dataset. |
| `reset` | `uv run refcloud dataset reset -d {dataset_id}` | Removes data from an existing dataset, preserving the dataset itself as well as the researchers authorized to access it. |
| `delete` | `uv run refcloud dataset reset -d {dataset_id}` | Deletes a dataset, including all associated data objects and researcher authorizations. |

#### Available Datasets

The following preconfigured datasets are currently available:

| Dataset ID | Description |
|-|-|
| `aws_opendata_registry_1000_genomes` | Sourced from AWS Registry of Open Data. The 1000 Genomes Project is an international collaboration which has established the most detailed catalogue of human genetic variation, including SNPs, structural variants, and their haplotype context. The final phase of the project sequenced more than 2500 individuals from 26 different populations around the world and produced an integrated set of phased haplotypes with more than 80 million variants for these individuals. |
| `ga4gh_refget_sequences_v2_conformance` | Test sequences for GA4GH Refget Sequences v2 Conformance |

## Configuration

Configure the CLI via the following environment variables:

| Variable Name | Description | Default |
|---------------|-------------|---------|
| `POSTGRES_HOST` | PostgreSQL host | `127.0.0.1` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` |
| `POSTGRES_DB` | PostgreSQL database name | `refcloudapi` |
| `POSTGRES_USER` | PostgreSQL user name | `refcloudapi` |
| `POSTGRES_PASSWORD` | PostgreSQL user password | `secret` |
