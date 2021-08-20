---
sidebar_position: 2
---

# Starter Kit DRS Configuration

The Starter Kit DRS service can be configured with modifiable properties. All property configuration is done via a YAML config file that is supplied to the program at runtime via a `-c ${CONFIG_FILE}` option. This section outlines the structure of the YAML config.

The config file MUST start with a single root object named `drs`. All sub-properties are nested under `drs`.

### Configurable `drs` properties

| Attribute | Description | Links |
|-----------|-------------|------|
| `serverProps` | Modifies the behavior of high-level server application properties, such as listening port(s), logging, and CORS headers. | See [`serverProps`](../../concepts-and-guides/configuring-webservice-properties) for more info.
| `databaseProps` | Connect to a target database that has been set up with the prerequisite table schema for Starter Kit DRS | See [`databaseProps`](../../concepts-and-guides/configuring-database-connection) for more info. |
| `serviceInfo` | Display custom information about the deployed web service via the `/service-info` endpoint | See [`serviceInfo`](../../concepts-and-guides/configuring-service-info) for more info on configuring a custom Service Info response. See [`DRS Service Info Defaults`](#drs-service-info-defaults) for the default service info values for Starter Kit DRS deployments. |
| `drsServiceProps` | Server properties unique only to the DRS API (and not applicable to other Starter Kit services) | See [`drsServiceProps`](#drs-service-props) for more info |

### DRS Service Info Defaults

The following table lists the default attribute values returned over `/service-info` for Starter Kit DRS services.

| Attribute | Default Value |
|-----------|---------------|
| `id` | org.ga4gh.starterkit.drs |
| `name` | GA4GH Starter Kit DRS Service |
| `description` | An open source, community-driven implementation of the GA4GH Data Repository Service (DRS) API specification. |
| `contactUrl` | mailto:info@ga4gh.org |
| `documentationUrl` | https://github.com/ga4gh/ga4gh-starter-kit-drs |
| `createdAt` | 2020-01-15T12:00:00Z |
| `updatedAt` | 2020-01-15T12:00:00Z |
| `environment` | test |
| `version` | 0.1.0 |
| `organization.name` | Global Alliance for Genomics and Health|
| `organization.url` | https://ga4gh.org |

### DRS Service Props

The `drsServiceProps` object modifies unique properties to the DRS API. A full explanation of configurable `drsServiceProps` properties is outlined below

| Attribute | Description | Data Type | Default |
|-----------|-------------|-----------|---------|
| `serveFileURLForFileObjects` | Indicates whether the DRS service should return `file://` URLs of DRSObject file location to the client. If true, assumes that the client has access to the same filesystem as the service (e.g. HPC environment, or local machine). Applies only to 'file' based objects (ie not https, s3, etc.)| boolean | `false` |
| `serveStreamURLForFileObjects` | Indicates whether the DRS service should provide the streaming endpoint URL to the client, for files that the server can access but the client cannot. If true, assumes that client cannot access the file by its path. Applies only to 'file' based objects (ie not https, s3, etc.) | boolean | `true` |

### Example Starter Kit DRS Config

The following snippet displays an example complete YAML configuration for a deployment of the Starter Kit DRS service.

```yaml
drs:
  serverProps:
    scheme: https
    hostname: drs-api-starterkit.example.com
    publicApiPort: 80
    adminApiPort: 7000
  databaseProps:
    url: jdbc:postgresql://starter-kit-demo-db.ga4gh.org:5555/drsdb
    username: ga4gh-admin
    password: makeSureThisIsSecure123
  serviceInfo:
    id: org.ga4gh.drs.public-dataset
    name: Genomics Public Dataset DRS Service
    description: Serves genomics public datasets according to DRS specification
  drsServiceProps:
    serveFileURLForFileObjects: true
    serveStreamURLForFileObjects: false
```