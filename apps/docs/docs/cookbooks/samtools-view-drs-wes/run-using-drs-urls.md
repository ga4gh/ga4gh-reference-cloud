---
sidebar_position: 3
---

# Run samtools view on WES with DRS URLs as input

In the previous section, we used **direct** `https://` URLs as input BAM files to the `samtools view` workflow. This is sufficient for multiple use cases, however, we may want to provide an **indirect** means of referencing workflow inputs, allowing the WES service to **resolve** the best paths to those inputs based on a number of criteria.

The Data Repository Service (DRS) provides an indirection layer to data. Each `DRS Object` served by DRS contains some metadata about a set of bytes (e.g. an input file). `DRS Object`s also provide one or more access methods to the raw file bytes. This allows multiple, identical copies of data to be stored on different cloud storage resources (e.g. AWS S3, Google Storage), and in different geographical regions. A resolving service (in this case, WES) can choose where to retrieve the raw data from based on its own capabilities. Different resolving (WES) services have the opportunity to choose the best "mirror" to access the data from. This could be based on regional proximity or supported cloud infrastructure. 

Let's run an API call to our DRS Service, requesting the `DRS Object` with an id of `0f8abce3-e161-4bdf-981f-86257d505d69`:

```
GET http://localhost:4502/ga4gh/drs/v1/objects/0f8abce3-e161-4bdf-981f-86257d505d69
```

The response from this API call is a `DRS Object` in JSON format, which contains standardized attributes like `id`, `name`, `description`, etc. In particular is the `access_methods` attribute, e.g.:

```
{
    ...
    "access_methods": [
        {
            "access_id": "b16b6733-5bf7-47c4-b029-a59b564cac23",
            "type": "https"
        },
        {
            "access_url": {
                "url": "s3://ga4gh-demo-data/phenopackets/Cao-2018-TGFBR2-Patient_4.json"
            },
            "type": "s3",
            "region": "us-east-2"
        }
    ]
    ...
}
```

The returned `DRS Object` has 2 access methods:
1. An `access_id` that we can pass back to the server to get a properly formatted URL to the data.
2. An `s3://` formatted URL to the data.

If we focus on (2), we see that the raw data for this `DRS Object` is stored in an AWS S3 bucket. Any WES service that understands `s3://` URLs could resolve this to a direct HTTP(s) URL as a staging step prior to running the workflow.

The Starter Kit WES service is capable of resolving `s3://` URLs. If we set up the tabula muris BAM files as `DRS Objects`, we can submit `DRS IDs` to WES as part of the workflow run request, and allow WES to resolve these to direct URLs.

## Create DRS Objects via the UI

### Navigate to the form

Let's create `DRS Objects` for some of the tabula muris BAM files. On a web browser, navigate to `http://localhost:4504` (where our UI is deployed), then click the following UI buttons to get to the "Create New DRS Object" form:

Enter -> Services -> View -> DRS Objects -> New DRS Object

You may also find the following video helpful to navigate to the form:

<video width="100%" controls>
  <source
    src="/video/cookbooks/samtools-view-drs-wes/SamtoolsViewDrsWes-NavigateToCreateForm.mp4"
    type="video/mp4"
  />
</video>

### Create the object

On the form, we will create one DRS Object representing a single Tabula Muris BAM file stored on the public AWS S3 bucket (at `https://s3.amazonaws.com/czbiohub-tabula-muris/facs_bam_files/A1-B000126-3_39_F-1-1_R1.mus.Aligned.out.sorted.bam`). Let's enter the following information into the web UI fields:

| Field Name | Field Value |
|------------|-------------|
| id | tabulamuris.A1-B000126-3_39_F-1-1_R1 |
| name | Tabula Muris FACS A1-B000126-3_39_F-1-1_R1 BAM file |
| description | FACS BAM file from Tabula Muris dataset id A1-B000126-3_39_F-1-1_R1 |
| created time | (no update; leave as default) |
| updated time | (no update; leave as default) |
| version | 1.0.0 |
| bundle or blob? | blob |
| size | 29298050 |
| checksums [0] | type: md5, checksum: eadcf4e4a9f0b3508c186a3d59002d61 |
| checksums [1] | type: sha1, checksum: a453b1d4efefa76a3f4f948cefc6853d1b5aa9b4 |
| checksums [2] | type: sha256, checksum: 66eac14b2563e594030491caedc99160725a7d0d614f9db5ee888dd351e02430 |
| aws s3 access points [0] | region: us-east-1, bucket: czbiohub-tabula-muris, key: /facs_bam_files/A1-B000126-3_39_F-1-1_R1.mus.Aligned.out.sorted.bam

