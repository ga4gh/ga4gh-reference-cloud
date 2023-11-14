---
sidebar_position: 3
---

# Starter Kit Data Connect Test Dataset

The Starter Kit Data Connect docker image comes prebundled with a test dataset. The test dataset will be served via the Data Connect API if no overriding database connection is provided. In this section we will confirm that we can execute HTTP requests to a running Starter Kit Data Connect service and receive the expected responses.

## Test HTTP Requests

### Service Info

Let's begin by making a request to the `/service-info` endpoint. Using your preferred API testing tool (e.g. Postman), issue the following HTTP request:

```
GET http://localhost:4500/service-info
```

You should receive a response with a status of `200`, and a response body resembling the following:

```
{
    "id": "org.ga4gh.starterkit.dataconnect",
    "name": "GA4GH Starter Kit Data Connect Service",
    "description": "Starter Kit implementation of the  Data Connect API specification. Gives researchers access to the data  model of given datasets/tables, and enables them to perform search  queries on the datasets using the model.",
    "contactUrl": "mailto:info@ga4gh.org",
    "documentationUrl": "https://github.com/ga4gh/ga4gh-starter-kit-data-connect",
    "createdAt": "2022-04-27T09:00:00Z",
    "updatedAt": "2022-04-27T09:00:00Z",
    "environment": "test",
    "version": "0.1.0",
    "type":
    {
        "group": "org.ga4gh",
        "artifact": "data-connect",
        "version": "1.0.0"
    },
    "organization":
    {
        "name": "Global Alliance for Genomics and Health",
        "url": "https://ga4gh.org"
    }
}

```

### List Tables

Issue the following HTTP request to list the tables in the Data Connect service:
```
GET http://localhost:4500/tables
```

You should receive a response with a status of `200`, and a response body resembling the following:
```
{
    "tables":
    [
        {
            "name": "phenopacket_v1",
            "description": "Table / directory containing JSON files for phenopackets",
            "data_model":
            {
                "$ref": "http://localhost:4500/table/phenopacket_v1/info"
            }
        },
        {
            "name": "one_thousand_genomes_sample",
            "description": "Table / directory containing JSON files for one thousand genomes sample from https://www.internationalgenome.org",
            "data_model":
            {
                "$ref": "http://localhost:4500/table/one_thousand_genomes_sample/info"
            }
        }
    ]
}
```

### Get Table Info
Issue the following HTTP request to get the table info of a specific table. You may use one of the tables from the "list tables" response: `one_thousand_genomes_sample`

```
GET http://localhost:4500/table/one_thousand_genomes_sample/info
```

You should receive a response with a status of `200`, and a response body resembling the following:
```
{
    "name": "one_thousand_genomes_sample",
    "description": "Table / directory containing JSON files for one thousand genomes sample from https://www.internationalgenome.org",
    "data_model": {
        "$id": "/table/one_thousand_genomes_sample/info",
        "$schema": "http://json-schema.org/draft-07/schema#",
        "description": "one thousand genomes sample JSON data model",
        "properties": {
            "sample_name": {
                "type": "string",
                "description": "An identifier specific for this genome sample"
            },
            "sex": {
                "type": "string",
                "enum": [
                    "male",
                    "female"
                ]
            },
            "biosample_id": {
                "type": "string",
                "description": "bio sample identifier"
            },
            "population_code": {
                "type": "string",
                "enum": [
                    "ITU",
                    "ASW",
                    "JPT",
                    "MSL",
                    "CHS",
                    "CDX",
                    "YRI",
                    "ACB",
                    "MXL",
                    "PUR",
                    "FIN",
                    "GWD",
                    "LWK",
                    "GIH",
                    "CLM",
                    "TSI",
                    "PEL",
                    "PJL",
                    "GBR",
                    "CHB",
                    "BEB",
                    "ESN",
                    "KHV",
                    "CEU",
                    "IBS",
                    "STU"
                ]
            },
            "population_name": {
                "type": "string",
                "enum": [
                    "Bengali,Bengali",
                    "African Ancestry SW",
                    "Punjabi",
                    "Dai Chinese",
                    "Gambian Mandinka",
                    "Yoruba",
                    "British",
                    "Japanese",
                    "Iberian",
                    "African Caribbean",
                    "Mende",
                    "Southern Han Chinese",
                    "Han Chinese",
                    "Luhya",
                    "Kinh,Kinh Vietnamese",
                    "Toscani",
                    "Luhya,Luhya",
                    "Kinh Vietnamese",
                    "Tamil",
                    "Gujarati",
                    "Bengali",
                    "Finnish",
                    "CEPH",
                    "Telugu",
                    "Peruvian",
                    "Esan",
                    "Colombian",
                    "Punjabi,Punjabi",
                    "Puerto Rican",
                    "Mexican Ancestry"
                ]
            },
            "superpopulation_code": {
                "type": "string",
                "enum": [
                    "AMR",
                    "EAS",
                    "EUR",
                    "SAS",
                    "AFR"
                ]
            },
            "superpopulation_name": {
                "type": "string",
                "enum": [
                    "East Asian Ancestry",
                    "European Ancestry",
                    "African Ancestry",
                    "American Ancestry",
                    "East Asia (SGDP),East Asian Ancestry",
                    "South Asian Ancestry",
                    "South Asia (SGDP),South Asian Ancestry",
                    "African Ancestry,Africa (SGDP)"
                ]
            },
            "population_elastic_id": {
                "type": "string"
            },
            "data_collections": {
                "type": "string"
            },
            "cram_drs_uri": {
                "type": "string",
                "description": "drs uri pointing to the cram file"
            },
            "crai_drs_uri": {
                "type": "string",
                "description": "drs uri pointing to the crai file"
            },
            "bundle_drs_uri": {
                "type": "string",
                "description": "drs uri pointing to the bundle file"
            }
        }
    }
}
```
### Fetch data from a Table
Issue the following HTTP request to retrieve all the data in a specific table. You may use one of the tables from the "list tables" response: `one_thousand_genomes_sample`

