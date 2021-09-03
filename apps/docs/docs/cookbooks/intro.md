---
sidebar_position: 1
---

# Intro to Cookbooks

The following cookbooks provide end-to-end walkthroughs of how to set up GA4GH Starter Kit services to demonstrate federated use cases made possible by GA4GH standards. They provide a simple model of how GA4GH standards work together to facilitate controlled data access, compute, and feature-rich data sharing.

Cookbooks are designed to run on your local machine (e.g. laptop or desktop). However, the demos herein can be readily adapted to other deployment contexts (such as HPC or cloud), either by deploying the Starter Kit in those environments, or by attempting to replicate the demos with your own implementations of GA4GH standards.  

As a prerequisite, the cookbooks assume that:
* `docker` and `docker-compose` are installed and updated on your local machine
* You have access to your local machine's command line terminal
* You have an API testing tool installed on your local machine (such as Postman).

## Cookbooks

Available cookbooks are listed both in the following table, and in the sidebar. They are independent from one another, and can be explored in any order.

| Cookbook | GA4GH Standards Used |
|----------|----------------------|
| [Run `samtools view` using WES and DRS](./samtools-view-drs-wes/intro-and-setup)  | WES, DRS |