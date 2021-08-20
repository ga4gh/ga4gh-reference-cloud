---
sidebar_position: 2
---

# Configuring Database Connection

All Starter Kit web services (i.e. [core applications](../starter-kit-apis/overview) based on GA4GH API specifications) are configurable with a YAML config file. Part of the YAML config file includes a `databaseProps` object, which modifies the behavior of database connection properties.

`databaseProps` can be modified to connect to any database that has been set up with the appropriate table schema for the starter kit service being run. For more information on setting up a database with the correct table schema, see [Using Starter Kit Utils](./using-starter-kit-utils).

The Starter Kit supports connections to either **SQLite** or **PostgreSQL** based databases.

### Configurable `databaseProps` properties

A full explanation of configurable database connectivity properties is outlined below.

| Attribute | Description | Data Type | Default |
|-----------|-------------|-----------|---------|
| `url` | A valid `JDBC` database connection URL, indicating the database type (**SQLite** or **PostgreSQL**) and location. The default URL connects to the test SQLite database bundled within the docker container. For more information, see [Constructing a valid JDBC URL](#constructing-a-valid-jdbc-url) | string (JDBC URL)| `jdbc:sqlite:./ga4gh-starter-kit.dev.db`|
| `username` | Database user with sufficient privileges to access required tables | string | |
| `password` | Password for the above `username` | string | |
| `poolSize` | Total connections to the database | integer | 1 |
| `showSQL` | Outputs SQL to console for each database operation | boolean | true |

### Constructing a Valid JDBC URL

#### SQLite

SQLite JDBC URLs are formatted as follows:
```
jdbc:sqlite:${SQLITE_DATABASE_FILE_PATH}
```

where `${SQLITE_DATABASE_FILE_PATH}` is the (absolute or relative) path to the local file acting as the database. For example, if the database file is at `/data/database/starter-kit.db`, the JDBC URL would be:
```
jdbc:sqlite:/data/database/starter-kit.db
```

#### PostgreSQL

PostgreSQL JDBC URLs can take one of the following forms:
* `jdbc:postgresql:${DATABASE}`
* `jdbc:postgresql://${HOST}/${DATABASE}`
* `jdbc:postgresql://${HOST}:${PORT}/${DATABASE}`

where:

* `${DATABASE}` is the database name,
* `${HOST}` is the host/domain name where the database is running. Does not need to be specified if database is running at the default `localhost`
* `${PORT}` is the networking port the database host is listening on. Does not need to be specified if it is listening on the default port `5432`.

For example, if the target database was named "StarterKitDB", was running at "drs-db-starterkit.example.com" on port `5555`, the JDBC URL would be:
```
jdbc:postgresql://drs-db-starterkit.example.com:5555/StarterKitDB
```

### Example `databaseProps`

The following snippet displays an example `databaseProps` YAML configuration to configure the behavior of a starter kit service. A `drs` service is used in the example, but the `serverProps` object is equally applicable to any starter kit service.

```yaml
drs:
  databaseProps:
    url: jdbc:postgresql://starter-kit-demo-db.ga4gh.org:5555/drsdb
    username: ga4gh-admin
    password: makeSureThisIsSecure123
    poolSize: 8
    showSQL: false
```