---
sidebar_position: 1
---

# Starter Kit DRS Overview

The Current Starter Kit DRS Release **0.2.0** implements DRS Specification Release **1.1.0**

[Github](https://github.com/ga4gh/ga4gh-starter-kit-drs), [Docker Hub](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-drs)

The Data Repository Service (DRS) API provides a generic interface to data repositories so data consumers, including workflow systems, can access data in a single, standardized way regardless of where it’s stored or how it’s managed. The primary functionality of DRS is to map a logical ID to a means for physically retrieving the data represented by the ID. [View specification](https://github.com/ga4gh/data-repository-service-schemas).

Starter Kit DRS is a reference server implementation of the DRS specification.

## Installation and Usage

To install, first pull the docker image:
```
docker pull ga4gh/ga4gh-starter-kit-drs:${TAG}
```

where `${TAG}` represents the release version of the application. See the "Latest Starter Kit DRS Release" above for the most current release, or browse releases/tags on [Docker Hub](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-drs/tags).

e.g.
```
docker pull ga4gh/ga4gh-starter-kit:0.2.0
```

To run the container with all default settings, run:
```
docker run -p 4500:4500 -p 4501:4501 ga4gh/ga4gh-starter-kit-drs:0.2.0
```

The above command will run the Starter Kit DRS service within the docker container. The service will serve data from the preconfigured test database bundled within the container. The default public and admin API ports `4500` and `4501` inside the container will be mapped to the equivalent ports on the host machine.

To run the Starter Kit DRS service with a YAML config file overriding default behavior, run:
```
docker run \
    -p ${HOST_PUBLIC_API_PORT}:${CONTAINER_PUBLIC_API_PORT} \
    -p ${HOST_ADMIN_API_PORT}:${CONTAINER_ADMIN_API_PORT} \
    -v ${HOST_CONFIG_DIR}:/config \
    ga4gh/ga4gh-starter-kit-drs:0.2.0 \
    java -jar ga4gh-starter-kit-drs.jar -c /config/${CONFIG_FILE}
```

where:
* `CONTAINER_PUBLIC_API_PORT`: The port within the docker container that the public API endpoints will be served over, must be equal to the `publicApiPort` value in `serverProps`; e.g. `8000`. [More info](../../concepts-and-guides/configuring-webservice-properties)
* `HOST_PUBLIC_API_PORT`: The mapped port on the host machine that will serve the public API endpoints; e.g. `80`
* `CONTAINER_ADMIN_API_PORT`: The port within the docker container the that the admin API endpoints will be server over, must be equal to the `adminApiPort` value in `serverProps`; e.g. `3000`. [More info](../../concepts-and-guides/configuring-webservice-properties)
* `HOST_ADMIN_API_PORT`: The mapped port on the host machine that will serve the admin API endpoints; e.g. `8080`
* `HOST_CONFIG_DIR`: Absolute file path to directory containing YAML config file; e.g. `/home/user/drs/config`
* `CONFIG_FILE`: Name of YAML config file mounted to container; e.g. `config.yml` [More info](./drs_configuration)

The following snippet displays an example `docker run` command:
```
docker run \
    -p 80:7000 \
    -p 7001:7001 \
    -v /home/usr/drs/config:/config \
    ga4gh/ga4gh-starter-kit-drs:0.2.0 \
    java -jar ga4gh-starter-kit-drs.jar -c /config/config.yml
```