---
sidebar_position: 2
---

# Starter Kit Data Connect Configuration

The Starter Kit Data Connect service can be configured with modifiable properties. All property configuration is done via a YAML config file that is supplied to the program at runtime via a `-c ${CONFIG_FILE}` option. This section outlines the structure of the YAML config.

The config file MUST start with a single root object named `dataConnectConfig`. All sub-properties are nested under `dataConnectConfig`.

### Configurable `dataConnectConfig` properties

| Attribute | Description | Links |
|-----------|-------------|------|
| `serverProps` | Modifies the behavior of high-level server application properties, such as listening port(s), logging, and CORS headers. | See [`serverProps`](../../concepts-and-guides/configuring-webservice-properties) for more info.
| `databaseProps` | Connect to a target database that has been set up with the prerequisite table schema for Starter Kit Data Connect | See [`databaseProps`](../../concepts-and-guides/configuring-database-connection) for more info. |
| `serviceInfo` | Display custom information about the deployed web service via the `/service-info` endpoint | See [`serviceInfo`](../../concepts-and-guides/configuring-service-info) for more info on configuring a custom Service Info response. See [`Data Connect Service Info Defaults`](#data-connect-service-info-defaults) for the default service info values for Starter Kit Data Connect deployments. |

### Data Connect Service Info Defaults

The following table lists the default attribute values returned over `/service-info` for Starter Kit Data Connect services.

| Attribute | Default Value |
|-----------|---------------|
| `id` | org.ga4gh.starterkit.dataconnect |
| `name` | GA4GH Starter Kit Data Connect Service |
| `description` | Starter Kit implementation of the  Data Connect API specification. Gives researchers access to the data  model of given datasets/tables, and enables them to perform search  queries on the datasets using the model. |
| `contactUrl` | mailto:info@ga4gh.org |
| `documentationUrl` | https://github.com/ga4gh/ga4gh-starter-kit-data-connect |
| `createdAt` | 2022-04-27T09:00:00Z |
| `updatedAt` | 2022-04-27T09:00:00Z |
| `environment` | test |
| `version` | 0.1.0 |
| `organization.name` | Global Alliance for Genomics and Health |
| `organization.url` | https://ga4gh.org |

### Example Starter Kit Data Connect Config

The following snippet displays an example complete YAML configuration for a deployment of the Starter Kit Data Connect service.

```yaml
dataConnectConfig:
  serverProps:
    scheme: https
    hostname: data-connect-api-starterkit.example.com
    publicApiPort: 80
    adminApiPort: 7000
  databaseProps:
    url: jdbc:postgresql://starter-kit-demo-db.ga4gh.org:5555/dataconnectdb
    username: ga4gh-admin
    password: makeSureThisIsSecure123
  serviceInfo:
    id: org.ga4gh.data-connect.public-data-connect
    name: Genomics Public Data Connect Service
    description: Allows researchers to discover and search biomedical data according to the Data Connect Specification
  ```