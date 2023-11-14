---
sidebar_position: 3
---

# Starter Kit DRS Test Dataset

The Starter Kit DRS docker image comes prebundled with a test dataset. The test dataset will be served via the DRS API if no overriding database connection is provided. In this section we will confirm that we can execute HTTP requests to a running Starter Kit DRS service and receive the expected response.

## Test HTTP Requests

Assuming you have a **Starter Kit DRS service running locally with all default properties**, you may use the following test ID to confirm the service is running as expected: `b8cd0667-2c33-4c9f-967b-161b905932c9`

Using your preferred API testing tool (e.g. Postman), issue the following HTTP request:
```
GET http://localhost:4500/ga4gh/drs/v1/objects/b8cd0667-2c33-4c9f-967b-161b905932c9
```

You should receive a response with a status of `200`, and a response body resembling the following:
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
    "self_uri": "drs://localhost:4500/b8cd0667-2c33-4c9f-967b-161b905932c9",
    "contents": [
        {
            "name": "phenopackets.mundhofir.family",
            "drs_uri": [
                "drs://localhost:4500/1af5cdcf-898c-4dbc-944e-1ac95e82c0ea"
            ],
            "id": "1af5cdcf-898c-4dbc-944e-1ac95e82c0ea"
        },
        {
            "name": "phenopackets.zhang.family",
            "drs_uri": [
                "drs://localhost:4500/355a74bd-6571-4d4a-8602-a9989936717f"
            ],
            "id": "355a74bd-6571-4d4a-8602-a9989936717f"
        },
        {
            "name": "phenopackets.cao.family",
            "drs_uri": [
                "drs://localhost:4500/a1dd4ae2-8d26-43b0-a199-342b64c7dff6"
            ],
            "id": "a1dd4ae2-8d26-43b0-a199-342b64c7dff6"
        },
        {
            "name": "phenopackets.lalani.family",
            "drs_uri": [
                "drs://localhost:4500/c69a3d6c-4a28-4b7c-b215-0782f8d62429"
            ],
            "id": "c69a3d6c-4a28-4b7c-b215-0782f8d62429"
        }
    ]
}
```

You can also hit the Service Info endpoint by issuing the following HTTP request:
```
GET http://localhost:4500/ga4gh/drs/v1/service-info
```

Again, you should receive a response with a status of `200`, and a response body containing information about the service according to the Service Info standard:
```
{
    "id": "org.ga4gh.starterkit.drs",
    "name": "GA4GH Starter Kit DRS Service",
    "description": "An open source, community-driven implementation of the GA4GH Data Repository Service (DRS) API specification.",
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

## Test Datasets

The following datasets / DRS IDs are ideal to begin experimenting with Starter Kit DRS.

### Test Dataset: Open dataset of 384 Phenopackets

[Paper](https://pubmed.ncbi.nlm.nih.gov/32755546/), [Dataset homepage](https://zenodo.org/record/3905420#.YArkBzpKhPZ)

A subset of the 384 Phenopackets has been applied to the test database. A single root `DRS bundle` (ID: `b8cd0667-2c33-4c9f-967b-161b905932c9`) contains the entire Phenopacket data subset. Intermediate bundles representing all Phenopackets for a single family have also been created.

#### DRS IDs

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



