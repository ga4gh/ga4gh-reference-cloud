---
sidebar_position: 1
---

# Starter Kit Data Connect Overview

The Current Starter Kit Data Connect Release **0.1.1** implements Data Connect Specification Release **1.0.0**

[Github](https://github.com/ga4gh/ga4gh-starter-kit-data-connect), [Docker Hub](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-data-connect)

The Data Connect API enables discovery and search of biomedical data. It offers a Table API component for organizing data into "Tables" and describe their data model, leveraging the JSON Schema standard. Additionally, the Search API component provides a way to query "Tables" of data, leveraging the SQL standard. [View specification](https://github.com/ga4gh-discovery/data-connect).

Starter Kit Data Connect is a reference server implementation of the Data Connect specification.

## Installation and Usage

To install, first pull the docker image:
```
docker pull ga4gh/ga4gh-starter-kit-data-connect:${TAG}
```

where `${TAG}` represents the release version of the application. Browse releases/tags for Starter Kit Data Connect images on [Docker Hub](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-data-connect/tags).

e.g.
```
docker pull ga4gh/ga4gh-starter-kit-data-connect:0.1.1
```

To run the container with all default settings, run:
```
docker run -p 4500:4500 -p 4501:4501 ga4gh/ga4gh-starter-kit-data-connect:0.1.1
```

The above command will run the Starter Kit Data Connect service within the docker container. The service will serve data from the preconfigured test database bundled within the container. The default public and admin API ports `4500` and `4501` inside the container will be mapped to the equivalent ports on the host machine.

To run the Starter Kit Data Connect service with a YAML config file overriding default behavior, run:
```
docker run \
    -p ${HOST_PUBLIC_API_PORT}:${CONTAINER_PUBLIC_API_PORT} \
    -p ${HOST_ADMIN_API_PORT}:${CONTAINER_ADMIN_API_PORT} \
    -v ${HOST_CONFIG_DIR}:/config \
    ga4gh/ga4gh-starter-kit-data-connect:0.1.1 \
    java -jar ga4gh-starter-kit-data-connect.jar -c /config/${CONFIG_FILE}
```

where:
* `CONTAINER_PUBLIC_API_PORT`: The port within the docker container that the public API endpoints will be served over, must be equal to the `publicApiPort` value in `serverProps`; e.g. `8000`. [More info](../../concepts-and-guides/configuring-webservice-properties)
* `HOST_PUBLIC_API_PORT`: The mapped port on the host machine that will serve the public API endpoints; e.g. `80`
* `CONTAINER_ADMIN_API_PORT`: The port within the docker container that the admin API endpoints will be served over, must be equal to the `adminApiPort` value in `serverProps`; e.g. `3000`. [More info](../../concepts-and-guides/configuring-webservice-properties)
* `HOST_ADMIN_API_PORT`: The mapped port on the host machine that will serve the admin API endpoints; e.g. `8080`
* `HOST_CONFIG_DIR`: Absolute file path to directory containing YAML config file; e.g. `/home/user/data_connect/config`
* `CONFIG_FILE`: Name of YAML config file mounted to container; e.g. `config.yml` [More info](./data_connect_configuration)

The following snippet displays an example `docker run` command:
```
docker run \
    -p 80:7000 \
    -p 7001:7001 \
    -v /home/usr/data_connect/config:/config \
    ga4gh/ga4gh-starter-kit-data-connect:0.1.1 \
    java -jar ga4gh-starter-kit-data-connect.jar -c /config/config.yml
```