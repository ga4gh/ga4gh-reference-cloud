# Milestones for GA4GH Plenary 2026

This document outlines Reference Cloud deliverables/features that we aim to achieve in advance of **GA4GH Plenary 2026**. Plenary takes place from September 28th to October 2nd 2026. Therefore, we aim to have all projected features in production by **September 14th, 2026**.

## Primary Features

* Researcher can register and login to the Reference Cloud (hosted at https://refcloud.ga4gh.org)
* Researcher will be able to browse the **datasets** that are available on the platform, including whether they have access to each dataset or not
* Researcher will be able to make a **data access request** for each dataset they are interested in. This will simply be a "request access" button that, when clicked, saves their request in the underlying database.
* After the data custodian / admin has reviewed the request, they can manually update the application status to either "Approved" or "Denied"
* If the researcher is approved, they will be able to access the data associated with the dataset via GA4GH APIs.
* Supported GA4GH APIs / Models:
  * **Passport:** Researcher will be able to view/copy their passport token via the UI. The Passport JWT will serve as the access token to all other GA4GH APIs
  * **Data Connect OR Beacon:** Will present a listing of DRS Object IDs available for the dataset, allowing the researcher to discover the DRS IDs and pass them to the DRS API
  * **DRS:** Researcher will be able to access **DRSObjects** via the DRS API, and use signed URLs to access/download the raw file data

## Demo Dataset

* For simplicity, we will add datasets from the [Registry of Open Data on AWS](https://registry.opendata.aws/) to the Reference Cloud. This can be seen as a "GA4GH-ification" of open data that is already available in the cloud. We will provide a thin layer of access control (i.e. Passports) so that researchers have a sense of how to use their Passport token in order to obtain access to data in a GA4GH context.
* We will begin with adding the [1000 Genomes Phase 3 Reanalysis](https://registry.opendata.aws/ilmn-dragen-1kgp/) dataset to the Reference Cloud, and will add more if time permits.
* **NOTE:** The raw data (i.e. BAM, FASTQ files) will not be copied to S3 buckets under GA4GH's control. We will simply be creating datasets and DRS Objects in the Reference Cloud that refer to the S3 data in its original location.

## Researcher Demo Workflow

Overall, the complete researcher demo workflow is as follows:

Register to the platform -> Request access to one or more datasets -> Acquire Passport Token -> Use Passport Token to view listing of DRS IDs -> Use DRS API to view DRS Objects -> Download raw file data via DRS Object access URLs

We aim to have this demo workflow ready by GA4GH Plenary, such that conference participants can sign up and work through the demo if they wish.

We will record a **demo video** that walks through the steps outlined above, illustrating what is possible with this first iteration of the reference cloud.

## Other Refinements

* The Reference Cloud UI will be clearly branded with GA4GH colors, images, styles, and themes
* A documentation site (hosted separately at https://docs.refcloud.ga4gh.org) will serve documentation on how to use the reference cloud. The **demo video** will be hosted here.

