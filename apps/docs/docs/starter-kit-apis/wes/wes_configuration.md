---
sidebar_position: 2
---

# Starter Kit WES Configuration

The Starter Kit WES service can be configured with modifiable properties. All property configuration is done via a YAML config file that is supplied to the program at runtime via a `-c ${CONFIG_FILE}` option. This section outlines the structure of the YAML config.

The config file MUST start with a single root object named `wes`. All sub-properties are nested under `wes`.

### Configurable `wes` properties

| Attribute | Description | Links |
|-----------|-------------|-------|
| `serverProps` | Modifies the behavior of high-level server application properties, such as listening port(s), logging, and CORS headers. | See [`serverProps`](../../concepts-and-guides/configuring-webservice-properties) for more info |
| `databaseProps` | Connect to a target database that has been set up with the prerequisite table schema for Starter Kit WES | See [`databaseProps`](../../concepts-and-guides/configuring-database-connection) for more info. |
| `serviceInfo` | Display custom information about the deployed web service via the `/service-info` endpoint | See [`serviceInfo`](../../concepts-and-guides/configuring-service-info) for more info on configuring a custom Service Info response. See [`WES Service Info Defaults`](#wes-service-info-defaults) for the default service info values for Starter Kit WES deployments. |

### WES Service Info Defaults

The following table lists the default attribute values returned over `/service-info` for Starter Kit WES services.

| Attribute | Default Value |
|-----------|---------------|
| `id` | org.ga4gh.starterkit.wes |
| `name` | GA4GH Starter Kit WES Service |
| `description` | An open source, community-driven implementation of the GA4GH Workflow Execution Service (WES) API specification. |
| `contactUrl` | mailto:info@ga4gh.org |
| `documentationUrl` | https://github.com/ga4gh/ga4gh-starter-kit-wes |
| `createdAt` | 2020-01-15T12:00:00Z |
| `updatedAt` | 2020-01-15T12:00:00Z |
| `environment` | test |
| `version` | 0.2.0 |
| `organization.name` | Global Alliance for Genomics and Health |
| `organization.url` | https://ga4gh.org |

### Example Starter Kit WES Config

The following snippet displays an example complete YAML configuration for a deployment of the Starter Kit WES service.

```yaml
wes:
  serverProps:
    scheme: https
    hostname: wes-api-starterkit.example.com
    publicApiPort: 80
    adminApiPort: 7000
  databaseProps:
    url: jdbc:postgresql://starter-kit-demo-db.ga4gh.org:5555/wesdb
    username: ga4gh-admin
    password: makeSureThisIsSecure123
  serviceInfo:
    id: org.ga4gh.wes.public-wes
    name: GA4GH Public Workflow Execution Service
    description: Allows researchers to run workflow analyses according to the WES specification 
```
