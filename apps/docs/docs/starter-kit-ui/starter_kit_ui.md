---
sidebar_label: Overview
title: Starter Kit UI Overview
id: overview
sidebar_position: 1
---

## Starter Kit UI: Getting Started

### Configuration

Run the Starter Kit UI with Docker using the default service configuration by running the following command: 
```jsx 
docker run --rm -d --name ga4gh-ui -p 8989:8989 ga4gh/ga4gh-starter-kit-ui:0.2.1
```

Alternatively, to use a custom YAML configuration file outlining the port for the Starter Kit UI and the GA4GH Starter Kit services to be run, use the command line argument `-c <config>` or `--config <config>` to provide the path to the configuration file. These instructions provide URLs assuming that the default ports are being used, however, if your configuration uses different ports, some of the URLs througout the Starter Kit UI documentation will differ.

The code below shows an example YAML configuration file that could be provided for the Starter Kit UI to use two DRS Starter Kit services.
Note that if the services defined in the config are not running, Starter Kit UI will identify them as invalid services. Refer to the API documentation for the service you are trying to use to ensure that it is running properly.
```YAML
starterKitUI:
  port: 8989
  services:
    - serviceType: 'drs'
      publicURL: 'http://localhost:4500'
      adminURL: 'http://localhost:8080'
    - serviceType: 'drs'
      publicURL: 'http://localhost:7000'
      adminURL: 'http://localhost:7500'
```

### Navigation

Launch the app by going to the url: `http://localhost:8989`. Select Continue to navigate to the GA4GH Starter Kit homepage, then select Get Started to view the available GA4GH Starter Kit services, as shown below.

![Services](./assets/ServicesImage.png)
_Figure 1: An example of the services page which displays three valid DRS services and two invalid DRS services. Click the View button to get started with one of the valid DRS services._

Choose a valid service to view more information about the service and the data models that can be updated using the GA4GH Starter Kit. Select one of the models to get started.

See the links below for more information about each of the GA4GH Starter Kits:
- [Data Repository Service (DRS) Starter Kit](../starter-kit-apis/drs/drs_starter_kit_ui.md)