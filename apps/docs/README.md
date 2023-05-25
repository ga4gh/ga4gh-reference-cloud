# GA4GH Starter Kit Docs

This documentation site provides information about the GA4GH Starter Kit. It uses the [Docusaurus](https://docusaurus.io/) framework for static site building.

## Table of Contents

1. [Installation](#installation)
    - [Native Installation](#native-installation)
    - [Docker Installation](#docker-installation)
2. [Build](#build)
    - [Native Build](#native-build)
    - [Docker Build](#docker-build)
3. [Deployment](#deployment)
    - [Native Deployment](#native-deployment-used-for-local-development)
    - [Docker Deployment](#deployment-using-docker)
    - [Deploy to GitHub Pages](#deploy-to-github-pages)

## Installation

### Native Installation

To install the GA4GH Starter Kit and its dependencies natively, make sure you have the following prerequisites:

- Node.js version >= 16.20.0
- Yarn (tested with version 1.22.19)

Run the following command to install all the dependencies:

```
yarn install
```

### Docker Installation

To install the GA4GH Starter Kit using Docker, make sure you have [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed on your machine.

## Build

The build process generates static content into the `build` directory, which can be hosted using any static content hosting service.

### Native Build

To build the project natively, run the following command:

```
yarn build
```

### Docker Build

To build the project using Docker, execute the following command:

```
make run-docker-static-build
```

## Deployment

This section describes how to deploy the GA4GH Starter Kit Docs.

### Native Deployment (used for Local Development)

The following command starts a local development server and opens up a browser window.
```
yarn start
```
This local server enables live reloading, allowing most changes to be reflected without having to restart the server.

### Deployment Using Docker

To deploy the static resources from the Docker image using Nginx, run the following command:

```
make run-docker-nginx-deploy
```

### Deploy to Github Pages


```
GIT_USER=<Your GitHub username> USE_SSH=true yarn deploy
```

If you are using GitHub pages for hosting, this command is a convenient way to build the website and push to the `gh-pages` branch.