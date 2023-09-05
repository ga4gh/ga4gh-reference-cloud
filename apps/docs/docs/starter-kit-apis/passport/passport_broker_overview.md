---
sidebar_position: 3
---

# Starter Kit Passport Broker Overview

The Current Starter Kit Passport Release **0.0.2** implements GA4GH Passport Specification Release **1.2.0**

[Github](https://github.com/ga4gh/ga4gh-starter-kit-passport-broker), [Docker Hub](https://hub.docker.com/r/ga4gh/ga4gh-starter-kit-passport-broker)

Basic instructions for running Starter Kit Passport Broker in a dev environment are included here.

## Installation and Usage

To install, first pull the docker image:
```
docker pull ga4gh/ga4gh-starter-kit-passport-broker:${TAG}
```

where `${TAG}` represents the release version of the application. Browse releases/tags on [Docker Hub](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-passport-broker/tags).

e.g.
```
docker pull ga4gh/ga4gh-starter-kit-passport-broker:0.0.2
```

To run the container with all default settings, run:
```
docker run -p 4500:4500 -p 4501:4501 ga4gh/ga4gh-starter-kit-passport-broker:0.0.2
```

The above command will run the Starter Kit Passport Broker service within the docker container. The service will serve data from the preconfigured test database bundled within the container. The default public and admin API ports `4500` and `4501` inside the container will be mapped to the equivalent ports on the host machine.

To run the Starter Kit Passport Broker service with a YAML config file overriding default behavior, run:
```
docker run \
    -p ${HOST_PUBLIC_API_PORT}:${CONTAINER_PUBLIC_API_PORT} \
    -p ${HOST_ADMIN_API_PORT}:${CONTAINER_ADMIN_API_PORT} \
    -v ${HOST_CONFIG_DIR}:/config \
    ga4gh/ga4gh-starter-kit-passport-broker:0.0.2 \
    java -jar ga4gh-starter-kit-passport-broker.jar -c /config/${CONFIG_FILE}
```

where:
* `CONTAINER_PUBLIC_API_PORT`: The port within the docker container that the public API endpoints will be served over, must be equal to the `publicApiPort` value in `serverProps`; e.g. `8000`. [More info](../../concepts-and-guides/configuring-webservice-properties)
* `HOST_PUBLIC_API_PORT`: The mapped port on the host machine that will serve the public API endpoints; e.g. `80`
* `CONTAINER_ADMIN_API_PORT`: The port within the docker container that the admin API endpoints will be served over, must be equal to the `adminApiPort` value in `serverProps`; e.g. `3000`. [More info](../../concepts-and-guides/configuring-webservice-properties)
* `HOST_ADMIN_API_PORT`: The mapped port on the host machine that will serve the admin API endpoints; e.g. `8080`
* `HOST_CONFIG_DIR`: Absolute file path to directory containing YAML config file; e.g. `/home/user/passport/config`
* `CONFIG_FILE`: Name of YAML config file mounted to container; e.g. `config.yml` [More info](./passport_configuration)

The following snippet displays an example `docker run` command:
```
docker run \
    -p 80:7000 \
    -p 7001:7001 \
    -v /home/usr/passport/config:/config \
    ga4gh/ga4gh-starter-kit-passport-broker:0.0.2 \
    java -jar ga4gh-starter-kit-passport-broker.jar -c /config/config.yml
```