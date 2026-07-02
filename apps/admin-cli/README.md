# Refcloud Data Onboarding
Data onboarding CLI for the GA4GH Reference Cloud

## Usage (Local Development)

* Prerequisites (ensure these are installed on your machine)
  * Python v3.14+ (currently developed using v3.14.3)
  * uv
* to install package locally (in current directory)
  * run `uv sync`
  * run application with `uv run refcloud`
* to install package globally on your system
  * run `uv tool install .`
  * run `uv tool update-shell`
  * run application with `refcloud`

## Configuration

Configure the UI app via the following environment variables

| Variable Name | Description | Example |
|---------------|-------------|---------|
| `POSTGRES_HOST` | PostgreSQL host | `127.0.0.1` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` |
| `POSTGRES_DB` | PostgreSQL database name | `refcloudapi` |
| `POSTGRES_USER` | PostgreSQL user name | `refcloudapi` |
| `POSTGRES_PASSWORD` | PostgreSQL user password | `secret` |

## Issues

For any issues relating to the UI, please create an issue in the [GA4GH Reference Cloud planning repo](https://github.com/ga4gh/ga4gh-reference-cloud/issues). Please do not create issues in this repo as they will not be monitored.