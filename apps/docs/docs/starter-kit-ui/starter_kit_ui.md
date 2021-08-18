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
docker run --rm -d --name ga4gh-ui-test -p 8989:8989 -v ga4gh/ga4gh-starter-kit-ui:0.2.1
```

Alternatively, to use a custom YAML configuration file outlining the port for the Starter Kit UI and the GA4GH Starter Kit services to be run, use the command line argument `-c <config>` or `--config <config>` to provide the path to the configuration file. These instructions provide URLs assuming that the default ports are being used, however, if your configuration uses different ports, some of the URLs througout the Starter Kit UI documentation will differ.

### Navigation

Launch the app by going to the url: `http://localhost:8989`. Select Continue to navigate to the GA4GH Starter Kit homepage (`http://localhost:8989/home`), then select Get Started to view the available GA4GH Starter Kit services (`http://localhost:8989/services`).

Choose a valid service to view more information about the service and the data models that can be updated with the GA4GH Starter Kit. Select the model of interest to get started.

See the links below for more information about each of the GA4GH Starter Kits:
- [Data Repository Service (DRS) Starter Kit](../starter-kit-apis/drs/drs_starter_kit_ui.md)