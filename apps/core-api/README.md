# GA4GH Reference Cloud: Core API

Core backend API for the GA4GH Reference Cloud.

**For instructions on how to run the full reference cloud, please see the main [README](../../README.md).**

To run the core API by itself, see below.

**Note:** you MUST install all [required prerequisites](../../README.md#getting-started) AND run the [setup script](../../scripts/refcloud-setup.sh) first to create required resources.

## Run

* Run the core API server: `./gradlew bootRun`

## Configuration

See [`src/main/resources/application.yml`](./src/main/resources/application.yml) for a list of configurable properties. Rather than making changes to the YAML file directly, we recommend setting environment variables that will override the defaults in `application.yml`. For example, the value:

```
ga4gh:
  refcloud:
    drs:
      service-info:
        id: org.ga4gh.refcloud.drs.local
```

can be overriden by setting the environment variable:

```
GA4GH_REFCLOUD_DRS_SERVICE_INFO_ID="{new_value}"
```

For more information, see the following article on [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html).