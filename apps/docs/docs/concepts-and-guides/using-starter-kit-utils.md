---
sidebar_position: 4
---

# Using Starter Kit Utils

Current Starter Kit Utils Release/Tag **0.1.0**

[GA4GH Starter Kit Utils](#) provides a commandline interface to set up resources (such as databases) that are associated with the core Starter Kit APIs, and are necessary for them to function.

## Install

Use [DockerHub](#) to pull the latest Starter Kit Utils Docker image:

```
docker image pull ga4gh/ga4gh-starter-kit-utils:0.1.0
```

[Browse list of available tags](#)

## Usage

Use `docker run` to run the utils app. For example:

```
docker run ga4gh/ga4gh-starter-kit-utils:0.1.0
```

will run the utils app but display an error message, because no specific command was provided.

```
docker run ga4gh/ga4gh-starter-kit-utils:0.1.0 -h
```

will display the default help message, including information about was commands are available.

## Commands

### database

Perform a variety of database setup operations to make a SQLite or PostgreSQL database suitable for storing GA4GH models.

```
docker run ga4gh/ga4gh-starter-kit-utils:0.1.0 database -h
```

displays the available subcommands under `database`

#### database list-apis

**Lists the GA4GH APIs for which it is possible to apply a database migration**

Example:
```
docker run ga4gh/ga4gh-starter-kit-utils:0.1.0 database list-apis
```

#### database list-migrations

**View all available database migrations for a given GA4GH API**

Arguments:

| Argument | Description | Example |
|----------|-------------|---------|
| `API_SIGNATURE` | GA4GH API name (use 'list-apis' to view full list) | drs |

Template and Example
```
docker run ga4gh/ga4gh-starter-kit-utils:0.1.0 database list-migrations ${API_SIGNATURE}
docker run ga4gh/ga4gh-starter-kit-utils:0.1.0 database list-migrations drs
```

#### database create-tables

**Create required tables for a GA4GH API at target database**

Arguments:

| Argument | Description | Example |
|----------|-------------|---------|
| `SOURCE` | Table SQL source to apply. Can be a either a valid API migration signature (e.g. drs@1.0.0), URL (e.g. https://somesite.com/drs-tables.sql), or file path (e.g. ./drs-tables.sql). Use 'list-migrations' to view available migrations | `drs@1.0.0`

Options:

| Option | Description | Example | Required? |
|--------|-------------|---------|----------|
| `-d` / `--db-url` | Valid `jdbc://` database connection URL | jdbc:sqlite:./ga4gh-starter-kit.dev.db | `true` |
| `-u` / `--username` | Username that can modify the target database | someuser123 | `false` |
| `-p` / `--password` | Password for the above username | aSecurePassword | `false` |

Example
```
docker run -v `pwd`:/db ga4gh/ga4gh-starter-kit-utils:0.1.0 database create-tables -d jdbc:sqlite:/db/ga4gh-demo.db drs@0.1.9
```

**Note**: for SQLite-based databases on host machine, you may need to mount the target directory into the docker container with `-v` 

#### database add-test-dataset

**Populate database tables with test dataset for a particular GA4GH API**

Arguments:

| Argument | Description | Example |
|----------|-------------|---------|
| `SOURCE` | Table SQL source to apply. Can be a either a valid API migration signature (e.g. drs@1.0.0), URL (e.g. https://somesite.com/drs-tables.sql), or file path (e.g. ./drs-tables.sql). Use 'list-migrations' to view available migrations | `drs@1.0.0`

Options:

| Option | Description | Example | Required? |
|--------|-------------|---------|----------|
| `-d` / `--db-url` | Valid `jdbc://` database connection URL | jdbc:sqlite:./ga4gh-starter-kit.dev.db | `true` |
| `-u` / `--username` | Username that can modify the target database | someuser123 | `false` |
| `-p` / `--password` | Password for the above username | aSecurePassword | `false` |

Example
```
docker run -v `pwd`:/db ga4gh/ga4gh-starter-kit-utils:0.1.0 database add-test-dataset -d jdbc:sqlite:/db/ga4gh-demo.db drs@0.1.9
```

**Note**: for SQLite-based databases on host machine, you may need to mount the target directory into the docker container with `-v` 