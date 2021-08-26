---
sidebar_position: 3
---

# Submitting workflow run requests to Starter Kit WES

The Starter Kit WES docker image comes prebundled with the ability to run jobs via Nextflow. In this section we will confirm that we can execute HTTP requests to a running Starter Kit WES service to trigger a Nextflow workflow run, and obtain the outputs of the run.

## Server Setup

First, let's create the YAML config file that the Starter Kit WES service will load properties from. In a new working directory, create a subdirectory `config` with a single file `config.yml`:

```
mkdir config
touch config/config.yml
```

Using any text editor, write the following YAML to `config/config.yml`:

```yaml
wes:
  serverProps:
    publicApiPort: 7500
    adminApiPort: 7501
  serviceInfo:
    id: org.ga4gh.demo.wes.test
    name: WES API Test Demo
  databaseProps:
    url: jdbc:sqlite:/home/user/db/ga4gh-starter-kit.dev.db
```

We also need to create a working directory mount between the host and container. To start, we first create this directory on the host, e.g.
```
mkdir -p /tmp/shared/wes
```

We are ready to start the WES service. Run the following command to spin up a WES + Nextflow docker container (substituting any parameters as relevant to your filesystem/environment):

```
docker run \
  -d \
  --name sk-wes-test \
  --rm \
  -v `pwd`/config:/config \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -p 7500:7500 \
  -p 7501:7501 \
  -v /tmp/shared/wes:/tmp/shared/wes \
  --workdir "/tmp/shared/wes" \
  ga4gh/ga4gh-starter-kit-wes:0.1.0-nextflow \
  -c /config/config.yml
```

In the above command, we create a docker container named `sk-wes-test` in `detached` mode. We mount the YAML config file. We also mount the docker daemon socket and the working directory to allow the docker container to run docker processes on the host.

## Test HTTP Requests

### Service Info

Let's begin by making a request to the `/service-info` endpoint. Using your preferred API testing tool (e.g. Postman), issue the following HTTP request:
```
GET http://localhost:7500/ga4gh/wes/v1/service-info
```