Once all information has been entered, click "Submit" to create the object.

The following video shows the creation of the DRS Object, followed by validation that the object was created:

<video width="100%" controls>
  <source
    src="/video/cookbooks/samtools-view-drs-wes/SamtoolsViewDrsWes-CreateDrsObject.mp4"
    type="video/mp4"
  />
</video>

### Validate and inspect object

Once we've submitted the new DRS Object, we can confirm that it is accessible from the DRS service via HTTP request:

```
GET http://localhost:4502/ga4gh/drs/v1/objects/tabulamuris.A1-B000126-3_39_F-1-1_R1
```

should return the following response (`created_time` and `updated_time` will be different): 
```
{
    "id": "tabulamuris.A1-B000126-3_39_F-1-1_R1",
    "description": "FACS BAM file from Tabula Muris dataset id A1-B000126-3_39_F-1-1_R1",
    "created_time": "2021-09-02T14:27:00Z",
    "name": "Tabula Muris FACS A1-B000126-3_39_F-1-1_R1 BAM file",
    "size": 29298050,
    "updated_time": "2021-09-02T14:27:00Z",
    "version": "1.0.0",
    "checksums": [
        {
            "checksum": "a453b1d4efefa76a3f4f948cefc6853d1b5aa9b4",
            "type": "sha1"
        },
        {
            "checksum": "66eac14b2563e594030491caedc99160725a7d0d614f9db5ee888dd351e02430",
            "type": "sha256"
        },
        {
            "checksum": "eadcf4e4a9f0b3508c186a3d59002d61",
            "type": "md5"
        }
    ],
    "self_uri": "drs://localhost:4502/tabulamuris.A1-B000126-3_39_F-1-1_R1",
    "access_methods": [
        {
            "access_url": {
                "url": "s3://czbiohub-tabula-muris/facs_bam_files/A1-B000126-3_39_F-1-1_R1.mus.Aligned.out.sorted.bam"
            },
            "type": "s3",
            "region": "us-east-1"
        }
    ]
}
```

Let's inspect both the `self_uri` and `access_methods` attributes further, as they are most important for running the workflow on WES using DRS IDs as input:

#### `self_uri`

The value of `self_uri` is `drs://localhost:4502/tabulamuris.A1-B000126-3_39_F-1-1_R1`. The `drs://` scheme indicates that this is a standardized DRS URI, in which both the server (`localhost:4502`) and the object id (`tabulamuris.A1-B000126-3_39_F-1-1_R1`) are indicated. Any service that understands DRS URIs will be able to resolve this to an HTTP URL to obtain the corresponding DRS Object. The Starter Kit WES service can resolve DRS URIs.

#### `access_methods`

`access_methods` contains a single method pointing to the AWS S3 bucket and key to the BAM file. Again, the Starter Kit WES service can resolve `s3://` based URLs to `http(s)` URLs to make them suitable as input to the workflow. 

## WES workflow run request

### Submit workflow run

We are ready to rerun our workflow. Now, instead of submitting the input HTTPS URL directly, we will submit the DRS URL as an input parameter to WES, and allow WES to resolve it.

Issue the following HTTP request:

**Method and URL**

```
POST http://localhost:4500/ga4gh/wes/v1/runs
```

**Headers**

```
Content-Type: multipart/form-data
```

**Form data (body)**
```
workflow_type: NEXTFLOW
workflow_type_version: 21.04.0
workflow_url: https://github.com/jb-adams/samtools-view-count-nf
workflow_params: {"input": "drs://drs-demo.ga4gh.org/tabulamuris.A1-B000126-3_39_F-1-1_R1"}
```

Note that compared to the `POST` request from the previous section, only the `workflow_params` have changed.

### Monitor workflow run

If we give the workflow run a minute to complete, we can again monitor its status by passing the `run_id` (in this case, `940f0c51-2dab-4e02-ab3a-92dfaf68d65f`) to the `GET /runs/{run_id}` endpoint:

```
GET http://localhost/ga4gh/wes/v1/runs/940f0c51-2dab-4e02-ab3a-92dfaf68d65f
```

