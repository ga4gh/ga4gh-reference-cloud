---
sidebar_position: 1
---

# Overview

This section provides comprehensive documentation on how to setup, configure, and run each of the Starter Kit applications.

## Core Applications

The Starter Kit core applications are open-source reference implementations of approved GA4GH API specifications. The following table lists each available app and where they can be accessed. 

| Name | Description | Docs | Github | Docker Hub |
|------|-------------|------|--------|------------|
| GA4GH Starter Kit DRS | Web service providing indirection layer to datasets stored in the cloud. Serves data according to the [Data Repository Service (DRS) specification](https://github.com/ga4gh/data-repository-service-schemas) | [DRS](./drs/drs_overview) | [![Starter Kit DRS Github Repo](/img/GitHub-Mark-32px.png)](https://github.com/ga4gh/ga4gh-starter-kit-drs) | [![Starter Kit DRS Docker Hub Repo](/img/Moby-logo.png)](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-drs) |
| GA4GH Starter Kit WES | Web service enabling the remote submission, monitoring, and canceling of computational workflows. Based on the [Workflow Execution Service (WES) specification](https://github.com/ga4gh/workflow-execution-service-schemas) | [WES](./wes/wes_overview) | [![Starter Kit WES Github Repo](/img/GitHub-Mark-32px.png)](https://github.com/ga4gh/ga4gh-starter-kit-wes) | [![Starter Kit WES Docker Hub Repo](/img/Moby-logo.png)](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-wes) |

## Support Applications

The Starter Kit support applications provide additional utility to the core apps in terms of interactivity and maintainability. The following table lists each available app/codebase and where they can be accessed.

| Name | Description | Docs | Github | Docker Hub | Other Links |
|------|-------------|------|--------|------------|---------------|
| GA4GH Starter Kit UI | View, create, edit, delete GA4GH models served by starter kit web services via user interface |  | [![Starter Kit UI Github Repo](/img/GitHub-Mark-32px.png)](https://github.com/ga4gh/ga4gh-starter-kit-ui) | [![Starter Kit UI Docker Hub Repo](/img/Moby-logo.png)](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-ui) |
| GA4GH Starter Kit Utils | Commandline tool for performing setup and maintenance operations on starter kit services and databases || [![Starter Kit Utils Github Repo](/img/GitHub-Mark-32px.png)](https://github.com/ga4gh/ga4gh-starter-kit-utils) | [![Starter Kit Utils Docker Hub Repo](/img/Moby-logo.png)](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-utils) |
| GA4GH Starter Kit Common | Common utils library imported by all starter kit core apps || [![Starter Kit Common Github Repo](/img/GitHub-Mark-32px.png)](https://github.com/ga4gh/ga4gh-starter-kit-common) | [![Starter Kit Common Docker Hub Repo](/img/Moby-logo.png)](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-common) | [Maven Central](https://search.maven.org/artifact/org.ga4gh/ga4gh-starter-kit-common/0.5.3/jar)
| GA4GH Starter Kit Docs | Static documentation site (i.e. this website) || [![Starter Kit Docs Github Repo](/img/GitHub-Mark-32px.png)](https://github.com/ga4gh/ga4gh-starter-kit-docs) | N/A |