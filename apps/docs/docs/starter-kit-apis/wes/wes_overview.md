---
sidebar_position: 1
---

# Starter Kit WES Overview

The current Starter Kit WES release **0.2.0** implements WES Specification Release **1.0.1**

[Github](https://github.com/ga4gh/ga4gh-starter-kit-wes), [Docker Hub](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-wes)

The Workflow Execution Service (WES) API provides a generic interface to workflow/batch job submission and monitoring over the web. Clients are able to launch dockerized workflows using a common REST interface regardless of the underlying language(s) different workflows are written in (Common Workflow Language, Workflow Description Language, Nextflow). [View specification](https://github.com/ga4gh/workflow-execution-service-schemas)

Starter Kit WES is a reference server implementation of the WES specification. 

Currently, the following workflow languages are supported:
* Nextflow

Currently, the following workflow engines (i.e. job submission environments) are supported:
* Native (local machine resources)

## Installation and Usage

### Nextflow

Nextflow WES docker images come pre-bundled with both the WES reference server and the Nextflow executable, so that Nextflow-based workflow runs can be run from the container.

To install, first pull the docker image:

```
docker pull ga4gh/ga4gh-starter-kit-wes:${TAG}
```

where `${TAG}` represents the release version of the application. Browse releases/tags of Nextflow-based WES images on [Docker Hub](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-wes/tags)

e.g.

```
docker pull ga4gh/ga4gh-starter-kit-wes:0.2.0-nextflow
```

We strongly recommend running the Starter Kit WES service with a YAML config file to set server properties. In addition, the docker container requires access to the host machine's docker daemon to launch dockerized workflows from Nextflow.

The following command template will start the Starter Kit WES container with custom configuration, and with all necessary file mounts between host machine and docker container:

```
docker run \
    -p ${HOST_PUBLIC_API_PORT}:${CONTAINER_PUBLIC_API_PORT} \
    -p ${HOST_ADMIN_API_PORT}:${CONTAINER_ADMIN_API_PORT} \
    -v ${HOST_DOCKER_SOCKET_FILE}:/var/run/docker.sock \
    -v ${HOST_WORKING_DIR}:${CONTAINER_WORKING_DIR} \
    -v ${HOST_CONFIG_DIR}:/config
    --workdir "${CONTAINER_WORKING_DIR}" \
    ga4gh/ga4gh-starter-kit-wes:0.2.0-nextflow \
    -c /config/${CONFIG_FILE}
```

where:
* `CONTAINER_PUBLIC_API_PORT`: The port within the docker container that the public API endpoints will be served over, must be equal to the `publicApiPort` value in `serverProps`; e.g. `7500`. [More info](../../concepts-and-guides/configuring-webservice-properties)
* `HOST_PUBLIC_API_PORT`: The mapped port on the host machine that will serve the public API endpoints; e.g. `7500`
* `CONTAINER_ADMIN_API_PORT`: The port within the docker container that the admin API endpoints will be served over, must be equal to the `adminApiPort` value in `serverProps`; e.g. `7501`. [More info](../../concepts-and-guides/configuring-webservice-properties)
* `HOST_ADMIN_API_PORT`: The mapped port on the host machine that will serve the admin API endpoints; e.g. `7501`
* `HOST_DOCKER_SOCKET_FILE`: Full path to `docker.sock` file on the host machine. Generally `/var/run/docker.sock`
* `CONTAINER_WORKING_DIR`: Directory within the container where Nextflow jobs are staged and run.
* `HOST_WORKING_DIR`: The mapped directory on the host machine. Ideally the same path as `CONTAINER_WORKING_DIR` 
* `HOST_CONFIG_DIR`: Absolute file path to directory containing YAML config filel e.g. `/home/user/wes/config`
* `CONFIG_FILE`: Name of YAML config file mounted to container; e.g. `config.yml` [More info](./wes_configuration)

The following snippet displays an example `docker run` command:
```
docker run \
  -p 7500:7500 \
  -p 7501:7501 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /tmp/shared/wes:/tmp/shared/wes \
  -v /home/user/wes/config:/config \
  --workdir "/tmp/shared/wes" \
  ga4gh/ga4gh-starter-kit-wes:0.2.0-nextflow \
  -c /config/config.yml
```