Response:
```
{
    "run_id": "940f0c51-2dab-4e02-ab3a-92dfaf68d65f",
    "request": {
        "workflow_params": {
            "input": "drs://drs-demo.ga4gh.org/tabulamuris.A1-B000126-3_39_F-1-1_R1"
        },
        "workflow_type": "NEXTFLOW",
        "workflow_type_version": "21.04.0",
        "workflow_url": "https://github.com/jb-adams/samtools-view-count-nf"
    },
    "state": "COMPLETE",
    "run_log": {
        "name": "jb-adams/samtools-view-count-nf",
        "cmd": [
            "#!/bin/bash -ue",
            "echo \"Running samtools view on https://s3.us-east-1.amazonaws.com/czbiohub-tabula-muris/facs_bam_files/A1-B000126-3_39_F-1-1_R1.mus.Aligned.out.sorted.bam\" >&2",
            "samtools view -c https://s3.us-east-1.amazonaws.com/czbiohub-tabula-muris/facs_bam_files/A1-B000126-3_39_F-1-1_R1.mus.Aligned.out.sorted.bam"
        ],
        "start_time": "2021-09-02T19:15:33Z",
        "end_time": "2021-09-02T19:15:43Z",
        "stdout": "http://localhost/ga4gh/wes/v1/logs/nextflow/stdout/940f0c51-2dab-4e02-ab3a-92dfaf68d65f?workdirs=0a%2F0838ad17d4486bace1dc908e9e44ca",
        "stderr": "http://localhost/ga4gh/wes/v1/logs/nextflow/stderr/940f0c51-2dab-4e02-ab3a-92dfaf68d65f?workdirs=0a%2F0838ad17d4486bace1dc908e9e44ca",
        "exit_code": 0
    },
    "task_logs": [
        {
            "name": "samtools_view",
            "cmd": [
                "#!/bin/bash -ue",
                "echo \"Running samtools view on https://s3.us-east-1.amazonaws.com/czbiohub-tabula-muris/facs_bam_files/A1-B000126-3_39_F-1-1_R1.mus.Aligned.out.sorted.bam\" >&2",
                "samtools view -c https://s3.us-east-1.amazonaws.com/czbiohub-tabula-muris/facs_bam_files/A1-B000126-3_39_F-1-1_R1.mus.Aligned.out.sorted.bam"
            ],
            "start_time": "2021-09-02T19:15:33Z",
            "end_time": "2021-09-02T19:15:43Z",
            "stdout": "http://localhost/ga4gh/wes/v1/logs/nextflow/stdout/940f0c51-2dab-4e02-ab3a-92dfaf68d65f/0a/0838ad17d4486bace1dc908e9e44ca",
            "stderr": "http://localhost/ga4gh/wes/v1/logs/nextflow/stderr/940f0c51-2dab-4e02-ab3a-92dfaf68d65f/0a/0838ad17d4486bace1dc908e9e44ca",
            "exit_code": 0
        }
    ],
    "outputs": {}
}
```

Just as before, the response payload tells us that the workflow run completed successfully. If we follow the URL at `run_log.stdout`, we again see the expected read count of `848036` printed to stdout.

But there's a key difference between this run and the run launched in the previous section. Let's look at original request parameters we submitted to WES, indicated by `request.workflow_params`:
```
{
  ...
  "request": {
        "workflow_params": {
            "input": "drs://drs-demo.ga4gh.org/tabulamuris.A1-B000126-3_39_F-1-1_R1"
        },
        ...
  },
  ...
}
```

Here, we see the DRS URL (representing our DRS Object) we submitted. However, when we look at the raw workflow commands under `run_log.cmd`, we see:
```
samtools view -c https://s3.us-east-1.amazonaws.com/czbiohub-tabula-muris/facs_bam_files/A1-B000126-3_39_F-1-1_R1.mus.Aligned.out.sorted.bam
```

How did WES know to run the workflow using that BAM file URL? Simply put, the WES service performed 2 URL resolutions prior to running the workflow on the `drs://` URL. First, to lookup the contents of the DRS Object, and second, to resolve the S3 access method it found into the final HTTPS URL.

While we only explored the resolution of `s3://` URLs, the DRS specification supports many URL schemes (e.g. `file://`, `https://`, `gs://`), giving data providers flexibility over the access methods they can support, and giving WES implementations choice over which access method(s) to make use of.

## Summary

In this cookbook, we explored how to use WES and DRS to launch a simple workflow using only HTTP requests. First, by submitting direct URLs as input parameters, and second, by submitting DRS URLs and allowing WES to resolve the DRS Object to a raw URL for fetching bytes.

If you wish to explore the concepts covered in this cookbook further, you could:
1. Create more DRS Objects based on the [table of Tabula Muris S3 BAM objects](./run-using-http-urls#run-workflow-with-different-inputs). Register one DRS Object per BAM file via the UI, and attempt to run the workflow using the resulting DRS URL as input. Validate the workflow run outputs the correct number of reads.
2. Attempt to run your own Nextflow workflow via WES. Currently, Nextflow workflows tracked on Github can be submitted to Starter Kit WES...

Return to table... 