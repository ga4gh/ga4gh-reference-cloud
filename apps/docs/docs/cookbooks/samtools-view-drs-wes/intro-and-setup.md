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

Let's begin by configuring and running our two starter kit services: WES and DRS.

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
wes@0.1.0
```

The above result tells us that we can create tables based on the `0.1.0` release of Starter Kit WES. Let's create these tables on the `wes.demo.db` SQLite database:

```
docker run \
    -v `pwd`/db/wes:/db \
    ga4gh/ga4gh-starter-kit-utils:0.1.0 \
    database \
    create-tables \
    -d jdbc:sqlite:/db/wes.demo.db \
    wes@0.1.0
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
    publicApiPort: 4500
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
    publicApiPort: 4502
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

## Run Services

With our database and YAML configurations set up, we are ready to run each of the necessary web services.

### Run WES Service

We are ready to spin up the WES service. If you haven't already done so, pull the `0.1.0-nextflow` release of the Starter Kit WES docker image:
```
docker pull ga4gh/ga4gh-starter-kit-wes:0.1.0-nextflow
```

The above image comes pre-bundled with Nextflow, so it is possible to submit Nextflow-based workflow run requests to WES.

We also need to create a working directory mount between the host and container. To start, we first create this directory on the host, e.g.
```
mkdir -p /tmp/shared/wes
```

Next, let's start the Starter Kit WES service using `docker run`, taking care to mount both the SQLite database and YAML config. We also need to mount docker-specific files/directories, so that docker processes can be started from within the container (Nextflow starts its own docker containers for individual workflow runs).

```
docker run \
  --rm \
  -d \
  --name starterkit-demo-wes \
  -p 4500:4500 \
  -p 4501:4501 \
  -v `pwd`/db/wes:/db \
  -v `pwd`/config/wes:/config \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /tmp/shared/wes:/tmp/shared/wes \
  --workdir "/tmp/shared/wes" \
  ga4gh/ga4gh-starter-kit-wes:0.1.0-nextflow \
  -c /config/wes.config.yml
```

Let's confirm the WES service is running correctly by checking its Service Info. Using a preferred API testing tool, submit the following request:

```
GET http://localhost:4500/ga4gh/wes/v1/service-info
```

You should receive the following Service Info as a response:

![WES Service Info](/img/cookbooks/samtools-view-drs-wes/wes-serviceinfo.png)

Note that the `id`, `name`, and `description` fields are the same as what was specified in the YAML config.

### Run DRS Service

We are ready to spin up the DRS service. If you haven't already done so, pull the `0.2.0` release of the Starter Kit DRS docker image:
```
docker pull ga4gh/ga4gh-starter-kit-drs:0.2.0
```

**NOTE**: The `0.1.9` DRS database schema we applied is compatible with the `0.2.0` release of Starter Kit DRS.

Next, let's start the Starter Kit DRS service using `docker run`, taking care to mount both the SQLite database and YAML config.

```
docker run \
  --rm \
  -d \
  --name starterkit-demo-drs \
  -p 4502:4502 \
  -p 4503:4503 \
  -v `pwd`/db/drs:/db \
  -v `pwd`/config/drs:/config \
  ga4gh/ga4gh-starter-kit-drs:0.2.0 \
  java -jar ga4gh-starter-kit-drs.jar \
  -c /config/drs.config.yml
```

Let's confirm the DRS service is running correctly by checking its Service Info. Using a preferred API testing tool, submit the following request:

```
GET http://localhost:4502/ga4gh/drs/v1/service-info
```

You should receive the following Service Info as a response:

![DRS Service Info](/img/cookbooks/samtools-view-drs-wes/drs-serviceinfo.png)

Note that the `id`, `name`, and `description` fields are the same as what was specified in the YAML config.

Since we added the test DRS dataset, we can also verify that the `/objects` endpoint is working correctly by supplying the test ID `b8cd0667-2c33-4c9f-967b-161b905932c9`. Submit the following request:

```
GET http://localhost:4502/ga4gh/drs/v1/objects/b8cd0667-2c33-4c9f-967b-161b905932c9
```

The response you receive should resemble the following partial response:

![DRS Object Partial Response](/img/cookbooks/samtools-view-drs-wes/drs-partial-response.png)

### Run Starter Kit UI

We are ready to spin up the Starter Kit UI service. In the YAML config, we point the UI to the DRS service, so that models controlled by DRS (i.e. DRS Objects) can be created and edited via the UI.

If you haven't already done so, pull the `0.2.1` release of the Starter Kit UI docker image:
```
docker pull ga4gh/ga4gh-starter-kit-ui:0.2.1
```

Next, let's start the Starter Kit UI service using `docker run`, taking care to mount the YAML config:

```
docker run \
  --rm \
  -d \
  --name starterkit-demo-ui \
  -p 4504:4504 \
  -v `pwd`/config/ui:/config \
  ga4gh/ga4gh-starter-kit-ui:0.2.1 \
  -c /config/ui.config.yml
```

Let's confirm the UI service is running correctly by visiting `http://localhost:4504/` via web browser (such as Google Chrome). You should see the following landing page:

![UI Landing](/img/cookbooks/samtools-view-drs-wes/ui-landing.png)

If we navigate to the `/services` view (by clicking "Enter" -> "Get Started"), we should also see a single **valid service**, representing the Starter Kit DRS instance we spun up and pointed the UI to:

![UI Services](/img/cookbooks/samtools-view-drs-wes/ui-services.png)

Lastly, if we click the "View" button for the DRS service, we will be directed to the `/services/org.ga4gh.starterkit.cookbook.samtools.drs` view. This displays the DRS instance's Service Info, and also leads to forms where we can create and edit DRS Objects for that instance.

![UI Service](/img/cookbooks/samtools-view-drs-wes/ui-service.png)