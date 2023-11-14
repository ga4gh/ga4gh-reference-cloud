---
sidebar_position: 4
---

# Starter Kit Passport Broker Configuration

The Starter Kit Passport service can be configured with modifiable properties. All property configuration is done via a YAML config file that is supplied to the program at runtime via a `-c ${CONFIG_FILE}` option. This section outlines the structure of the YAML config.

The config file MUST start with a single root object named `passport`. All sub-properties are nested under `passport`.

### Configurable `passport` properties

| Attribute | Description | Links |
|-----------|-------------|------|
| `serverProps` | Modifies the behavior of high-level server application properties, such as listening port(s), logging, and CORS headers. | See [`serverProps`](../../concepts-and-guides/configuring-webservice-properties) for more info.
| `serviceInfo` | Display custom information about the deployed web service via the `/service-info` endpoint | See [`serviceInfo`](../../concepts-and-guides/configuring-service-info) for more info on configuring a custom Service Info response. See [`Passport Service Info Defaults`](#passport-service-info-defaults) for the default service info values for Starter Kit Passport deployments. |


### Passport Service Info Defaults

The following table lists the default attribute values returned over `/service-info` for Starter Kit Passport services.

| Attribute | Default Value |
|-----------|---------------|
| `id` | org.ga4gh.starterkit.passport.broker |
| `name` | GA4GH Starter Kit Passport Broker Service |
| `description` | Starter Kit implementation of a Passport Broker service, outlined in the GA4GH Passport specification. Manages researcher permissions to data and compute, and enables this information to be minted as JWTs and passed to downstream clearinghouses. |
| `contactUrl` | mailto:info@ga4gh.org |
| `documentationUrl` | https://github.com/ga4gh/ga4gh-starter-kit-passport-broker |
| `createdAt` | 2022-04-28T09:00:00Z |
| `updatedAt` | 2022-04-28T09:00:00Z |
| `environment` | test |
| `version` | 0.0.2 |
| `type.group`| org.ga4gh |
| `type.artifact`| passport-broker |
| `type.version`| 1.0.0 |
| `organization.name` | Global Alliance for Genomics and Health|
| `organization.url` | https://ga4gh.org |


### Example Starter Kit Passport Config

The following snippet displays an example complete YAML configuration for a deployment of the Starter Kit Passport service.

```yaml
passportBrokerConfig:
  serverProps:
    scheme: http
    publicApiPort: 7000
    adminApiPort: 7001
  serviceInfo:
    id: org.ga4gh.starterkit.passport.broker.custom.test
    name: Passport Broker Custom Config Test Deployment
    description: Passport Broker test deployment using custom configurations
    contactUrl: mailto:test@ga4gh.org
    organization:
      name: GA4GH
      url: https://ga4gh.org
```