```
GET http://localhost:4500/table/one_thousand_genomes_sample/data
```

You should receive a response with a status of `200`, and a response body containing the `data` of the specified table. The returned response resembles the following:

```
{
    "data_model": {...},
    "data": [
        {
            "sample_name": "HG01965",
            "sex": "female",
            "biosample_id": "SAME1839145",
            "population_code": "PEL",
            "population_name": "Peruvian",
            "superpopulation_code": "AMR",
            "superpopulation_name": "American Ancestry",
            "population_elastic_id": "PEL",
            "data_collections": "1000 Genomes on GRCh38,1000 Genomes 30x on GRCh38,1000 Genomes phase 3 release",
            "cram_drs_uri": "drs://localhost:5000/HG01965.1000genomes.lowcov.downsampled.cram",
            "crai_drs_uri": "drs://localhost:5000/HG01965.1000genomes.lowcov.downsampled.crai",
            "bundle_drs_uri": "drs://localhost:5000/HG01965.1000genomes.lowcov.downsampled.bundle"
        },
        ...
    ]
}
```
### Perform a search on Tables

Next, Let's perform a search on `one_thousand_genomes_sample` table to retrieve `sample_name`, `biosample_id`, `population_code`, `sex` and `cram_drs_uri` fields using SQL for condition `population_code = "PUR" and sex = "femlae"`.

**Method and URL**

```
POST http://localhost:4500/search
```

**Headers**
```
Content-Type: application/json
```
**Body**
```
{
  "query": "select sample_name, biosample_id, population_code, sex,cram_drs_uri from one_thousand_genomes_sample where population_code=? and sex=?;",
  "parameters": [ "PUR","female" ]
}
```

In the above request, we specify a search SQL query using the following fields in the **request-body**:
* `query`: specifies the SQL query that we want to run
* `parameters`: specifies the positional parameters for the query in the `query` property


You should receive a response with a status of `200`, and a response body resembling the following:
```
{
    "data": [
        {
            "sample_name": "HG00740",
            "biosample_id": "SAME124834",
            "population_code": "PUR",
            "sex": "female",
            "cram_drs_uri": "drs://localhost:5000/HG00740.1000genomes.lowcov.downsampled.cram"
        },
        {
            "sample_name": "HG01070",
            "biosample_id": "SAME123993",
            "population_code": "PUR",
            "sex": "female",
            "cram_drs_uri": "drs://localhost:5000/HG01070.1000genomes.lowcov.downsampled.cram"
        },
        {
            "sample_name": "HG01326",
            "biosample_id": "SAME1839598",
            "population_code": "PUR",
            "sex": "female",
            "cram_drs_uri": "drs://localhost:5000/HG01326.1000genomes.lowcov.downsampled.cram"
        }
    ]
}
```

## Test Dataset:

The default SQLite dev database is preconfigured with two datasets to aid development and testing. The following datasets are included:

* A subset of 200 genome samples from the one thousand genome samples dataset: [Paper](https://www.nature.com/articles/nature15393) , [Dataset homepage](https://www.internationalgenome.org/data)
* A subset of 50 phenopackets from an open dataset of 384 Phenopackets: [Paper](https://pubmed.ncbi.nlm.nih.gov/32755546/), [Dataset homepage](https://zenodo.org/record/3905420#.YArkBzpKhPZ)