---
sidebar_position: 1
---

# Starter Kit Passport Overview

The Current Starter Kit Passport Release **0.0.2** implements GA4GH Passport Specification Release **1.2.0**

The GA4GH Starter Kit Passport implementation is broken down into two starter kits; The Starter Kit Passport UI and the Starter Kit Passport Broker.

The [Passport UI](./passport_ui_overview) creates the front end interface that makes it easier to use the passport implementation. This UI connects two other Passport-related microservices, which are ory Hydra and ory Kratos. Using the UI, you are able to view visas, manage users, and get passport tokens all from the web page.

The [Passport Broker](./passport_broker_overview) is the passport server that can respond to http requests with the data. This implementation works as the backend to the UI starter kit and can be run by itself for users who want to bypass the UI and make requests directly using http. The Passport Broker provides the endpoints in the [Passport specification](https://github.com/ga4gh-duri/ga4gh-duri.github.io/blob/master/researcher_ids/ga4gh_passport_v1.md).

## Installation and Usage

For installation instructions and how to run the Starter Kit Passport UI, go to the [Passport UI Overview](./passport_ui_overview) page or to the [Github repo](https://github.com/ga4gh/ga4gh-starter-kit-passport-ui).

For installation instructions and how to run the Starter Kit Passport Broker, go to the [Passport Broker Overview](./passport_broker_overview) page or to the [Github repo](https://github.com/ga4gh/ga4gh-starter-kit-passport-broker).