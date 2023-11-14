---
sidebar_position: 2
---

# Starter Kit Passport UI Overview

The Current Starter Kit Passport UI Release **0.0.2** implements GA4GH Passport Specification Release **1.2.0**

[Github](https://github.com/ga4gh/ga4gh-starter-kit-passport-ui), [Docker Hub](https://hub.docker.com/repository/docker/ga4gh/ga4gh-starter-kit-passport-ui-node)

The GA4GH Passport UI is the central UI server that connects the other Passport related microservice (ory hydra, ory kratos). The GA4GH Starterkit Passport implementation is made up of multiple services, this UI-node being one of them. To make API requests, the Passport Broker Service will need to be running. [View specification](https://github.com/ga4gh-duri/ga4gh-duri.github.io/blob/master/researcher_ids/ga4gh_passport_v1.md).


## Installation and Usage

To run the Passport UI, first clone the Passport UI repo
```
git clone https://github.com/ga4gh/ga4gh-starter-kit-passport-ui
```

This service is a docker container, having [Docker Desktop](https://docs.docker.com/desktop/) installed beforehand will be useful. 

After cloning the repository, navigate to the cloned repo and use the command below to run the different services in the passport-network docker-compose file.
```
docker-compose -f passport-network.yml up --build --force-recreate -d
```

The following services should be displayed when the command above is run
![Passport Services](/img/passport-services.png)

Ory hydra is a OAuth 2.0 and Open ID Connect certified server. It allows you to interface with the passport application and login systems.

Ory kratos is the identity management system for the server for the different users. This service supports user creation and management.

MailSlurper is a SMTP mail server that is used to test email functionality. It is used in the developer environment of the starterkit as part of the kratos user management system. 

To stop and remove all the deployed Docker containers, networks, and volumes created using the `passport-network.yml` docker-compose file, execute the following command.
```
docker-compose -f passport-network.yml down -v
```
