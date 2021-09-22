---
sidebar_position: 1
---

# CNest workflow at EBI

For this demo, we set up the Starter Kit implementations of WES and DRS on a mini-cluster at the European Bioinformatics Institute (EBI), resembling a massively scaled-down version of the core HPC cluster. We connected WES to a Slurm engine, and ran a novel Copy Number Variation and Association workflow, _CNest_, using API Calls to WES. DRS resolved paths to the input files on an NFS node, enabling us to run the workflow with DRS URIs as input rather than hardcoded paths.

## The CNest Workflow

CNest is a novel method for copy number variation (CNV) analysis from next generation sequencing (NGS) data. For more information about CNest, see:
* [Fitzgerald, T. & Birney, E. (2021)](https://www.biorxiv.org/content/10.1101/2021.08.19.456963v1)
* [CNest on Github](https://github.com/tf2/CNest)
* [Nextflow workflow wrapping CNest on Github](https://github.com/smshuai/CNest-nf)

## Starter Kit Setup and Workflow Execution

As mentioned, the goal for this demo was to successfully run the Nextflow-based CNest workflow via a Slurm cluster backend, using API calls made to WES from a hypothetical researcher.

Given our requirements, EBI Cluster Services set up a mini-cluster of 4 nodes:
1. `slurm-main`: A main node functioning as the Slurm orchestrator, can delegate computational tasks to worker nodes
2. `slurm-node-1`: A first Slurm worker node to perform jobs submitted to it by the main node 
3. `slurm-node-2`: A second Slurm worker node, functionally identical to the first
4. `slurm-nfs`: A 30+ TB storage instance to store the input files (CRAMs and indexes) for the workflow

WES and DRS instances were also spun up on the `slurm-main` node. `slurm-main` thus acted as the "internet-facing" node for the cluster, receiving WES and DRS API calls from a remote researcher. Figure 1 displays a high-level overview of the mini-cluster architecture. 

![Architecture Diagram](/img/in-action/cnest-workflow-ebi/architecture.png)
_Figure 1: Architecture of proof-of-concept mini-cluster used to run CNest workflow via Slurm using WES and DRS._

### DRS Setup

The database backing the DRS instance was loaded with the following DRS Objects:
* A single, root bundle, representing the root of the entire project dataset
* A bundle for each individual in the dataset, containing a child blob for each file associated with the individual
* A blob-based DRS Object for each CRAM file
* A blob-based DRS Object for each CRAM index (CRAI) file

Each DRS Object representing a CRAM or CRAI file was associated with the file path to the raw data on the NFS node. This information was relayed back to the client as a `file://` based `access_method` in the `access_methods` array. 

### Workflow Submission

Workflow run submission was initially triggered by a `POST /runs` API call to WES. 

For this demo, the researcher was not expected to have knowledge of raw file paths to input CRAM and CRAI files. Rather, they were assumed to have knowledge of the DRS URIs representing the DRS Objects for these inputs. The researcher thus submitted DRS URIs as part of the `workflow_params` payload to WES.

Upon receiving the workflow run request, WES interpreted the DRS URI `workflow_params` (i.e. starting with `drs://`) as requiring resolution. WES, acting as a client, made API calls to DRS, and resolved the file paths to the raw data (on the NFS node) based on the single `access_method` returned by the `DRSObject`. WES staged the run by substituting in the resolved file paths as inputs to the Nextflow workflow.

Following this, the workflow was launched via Nextflow, using Slurm as the job engine. Figure 2 illustrates the downstream events triggered, such as input path resolution and run submission, by the initial researcher workflow request to WES.

![API Call Diagram](/img/in-action/cnest-workflow-ebi/api-calls.png)
_Figure 2: Steps to launching CNest workflow upon API call to WES, including input path resolution via DRS._

## Outcomes

_Coming soon_

## Future Directions

_Coming soon_

## Acknowledgements

The GA4GH Engineering Team would like to thank the following individuals for making this demo possible:

* Tomas Fitzgerald
* Shimin Shuai
* Mohamed Alibi
* Rafa Grimán Canto
* Tim Dyce