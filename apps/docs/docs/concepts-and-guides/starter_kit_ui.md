---
title: Configuring the UI
sidebar_position: 3
---

## Install
Use [DockerHub](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-ui) to pull the latest Starter Kit UI Docker image: 
```
docker image pull ga4gh/ga4gh-starter-kit-ui:${tag}
```

## Usage
### Default Configuration 
Run with default Starter Kit UI service configuration properties, using Docker Image with tag `0.2.1`: 
``` 
docker run -p 8989:8989 ga4gh/ga4gh-starter-kit-ui:0.2.1
```
Note: `8989` is the UI server's default port
### Custom Configuration
Run with custom Starter Kit UI configuration: 
```
docker run -p ${HOST_PORT}:${CONTAINER_PORT} -v ${HOST_CONFIG_DIR}:/config ga4gh/ga4gh-starter-kit-ui:0.2.1 -c /config/${CONFIG_FILE}
```
`HOST_PORT`: The mapped port on the host machine<br/>
`CONTAINER_PORT`: The port within the Docker container that the app will run on, which should correspond to the `port` property defined in the YAML config file<br/>
`HOST_CONFIG_DIR`: The absolute file path to the directory containing the YAML configuration file<br/>
`CONFIG_FILE`: The name of the YAML configuration file mounted to the container

## Configuration
The Starter Kit UI app can be configured to load, render and manipulate data models from GA4GH Starter Kit Web Services that are running within a local network or over the web. All property configuration is done via a YAML config file that is supplied to the program at runtime via a `-c ${CONFIG_FILE}` command line option.

The config file MUST start with a single root object named `starterKitUI`. All sub-properties are nested under `starterKitUI`.

### `starterKitUI` Properties: 
|Name|Description|Data Type|Example|
|----|-----------|---------|-------|
|port|The port that the app will run on.|`integer`|`4000`|
|services|A list of running GA4GH Starter Kit services that the UI will reference and load data from.|[`serviceConfig`] array|||

### `serviceConfig` Properties:
|Name|Description|Data Type|Example|
|----|-----------|---------|-------|
|serviceType|The canonical GA4GH API specification/type of the GA4GH Starter Kit service.|enum [`drs`]|`drs`|
|publicURL|The URL and port serving the service's public, read-only API.|URL|`https://drs1.example.com`|
|adminURL|The URL and port serving the service's private, administrative API.|URL|`https://drs1.example.com:6000`|

### Configuration Example
The code below shows an example YAML configuration file that could be provided for the Starter Kit UI to load three DRS Starter Kit services.
```YAML title='config.yml'
starterKitUI:
  port: 4000
  services:
    - serviceType: 'drs'
      publicURL: 'https://drs1.example.com'
      adminURL: 'https://drs1.example.com:6000'
    - serviceType: 'drs'
      publicURL: 'https://drs2.example.com'
      adminURL: 'https://drs2.example.com:7000'
    - serviceType: 'drs'
      publicURL: 'https://drs3.example.com'
      adminURL: 'https://drs3.example.com:8001'
```
### Default Configuration
If a configuration file is not provided, the default configuration is used instead. In this case, the Starter Kit UI runs on port `8989` and a single DRS service is loaded using publicURL `http://localhost:4500` and adminURL `http://localhost:8080`.


### Valid and Invalid Services
The GA4GH Starter Kit UI attempts to load each of the services defined in the configuration file. To determine if a service is valid, a GET request is made to the service-info endpoint corresponding to the specified service type. If the GET request is successful, the service is determined to be valid; however, if the request is unsuccessful and the service cannot be reached, it is identified as invalid, meaning that it cannot be used in the UI. Ensure that the Starter Kit UI configuration file is correct and refer to the [API documentation](../starter-kit-apis/overview.md) to ensure that the service is set up and running correctly.

## Starter Kit Services

Launch the app by visiting the URL where the Starter Kit UI is deployed in a web browser using the port that the UI service is running on. If the UI service is deployed on your local machine and uses the default port, the URL will be: `http://localhost:8989`. 

Upon navigating to the Starter Kit UI in a web browser, the GA4GH Starter Kit landing page is displayed. Select Enter to navigate to the GA4GH Starter Kit homepage, then select Get Started to view the available GA4GH Starter Kit services, as shown below. Choose a valid service to view more information about the service and the data models that can be updated using the GA4GH Starter Kit.


![Services](./assets/ServicesImage.png)
_Figure 1: An example of the Services page which currently displays three valid DRS services and two invalid DRS services. For each valid service, the "View" button navigates to that service's `service-info`, as well as forms for creating and modifying the GA4GH data models the service is responsible for._


See the links below for more information about the UI modules for the GA4GH Starter Kit APIs:
- [Data Repository Service (DRS) UI](../starter-kit-apis/drs/drs_starter_kit_ui.md)