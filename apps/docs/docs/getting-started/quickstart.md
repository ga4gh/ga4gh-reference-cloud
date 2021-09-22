---
sidebar_position: 2
---

# Quickstart Tutorial

The following [tutorial video](#tutorial-video) provides instructions on how to get up and running with one of the applications in the Starter Kit suite with only a couple docker commands and API calls.

A [transcript](#transcript) of the video is also available.

### Tutorial Video

<video width="100%" controls>
  <source
    src="/video/intro/QuickstartTutorial.mp4"
    type="video/mp4"
  />
</video>

**DRS ID used in tutorial video:** `1a570e4e-2489-4218-9333-f65549495872`

### Commands and API Calls

For reference, the docker commands and API calls used in the video are listed below (in the order they appear).

**CLI Command:** Pull the Starter Kit DRS docker image:
```
docker pull ga4gh/ga4gh-starter-kit-drs:0.2.1
```

**CLI Command:** Run a Starter Kit DRS container instance
```
docker run -d --rm --name starter-kit-drs -p 4500:4500 -p 4501:4501 ga4gh/ga4gh-starter-kit-drs:0.2.1
```

**HTTP Request:** View DRS `/service-info`
```
GET http://localhost:4500/ga4gh/drs/v1/service-info
```

**HTTP Request:** View DRS Object
```
GET http://localhost:4500/ga4gh/drs/v1/objects/1a570e4e-2489-4218-9333-f65549495872
```

**HTTP Request:** Get Access URL for raw data

**Note:** Use the `access_id` (under `access_methods`) returned in the previous API call to complete the following URL: 
```
GET http://localhost:4500/ga4gh/drs/v1/objects/1a570e4e-2489-4218-9333-f65549495872/access/{access_id}
```

**HTTP Request:** Fetch raw data

**Note:** Use the `url` value returned in the previous API call to fetch the raw data. 

### Other DRS IDs

You may use submit any of the following DRS IDs to the `/ga4gh/drs/v1/objects/{object_id}` endpoint to explore accessing more DRS Objects via the Starter Kit DRS API:

* b8cd0667-2c33-4c9f-967b-161b905932c9
* a1dd4ae2-8d26-43b0-a199-342b64c7dff6
* 1a570e4e-2489-4218-9333-f65549495872
* 4d83ba3f-a476-4c7c-868f-3d1fcf77fe29
* 924901d5-6d31-4c33-b443-7931eadfac4b
* 0f8abce3-e161-4bdf-981f-86257d505d69
* c69a3d6c-4a28-4b7c-b215-0782f8d62429
* 456e9ee0-5b60-4f38-82b5-83ba5d338038
* 1af6b862-7fc8-411a-86c5-d8e280e5b77a
* c37b37fd-0450-432d-b6aa-9ffdece35ad0
* 0bb9d297-2710-48f6-ab4d-80d5eb0c9eaa
* a3bb76d7-76ae-414b-81f7-97e663b02206
* 1af5cdcf-898c-4dbc-944e-1ac95e82c0ea
* 2506f0e1-29e4-4132-9b37-f7452dc8a89b
* c00c264a-8f17-471f-8ded-1a1f10e965ac
* 355a74bd-6571-4d4a-8602-a9989936717f
* 697907bf-d5bd-433e-aac2-1747f1faf366
* 3a45fab2-f350-445d-a137-4b1f946bf184
* ac53ca59-46f3-4f61-b1e7-bb75a49bfbfe
* 1275f896-4c8f-47e1-99a1-873a6b2ef5fb
* 8f40acc0-0c54-45c5-8c85-a6f5fb32a1a7
* 41898242-62a9-4129-9a2c-5a4e8f5f0afb
* 6b994f18-6189-4233-bb83-139686490d68

### Transcript

Hello, and welcome to the quickstart tutorial for the GA4GH Starter Kit. The Starter Kit is a suite of microservice applications. Each application acts as a server implementation of a single GA4GH API specification.

Each GA4GH API spec outlines a standardized contract about what endpoints and data types a webserver supports. Different organizations adopt the specifications and serve their own datasets according to the protocols prescribed therein, enabling standardized genomic data access and analysis over the web. You may be familiar with some GA4GH API specifications already, such as Workflow Execution Service, htsget API, refget API, and Beacon. With the Starter Kit, you will be able to get up and running with GA4GH APIs quickly, without having to code them yourself.

In this tutorial, we will download, install, and run the Starter Kit implementation of the Data Repository Service specification on our local laptop or desktop.

Data Repository Service, or DRS, provides minimal metadata to genomic data files that may be used as input to an analysis. It also provides information about what copies of the data exist and where, such as across different geographic regions and cloud providers. This lets the client decide the best location to access the raw data from.

As a prerequisite, please ensure you have Docker and an API testing tool installed on your local machine. In this video, I will be using Postman to make requests to the DRS API.

Let’s get started. open your command line terminal and navigate to an empty directory.

We will use docker to pull the DRS server image: `docker pull ga4gh/ga4gh-starter-kit-drs:0.2.1`

It may take a minute to download.

Once complete, we are ready to run a container instance. Execute the following command to run the DRS service: `docker run -d —rm —name starter-kit-drs -p 4500:4500 -p 4501:4501 ga4gh/ga4gh-starter-kit-drs:0.2.1`

We should now have a running, dockerized service. Now, let’s make some HTTP requests to the DRS service and inspect the responses we receive. First, let’s hit the service info endpoint, which should tell us more about the service itself. In your API testing tool, navigate to: `http://localhost:4500/ga4gh/drs/v1/service-info`

From here, the service informs the client about its name, description, and other service metadata. The service also explicitly tells us that it has implemented the DRS version 1.1.0 specification via its “type” attribute, which lets new clients that discover the service know what operations it should support.

Next, let’s use the “objects” endpoint to inspect a single DRS object. Below this video window, there’s an object ID we can use to retrieve the corresponding object’s metadata. Copy the id, and append it to: `http://localhost:4500/ga4gh/drs/v1/objects/`

The final URL should look like this: `http://localhost:4500/ga4gh/drs/v1/objects/1a570e4e-2489-4218-9333-f65549495872`

The JSON response displays an instance of a DRS object. We can see the object’s id, name, description, file size, and more, all captured under standardized fields outlined in the specification itself. Towards the end of the JSON object, we see an array of 2 access methods, one provides us with an access id, and the other provides us with a URL to an Amazon Web Services S3 bucket. We can use either access method to fetch the raw data, which should be the same regardless of which method we choose.

Let’s use the access id method. Using the same object id we provided in the previous API request, let’s supply the access id to the server’s “Access” endpoint: `http://localhost:4500/ga4gh/drs/v1/objects/1a570e4e-2489-4218-9333-f65549495872/access/{access_id}`

The server then gives us a direct URL that should return the raw data for this object. Let’s make a request to this endpoint.

Finally, we see the raw data associated with the DRS object. The data is in Phenopackets format, which is another GA4GH specification for clinical and phenotypic data representation, and can be used as input to clinical analysis workflows. DRS can be used to serve any data in any format. It is commonly used to serve alignment files, such as SAM, BAM, or CRAM, variant files, such as VCF and BCF, and more.

You can now stop and remove the container with a docker stop command: `docker stop starter-kit-drs`

On the other hand, you may wish to keep the server running to further test out the DRS API

In this tutorial, we used only a couple docker commands to download and start a local DRS service, then we made API requests to the service to access metadata and raw data of a single DRS object.

From here, you may wish to make more API requests to the local DRS service, using the list of IDs at the bottom of this page. You can also explore the documentation on the other available Starter Kit APIs, as well as how to configure those APIs to serve your own custom datasets, rather than the default dataset we explored here.

Lastly, please be sure to check out the cookbooks section, which provides step-by-step instructions on how to create a network of multiple GA4GH services on your local machine, and leverage them to accomplish scientific tasks with only a few API calls.
