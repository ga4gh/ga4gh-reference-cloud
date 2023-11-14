---
sidebar_position: 1
---

# Configuring Webservice Properties

All Starter Kit web services (i.e. [core applications](../starter-kit-apis/overview) based on GA4GH API specifications) are configurable with a YAML config file. Part of the YAML config file includes a `serverProps` object, which modifies the behavior of high-level server application properties.

### Configurable `serverProps` properties

A full explanation of configurable server properties is outlined below.

| Attribute | Description | Data Type | Default |
|-----------|-------------|-----------|---------|
| `scheme` | The URL scheme/protocol by which the service can be accessed. (`http` or `https`). Allows `http` in dev/local deployments, but real-world deployments should use `https`. Used for constructing URLs that self-reference the running service. | enum [`http`, `https`] | http |
| `hostname` | The URL domain name (including subdomain) that this service is running at. Used for constructing URLs that self-reference the running service. | string | localhost |
| `publicApiPort` | The networking port that will be open to requests to the **public** API, that is, all API endpoints outlined in GA4GH specifications. | integer | 4500 |
| `adminApiPort` | The networking port that will be open to requests to the **admin** API, that is, API endpoints that are not outlined in GA4GH specs, but are required to create and edit data models. Traffic to the admin port should have stricter firewall rules compared to the public API port. | integer | 4501 |
| `logLevel` | The log severity level. The program will output log messages encountered at the specified log level or higher. | enum [`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`]| `DEBUG` |
| `logFile` | Path to output file storing log output. If null, log output will be printed to console. | string (file path) | `null` |
| `publicApiCorsAllowedOrigins` | Customizes the Cross Origin Resource Sharing (CORS) header `Access-Control-Allow-Origin` for requests made to any public API endpoint. Allows content returned from the API to be rendered only on trusted websites. | array [URL] | [`"http://localhost"`] |
| `publicApiCorsAllowedMethods` | Customizes the CORS header `Access-Control-Allow-Methods` for requests made to any public API endpoint. Allows content returned from the API to be rendered only when a trusted HTTP method was used to request data. | array [enum [`GET`, `POST`, `PUT`, `DELETE`]]| [`GET`, `POST`, `PUT`, `DELETE`] |
| `publicApiCorsAllowedHeaders` | Customizes the CORS header `Access-Control-Allow-Headers` for requests made to any public API endpoint. Allows API content to be rendered on a website only when trusted HTTP headers are sent to the server. | array [string]| [] |
| `adminApiCorsAllowedOrigins` | Customizes the Cross Origin Resource Sharing (CORS) header `Access-Control-Allow-Origin` for requests made to any admin API endpoint. Allows content returned from the API to be rendered only on trusted websites. | array [URL] | [`"http://localhost"`]|
| `adminApiCorsAllowedMethods` | Customizes the CORS header `Access-Control-Allow-Methods` for requests made to any admin API endpoint. Allows content returned from the API to be rendered only when a trusted HTTP method was used to request data. | array [enum [`GET`, `POST`, `PUT`, `DELETE`]]| [`GET`, `POST`, `PUT`, `DELETE`] |
| `adminApiCorsAllowedHeaders` | Customizes the CORS header `Access-Control-Allow-Headers` for requests made to any admin API endpoint. Allows API content to be rendered on a website only when trusted HTTP headers are sent to the server. | array [string]| [] |
| `disableSpringLogging` | If true, does not output the default startup messages for Spring Boot web apps to console (i.e. Spring logo) | boolean | `false` |

### Example `serverProps`

The following snippet displays an example `serverProps` YAML configuration to configure the behavior of a starter kit service. A `drs` service is used in the example, but the `serverProps` object is equally applicable to any starter kit service.

```yaml
drs:
  serverProps:
    scheme: https
    hostname: drs-api-starterkit.example.com
    publicApiPort: 80
    adminApiPort: 7000
    logLevel: INFO
    logFile: apilog.txt
    publicApiCorsAllowedOrigins:
      - https://some-trusted-public-site.com
      - https://another-trusted-public-site.com
    publicApiCorsAllowedMethods:
      - GET
      - POST
      - PUT
      - DELETE
    publicApiCorsAllowedHeaders:
      - *
    adminApiCorsAllowedOrigins:
      - https://private-site-for-admins.com
    adminApiCorsAllowedMethods:
      - GET
      - POST
      - PUT
      - DELETE
    adminApiCorsAllowedHeaders:
      - *
    disableSpringLogging: true
```