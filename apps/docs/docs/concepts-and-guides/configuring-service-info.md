---
sidebar_position: 3
---

# Configuring Service Info

All Starter Kit web services (i.e. [core applications](../starter-kit-apis/overview) based on GA4GH API specficiations) are configurable with a YAML config file. Part of the YAML config file includes a `serviceInfo` object, which modifies the behavior of the attribute values returned in the `/service-info` response.

`Service Info` is the GA4GH standardized method of returning information about running web services adopting GA4GH standards. For more information, see the [Service Info specification](https://github.com/ga4gh-discovery/ga4gh-service-info)

### Configurable `serviceInfo` properties

A full explanation of configurable service info properties is outlined below. The default value of each property depends on the starter kit service type being run (e.g. DRS, WES, etc.)

| Attribute | Description | Data Type |
|-----------|-------------|-----------|
| `id` | Unique identifier for the service. Reverse domain name format recommended. | string |
| `name` | Short, human readable service name | string |
| `description` | Longer, human readable description | string |
| `contactUrl` | URL/email address users should contact with questions or issues about the service | string (URL) |
| `documentationUrl` | URL to where documentation about the service is hosted | string (URL) |
| `createdAt` | ISO 8601 timestamp of when the service was first started | [ISO 8601](https://www.iso.org/iso-8601-date-and-time-format.html) |
| `updatedAt` | ISO 8601 timestamp of when the service was last updated | [ISO 8601](https://www.iso.org/iso-8601-date-and-time-format.html) |
| `environment` | Describes what deployment environment the service is running in (e.g. dev, prod, test | string |
| `version` | The service version | string |
| `organization` | Details about the organization hosting the service | [Organization](#organization-object) object|

#### `Organization` object

Details about the organization hosting a web service. The following properties are supported:

| Attribute | Description | Data Type |
|-----------|-------------|-----------|
| `name` | Name of the organization hosting the service | string |
| `url` | URL to organization homepage | string (URL) |

### Example `serviceInfo`

The following snippet displays an example `serviceInfo` YAML configuration to configure the behavior of a starter kit service. A `drs` service is used in the example, but the `serviceInfo` object is equally applicable to any starter kit service.

```yaml
drs:
  serviceInfo:
    id: org.ga4gh.drs.public-dataset
    name: Genomics Public Dataset DRS Service
    description: Serves genomics public datasets according to DRS specification
    contactUrl: mailto:support@ga4gh.org
    documentationUrl: https://drs-public-dataset-docs.ga4gh.org
    createdAt: 2021-08-20T14:00:00Z
    updatedAt: 2021-08-21T15:30:00Z
    environment: prod
    version: 2.1.0
    organization:
      name: Global Alliance for Genomics and Health
      url: https://ga4gh.org
```
