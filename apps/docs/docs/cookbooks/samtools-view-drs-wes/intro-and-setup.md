---
sidebar_position: 1
---

# Intro and Setup

In this cookbook, we will be running a very simple `samtools view` workflow on a small test dataset using two GA4GH standards: Worfklow Execution Service (WES) and Data Repository Service (DRS). [Samtools](http://www.htslib.org/) is a command line application that is ubiquitous in bioinformatics and genomics. It is generally used to summarize, filter, and manipulate alignment data (in SAM, BAM, or CRAM format). Here, Samtools will simply be used to count the number of reads in some test BAM files.

As mentioned above, we will set up two 2 starter kit services based on GA4GH API specifications:
1. Workflow Execution Service (WES) - we will trigger `samtools view` workflow runs by submitting API requests to WES
2. Data Repostory Service (DRS) - in the last part of this exercise, we will use DRS IDs and DRS URLs to provide a layer of indirection to the BAM file inputs of the workflow, allowing us to run the same workflow regardless of what cloud or local resources the input files are stored on.

We will also use the Starter Kit UI to make manual changes to our DRS database using a web browser.

This cookbook assumes that:
* docker and docker-compose are installed and updated on your local machine
* You have access to your local machine's command line terminal
* You have an API testing tool installed on your local machine (such as Postman).

## Starter Kit Service setup

Let's begin by configuring and running our two starter kit services: WES and DRS, as well as the UI service.

### Project directory

Let's setup the project directory space. Using the terminal, create and navigate to an empty directory in a preferred workspace. For example, the project directory used for the sample commands in the docs is: `/Users/jadams/cookbooks/samtools_wes_drs`.

Create and navigate to project directory:
```
mkdir -p /Users/jadams/cookbooks/samtools_wes_drs
cd /Users/jadams/cookbooks/samtools_wes_drs
```

### Database setup

We will use simple SQLite databases as the database backend driving WES and DRS starter kit apps. The `ga4gh-starter-kit-utils` app will allow us to easily create these databases with the correct tables to store the underlying GA4GH data models.

First, let's create two empty files that will eventually become our SQLite databases: one for WES and one for DRS.
```
mkdir -p db/wes db/drs
touch db/wes/wes.demo.db db/drs/drs.demo.db
```

Next, let's pull the `ga4gh-starter-kit-utils` docker image if we haven't already:
```
docker pull ga4gh/ga4gh-starter-kit-utils:0.1.0
```

`ga4gh-starter-kit-utils` will help us create the necessary tables for the WES and DRS Starter Kit apps. 

#### WES: Create database tables

First, let's see what migration sets we can apply for WES:

```
docker run ga4gh/ga4gh-starter-kit-utils:0.1.0 database list-migrations wes
```

Result:
```
API Migration Signatures: wes
wes@0.2.0
wes@0.1.0
```

The above result tells us that we can create tables based on the `0.2.0` release of Starter Kit WES. Let's create these tables on the `wes.demo.db` SQLite database:

```
docker run \
    -v `pwd`/db/wes:/db \
    ga4gh/ga4gh-starter-kit-utils:0.1.0 \
    database \
    create-tables \
    -d jdbc:sqlite:/db/wes.demo.db \
    wes@0.2.0
```

You may confirm the operation was successful by reviewing the contents of `wes.demo.db`.

#### DRS: Create database tables and add test dataset

First, let's see what migration sets we can apply for DRS:

```
docker run ga4gh/ga4gh-starter-kit-utils:0.1.0 database list-migrations drs
```

Result:
```
API Migration Signatures: drs
drs@0.1.9
```

The above result tells us that we can create tables based on the `0.1.9` release of Starter Kit DRS. Let's create these tables on the `drs.demo.db` SQLite database:

```
docker run \
    -v `pwd`/db/drs:/db \
    ga4gh/ga4gh-starter-kit-utils:0.1.0 \
    database \
    create-tables \
    -d jdbc:sqlite:/db/drs.demo.db \
    drs@0.1.9
```

Next, let's add the DRS test dataset to `drs.demo.db`:
```
docker run \
    -v `pwd`/db/drs:/db \
    ga4gh/ga4gh-starter-kit-utils:0.1.0 \
    database \
    add-test-dataset \
    -d jdbc:sqlite:/db/drs.demo.db \
    drs@0.1.9
```

You may confirm the operations were successful by reviewing the contents of `drs.demo.db`.

### Service configuration

We plan to set up 3 web services via Docker:
1. WES
2. DRS
3. Starter Kit UI

For each service, we need to create custom YAML configuration files to ensure they run with the correct properties, such as listening port(s), database connections, and service info responses.

First, let's create three empty files that will eventually become our YAML configurations for each service:
```
mkdir -p config/wes config/drs config/ui
touch config/wes/wes.config.yml config/drs/drs.config.yml config/ui/ui.config.yml
```

#### WES YAML Configuration

Using a preferred text editor, write the following YAML config to `config/wes/wes.config.yml`:

```
wes:
  serverProps:
    publicApiPort: 80
    adminApiPort: 4501
  databaseProps:
   url: jdbc:sqlite:/db/wes.demo.db
  serviceInfo:
    id: org.ga4gh.starterkit.cookbook.samtools.wes
    name: Starter Kit Demo WES Service
    description: WES service for samtools view Starter Kit Cookbook
```

#### DRS YAML Configuration

Using a preferred text editor, write the following YAML config to `config/drs/drs.config.yml`:

```
drs:
  serverProps:
    hostname: drs-demo.ga4gh.org
    publicApiPort: 80
    publicApiCorsAllowedOrigins:
      - '*'
    publicApiCorsAllowedHeaders:
      - '*'
    adminApiPort: 4503
    adminApiCorsAllowedOrigins:
      - '*'
    adminApiCorsAllowedHeaders:
      - '*'
  databaseProps:
    url: jdbc:sqlite:/db/drs.demo.db
    poolSize: 8
  serviceInfo:
    id: org.ga4gh.starterkit.cookbook.samtools.drs
    name: Starter Kit Demo DRS Service
    description: DRS service for samtools view Starter Kit Cookbook
```

#### UI YAML Configuration

Using a preferred text editor, write the following YAML config to `config/ui/ui.config.yml`:

```
starterKitUI:
  port: 4504
  services:
    - serviceType: drs
      publicURL: 'http://localhost:4502'
      adminURL: 'http://localhost:4503'
```

The above config points the UI to the DRS service, so that models controlled by DRS (i.e. DRS Objects) can be created and edited via the UI.

## Run Services

With our database and YAML configurations set up, we are ready to run each of the necessary web services.

First, let's pull the docker images we want to run if we haven't already:
```
docker pull ga4gh/ga4gh-starter-kit-wes:0.2.0-nextflow
docker pull ga4gh/ga4gh-starter-kit-drs:0.2.0
docker pull ga4gh/ga4gh-starter-kit-ui:0.2.1
```

For WES, we also need to create a working directory mount between the host and container. To start, we first create this directory on the host, e.g.
```
mkdir -p /tmp/shared/wes
```

### Run Services with docker-compose

We will use `docker-compose` to spin up our 3 dockerized GA4GH services using a single YAML file.

In the project directory, create a file named `docker-compose.yml`, e.g.:
```
touch docker-compose.yml
```

In a text editor, write the following YAML config to `docker-compose.yml`:

```
version: "3.9"
services:
  wes-demo.ga4gh.org:
    container_name: wes-demo.ga4gh.org
    image: ga4gh/ga4gh-starter-kit-wes:0.2.0-nextflow
    ports:
      - "80:80"
      - "4501:4501"
    volumes:
      - $PWD/db/wes:/db
      - $PWD/config/wes:/config
      - /var/run/docker.sock:/var/run/docker.sock
      - /tmp/shared/wes:/tmp/shared/wes
    working_dir: /tmp/shared/wes
    command: -c /config/wes.config.yml
  drs-demo.ga4gh.org:
    container_name: drs-demo.ga4gh.org
    image: ga4gh/ga4gh-starter-kit-drs:0.2.0
    ports:
      - "4502:80"
      - "4503:4503"
    volumes:
      - $PWD/db/drs:/db
      - $PWD/config/drs:/config
    command: java -jar ga4gh-starter-kit-drs.jar -c /config/drs.config.yml
  ui-demo.ga4gh.org:
    container_name: ui-demo.ga4gh.org
    image: ga4gh/ga4gh-starter-kit-ui:0.2.1
    ports:
      - "4504:4504"
    volumes:
      - $PWD/config/ui:/config
    command: -c /config/ui.config.yml
```

Finally, we can start our network of 3 services by simply running:
```
docker-compose up
```

### Explanation of the docker network

Running the above steps just created a **docker network** of 3 services. With `docker-compose`, each service sits within a single virtual network, meaning that one service can easily reach out to another service in the network by referencing the docker container name as if it were a domain name. In the last section of this cookbook, we will see WES making calls to DRS using its `container_name`, i.e. `http://drs-demo.ga4gh.org`

**Note:** Since our API testing tool sits outside the docker network, the URLs we use to make test API calls will be different from the URLs used by one service in the network to reach another service. For example, to call the DRS service we use:
* `http://localhost:4502` outside the docker network, i.e. from API testing tool
* `http://drs-demo.ga4gh.org` inside the docker network, i.e. from WES

Next, let's quickly go over how each of our services are configured to run according to the contents of `docker-compose.yml`.

#### WES docker-compose.yml config

Recall the WES service snippet from `docker-compose.yml:

```
...
  wes-demo.ga4gh.org:
    container_name: wes-demo.ga4gh.org
    image: ga4gh/ga4gh-starter-kit-wes:0.2.0-nextflow
    ports:
      - "80:80"
      - "4501:4501"
    volumes:
      - $PWD/db/wes:/db
      - $PWD/config/wes:/config
      - /var/run/docker.sock:/var/run/docker.sock
      - /tmp/shared/wes:/tmp/shared/wes
    working_dir: /tmp/shared/wes
    command: -c /config/wes.config.yml
...
```

The above configuration:
* starts a Starter Kit WES container that is pre-bundled with Nextflow, so it is possible to submit Nextflow-based workflow run requests to WES.
* exposes ports, enabling us to make API requests from outside the network
* mounts the SQLite database and YAML config passed to the server application
* mounts docker-specific files/directories, so that docker processes can be started from within the container (Nextflow starts its own docker containers for individual workflow runs).
* runs the Starter Kit WES application with the YAML config as input

#### DRS docker-compose.yml config

Recall the DRS service snippet from `docker-compose.yml`

```
...
  drs-demo.ga4gh.org:
    container_name: drs-demo.ga4gh.org
    image: ga4gh/ga4gh-starter-kit-drs:0.2.0
    ports:
      - "4502:80"
      - "4503:4503"
    volumes:
      - $PWD/db/drs:/db
      - $PWD/config/drs:/config
    command: java -jar ga4gh-starter-kit-drs.jar -c /config/drs.config.yml
...
```

The above configuration:
* starts a Starter Kit DRS container
* exposes ports, enabling us to make API requests from outside the network
* mounts the SQLite database and YAML config passed to the server application
* runs the Starter Kit DRS application with the YAML config as input

**NOTE**: The `0.1.9` DRS database schema we applied is compatible with the `0.2.0` release of Starter Kit DRS.

#### Starter Kit UI docker-compose.yml config

Recall the UI service snippet from `docker-compose.yml`

```
...
  ui-demo.ga4gh.org:
    container_name: ui-demo.ga4gh.org
    image: ga4gh/ga4gh-starter-kit-ui:0.2.1
    ports:
      - "4504:4504"
    volumes:
      - $PWD/config/ui:/config
    command: -c /config/ui.config.yml
```

The above configuration:
* starts a Starter Kit UI container
* exports ports, enabling us to access the UI via web browser
* mounts the YAML config passed to the UI server application

## Validate Services

Now, let's validate each service is running correctly

### Validate WES

Let's confirm the WES service is running correctly by checking its Service Info. Using a preferred API testing tool, submit the following request:

```
GET http://localhost/ga4gh/wes/v1/service-info
```

You should receive the following Service Info as a response:

```
{
    "id": "org.ga4gh.starterkit.cookbook.samtools.wes",
    "name": "Starter Kit Demo WES Service",
    "description": "WES service for samtools view Starter Kit Cookbook",
    "contactUrl": "mailto:info@ga4gh.org",
    "documentationUrl": "https://github.com/ga4gh/ga4gh-starter-kit-wes",
    "createdAt": "2020-01-15T12:00:00Z",
    "updatedAt": "2020-01-15T12:00:00Z",
    "environment": "test",
    "version": "0.2.0",
    "type": {
        "group": "org.ga4gh",
        "artifact": "wes",
        "version": "1.0.1"
    },
    "organization": {
        "name": "Global Alliance for Genomics and Health",
        "url": "https://ga4gh.org"
    },
    "workflowTypeVersions": {
        "NEXTFLOW": [
            "21.04.0"
        ]
    },
    "workflowEngineVersions": {
        "NATIVE": ""
    }
}
```

Note that the `id`, `name`, and `description` fields are the same as what was specified in the YAML config.

### Validate DRS

Let's confirm the DRS service is running correctly by checking its Service Info. Using a preferred API testing tool, submit the following request:

```
GET http://localhost:4502/ga4gh/drs/v1/service-info
```

You should receive the following Service Info as a response:

```
{
    "id": "org.ga4gh.starterkit.cookbook.samtools.drs",
    "name": "Starter Kit Demo DRS Service",
    "description": "DRS service for samtools view Starter Kit Cookbook",
    "contactUrl": "mailto:info@ga4gh.org",
    "documentationUrl": "https://github.com/ga4gh/ga4gh-starter-kit-drs",
    "createdAt": "2020-01-15T12:00:00Z",
    "updatedAt": "2020-01-15T12:00:00Z",
    "environment": "test",
    "version": "0.1.0",
    "type": {
        "group": "org.ga4gh",
        "artifact": "drs",
        "version": "1.1.0"
    },
    "organization": {
        "name": "Global Alliance for Genomics and Health",
        "url": "https://ga4gh.org"
    }
}
```

Note that the `id`, `name`, and `description` fields are the same as what was specified in the YAML config.

Since we added the test DRS dataset, we can also verify that the `/objects` endpoint is working correctly by supplying the test ID `b8cd0667-2c33-4c9f-967b-161b905932c9`. Submit the following request:

```
GET http://localhost:4502/ga4gh/drs/v1/objects/b8cd0667-2c33-4c9f-967b-161b905932c9
```

You should receive the following DRS Object response:

```
{
    "id": "b8cd0667-2c33-4c9f-967b-161b905932c9",
    "description": "Open dataset of 384 phenopackets",
    "created_time": "2021-03-12T20:00:00Z",
    "name": "phenopackets.test.dataset",
    "size": 143601,
    "updated_time": "2021-03-13T12:30:45Z",
    "version": "1.0.0",
    "checksums": [
        {
            "checksum": "8711d59ca4264b3e3d0ce16349d94d0ab8ce493e",
            "type": "sha1"
        },
        {
            "checksum": "930014c944b655323ade3f4b239178022bfb5443ef6b280a7d7d69292867d010",
            "type": "sha256"
        },
        {
            "checksum": "938b077a59b11ad6e5958f9f34148f18",
            "type": "md5"
        }
    ],
    "self_uri": "drs://drs-demo.ga4gh.org/b8cd0667-2c33-4c9f-967b-161b905932c9",
    "contents": [
        {
            "name": "phenopackets.mundhofir.family",
            "drs_uri": [
                "drs://drs-demo.ga4gh.org/1af5cdcf-898c-4dbc-944e-1ac95e82c0ea"
            ],
            "id": "1af5cdcf-898c-4dbc-944e-1ac95e82c0ea"
        },
        {
            "name": "phenopackets.zhang.family",
            "drs_uri": [
                "drs://drs-demo.ga4gh.org/355a74bd-6571-4d4a-8602-a9989936717f"
            ],
            "id": "355a74bd-6571-4d4a-8602-a9989936717f"
        },
        {
            "name": "phenopackets.cao.family",
            "drs_uri": [
                "drs://drs-demo.ga4gh.org/a1dd4ae2-8d26-43b0-a199-342b64c7dff6"
            ],
            "id": "a1dd4ae2-8d26-43b0-a199-342b64c7dff6"
        },
        {
            "name": "phenopackets.lalani.family",
            "drs_uri": [
                "drs://drs-demo.ga4gh.org/c69a3d6c-4a28-4b7c-b215-0782f8d62429"
            ],
            "id": "c69a3d6c-4a28-4b7c-b215-0782f8d62429"
        }
    ]
}
```

### Validate Starter Kit UI

Let's confirm the UI service is running correctly by visiting `http://localhost:4504/` via web browser (such as Google Chrome). You should see the following landing page:

![UI Landing](/img/cookbooks/samtools-view-drs-wes/ui-landing.png)

If we navigate to the `/services` view (by clicking "Enter" -> "Get Started"), we should also see a single **valid service**, representing the Starter Kit DRS instance we spun up and pointed the UI to:

![UI Services](/img/cookbooks/samtools-view-drs-wes/ui-services.png)

Lastly, if we click the "View" button for the DRS service, we will be directed to the `/services/org.ga4gh.starterkit.cookbook.samtools.drs` view. This displays the DRS instance's Service Info, and also leads to forms where we can create and edit DRS Objects for that instance.

![UI Service](/img/cookbooks/samtools-view-drs-wes/ui-service.png)

In the [next phase](./run-using-http-urls), we will run a Nextflow-based `samtools view` workflow using API calls to the WES service we set up.