You should receive a response with a status of `200`, and a response body of:
```
{
    "id": "org.ga4gh.demo.wes.test",
    "name": "WES API Test Demo",
    "description": "An open source, community-driven implementation of the GA4GH Workflow Execution Service (WES)API specification.",
    "contactUrl": "mailto:info@ga4gh.org",
    "documentationUrl": "https://github.com/ga4gh/ga4gh-starter-kit-wes",
    "createdAt": "2020-01-15T12:00:00Z",
    "updatedAt": "2020-01-15T12:00:00Z",
    "environment": "test",
    "version": "0.0.1",
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

### Submit Workflow Run Request

Next, let's trigger a workflow run by submitting a `POST` request to the service's `/runs` endpoint. Prepare the following HTTP request using your API testing tool:

**Method and URL**

```
POST http://localhost:7500/ga4gh/wes/v1/runs
```

**Headers**

```
Content-Type: multipart/form-data
```

**Form Data (body)**

```
workflow_type: NEXTFLOW
workflow_type_version: 21.04.0
workflow_url: https://github.com/jb-adams/md5-nf
workflow_params: {"file_int": 3}
```

In the above request, we specify a workflow run according to the WES specification using the following parameter values:
* `workflow_type`: specifies that the workflow we want to run is written in Nexflow
* `workflow_type_version`: specifies that we want to run Nextflow version 21.04.0
* `workflow_url`: The workflow we want to run is hosted on Github. The workflow simply computes checksum digest values (`md5`, `sha1`, and `sha256`) on 1 of 5 files prebundled with the workflow
* `workflow_params`: We specify a `file_int` of `3`, that is, we want to compute checksum values on the 3rd prebundled file

You should receive a response with a status of `200`, and a response body resembling the following:
```
{
    "run_id": "e4d25c91-f950-4c4b-8616-cfafe83296c8"
}
```

This indicates the server successfully accepted the job request. The exact value of `run_id` will be different than the above, as the server randomly assigns a unique ID to each request.

### Monitor Workflow Status and Obtain Outputs

Using the `run_id` we obtained from the service, we can obtain information about the run. Issue the following request (using the `run_id` you obtained):

```
GET http://localhost:7500/ga4gh/wes/v1/runs/e4d25c91-f950-4c4b-8616-cfafe83296c8
```

You should receive a response with a status of `200`, and a response body resembling:
```
{
    "run_id": "e4d25c91-f950-4c4b-8616-cfafe83296c8",
    "request": {
        "workflow_params": {
            "file_int": 3
        },
        "workflow_type": "NEXTFLOW",
        "workflow_type_version": "21.04.0",
        "workflow_url": "https://github.com/jb-adams/md5-nf"
    },
    "state": "COMPLETE",
    "run_log": {
        "name": "jb-adams/md5-nf",
        "cmd": [
            "#!/bin/bash -ue",
            "echo \"Running md5 on /data/3.json\" >&2",
            "md5sum /data/3.json | cut -f 1 -d ' '",
            "#!/bin/bash -ue",
            "echo \"Running sha1 on /data/3.json\" >&2",
            "sha1sum /data/3.json | cut -f 1 -d ' '",
            "#!/bin/bash -ue",
            "echo \"Running sha256 on /data/3.json\" >&2",
            "sha256sum /data/3.json | cut -f 1 -d ' '"
        ],
        "start_time": "2021-08-26T18:46:19Z",
        "end_time": "2021-08-26T18:46:21Z",
        "stdout": "http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stdout/e4d25c91-f950-4c4b-8616-cfafe83296c8?workdirs=96%2F9094b1f92135bc9b9e72b9ec1629cf%2Cb0%2Ff8fc3696479989ba6cc3c1914f6f6d%2Cc3%2F9c00d208bf6d0bd994693a0a6956ea",
        "stderr": "http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stderr/e4d25c91-f950-4c4b-8616-cfafe83296c8?workdirs=96%2F9094b1f92135bc9b9e72b9ec1629cf%2Cb0%2Ff8fc3696479989ba6cc3c1914f6f6d%2Cc3%2F9c00d208bf6d0bd994693a0a6956ea",
        "exit_code": 0
    },
    "task_logs": [
        {
            "name": "md5",
            "cmd": [
                "#!/bin/bash -ue",
                "echo \"Running md5 on /data/3.json\" >&2",
                "md5sum /data/3.json | cut -f 1 -d ' '"
            ],
            "start_time": "2021-08-26T18:46:19Z",
            "end_time": "2021-08-26T18:46:20Z",
            "stdout": "http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stdout/e4d25c91-f950-4c4b-8616-cfafe83296c8/96/9094b1f92135bc9b9e72b9ec1629cf",
            "stderr": "http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stderr/e4d25c91-f950-4c4b-8616-cfafe83296c8/96/9094b1f92135bc9b9e72b9ec1629cf",
            "exit_code": 0
        },
        {
            "name": "sha1",
            "cmd": [
                "#!/bin/bash -ue",
                "echo \"Running sha1 on /data/3.json\" >&2",
                "sha1sum /data/3.json | cut -f 1 -d ' '"
            ],
            "start_time": "2021-08-26T18:46:20Z",
            "end_time": "2021-08-26T18:46:20Z",
            "stdout": "http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stdout/e4d25c91-f950-4c4b-8616-cfafe83296c8/b0/f8fc3696479989ba6cc3c1914f6f6d",
            "stderr": "http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stderr/e4d25c91-f950-4c4b-8616-cfafe83296c8/b0/f8fc3696479989ba6cc3c1914f6f6d",
            "exit_code": 0
        },
        {
            "name": "sha256",
            "cmd": [
                "#!/bin/bash -ue",
                "echo \"Running sha256 on /data/3.json\" >&2",
                "sha256sum /data/3.json | cut -f 1 -d ' '"
            ],
            "start_time": "2021-08-26T18:46:21Z",
            "end_time": "2021-08-26T18:46:21Z",
            "stdout": "http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stdout/e4d25c91-f950-4c4b-8616-cfafe83296c8/c3/9c00d208bf6d0bd994693a0a6956ea",
            "stderr": "http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stderr/e4d25c91-f950-4c4b-8616-cfafe83296c8/c3/9c00d208bf6d0bd994693a0a6956ea",
            "exit_code": 0
        }
    ],
    "outputs": {}
}
```

From the request, we see the `run_id` and the original parameters we supplied to trigger the workflow run in the `request` attribute. The `state` attribute tells us that the job completed successfully.

#### stdout and stderr

If we follow the URLs provided in the `run_log.stdout` and `run_log.stderr` attributes, we can see the standard output and standard error produced during the workflow run.

**stdout request**

```
GET http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stdout/e4d25c91-f950-4c4b-8616-cfafe83296c8?workdirs=96%2F9094b1f92135bc9b9e72b9ec1629cf%2Cb0%2Ff8fc3696479989ba6cc3c1914f6f6d%2Cc3%2F9c00d208bf6d0bd994693a0a6956ea
```

**stdout response**

```
284bc700ae1456223c514fa64bee948c

0a8d6f1af30ead10b0b113c6785bc415100fd130

e43d20405adee85ac532db75a3e4c32bd96a775dd7bbf43d7df4437cd4b68dcf
```

The md5, sha1, and sha256 digest values were written to stdout during the run.

**stderr request**

```
GET http://localhost:7500/ga4gh/wes/v1/logs/nextflow/stderr/e4d25c91-f950-4c4b-8616-cfafe83296c8?workdirs=96%2F9094b1f92135bc9b9e72b9ec1629cf%2Cb0%2Ff8fc3696479989ba6cc3c1914f6f6d%2Cc3%2F9c00d208bf6d0bd994693a0a6956ea
```

**stderr response**

```
Running md5 on /data/3.json

Running sha1 on /data/3.json

Running sha256 on /data/3.json
```

The above messages were written to stderr during the run.
