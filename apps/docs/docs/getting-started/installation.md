---
sidebar_position: 3
---

# Installation

The GA4GH Starter Kit is not a single, monolithic application, rather it is a suite of tools, with each component in the toolset acting as a server application that fulfills a GA4GH API specification. Users can deploy only the required services for the standardized use case or data sharing scenario at hand.

This article provides a generic overview of how to install apps in the Starter Kit suite. For more detailed instructions on how to install and run a specific API, see the [table below](#starter-kit-suite-docs).

## Install via Docker

We recommend using Starter Kit Docker images for all deployment contexts where Docker is allowed. Docker ensures that any software dependencies are pre-bundled within the container, eliminating the need to manually install and configure dependencies on the host machine.

We'll use `ga4gh-starter-kit-drs` as an example, which implements the Data Repository Service (DRS) specification. To run the `ga4gh-starter-kit-drs` application, we first pull the docker image:
```
docker pull ga4gh/ga4gh-starter-kit-drs:0.2.0
```

Next, we can simply run the application with all default settings by running:
```
docker run ga4gh/ga4gh-starter-kit-drs:0.2.0
```

That's it! A configuration file can be provided to the server application at runtime, modifying the server's behavior (such as the database it connects to). But that's too much to discuss here. See the [table below](#starter-kit-suite-docs) for more information on how to configure the specific Starter Kit APIs.

Feel free to check out our space on [Docker Hub](https://hub.docker.com/orgs/ga4gh/repositories) for the full range of available Starter Kit Docker images (all Docker repos have the prefix `ga4gh-starter-kit` in their name).

## Install from source

If a docker-based setup is not possible for the deployment context, we also provide releases on Github. Each release contains the Java code that can build the application from source.

Again, let's use `ga4gh-starter-kit-drs` as an example. If we look at the [releases](https://github.com/ga4gh/ga4gh-starter-kit-drs/releases) page, we see a list of artifacts containing the source code. If we wish to build the `0.1.9` release of `ga4gh-starter-kit-drs`, we can download the artifact, and then run the following commands to build the Java archive (JAR):

```
tar -zxvf ga4gh-starter-kit-drs-0.1.9.tar.gz
cd ga4gh-starter-kit-drs-0.1.9
./gradlew bootJar
```

We can then run the JAR by using:

```
java -jar build/libs/ga4gh-starter-kit-drs-0.1.9.jar
```

**Note**: Starter Kit Apps are currently tested on Java 12.

## Starter Kit Suite Docs

The following table links out to the full installation and configuration instructions for each of the Starter Kit microservice applications:

| Name | Documentation |
|------|---------------|
| GA4GH Starter Kit DRS | [Documentation](../starter-kit-apis/drs/drs_overview) |
| GA4GH Starter Kit WES | [Documentation](../starter-kit-apis/wes/wes_overview) |
| GA4GH Starter Kit Data Connect | [Documentation](../starter-kit-apis/data_connect/data_connect_overview) |