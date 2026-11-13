# Compute Milestones for the End of 2026

This document outlines the Reference Cloud compute (WES/TES) deliverables we aim to achieve before the Christmas break, building on stage 1 (local native mode, [spec 0008](../../claude/specs/active/0008-add-compute-wes-tes.md)). Prioritized on November 13th, 2026, for the six weeks to the break. We aim to have compute in production by **December 18th, 2026** (proposed), leaving time to watch it before the break.

The design source of truth is [`claude/discussions/0000-about-adding-compute.md`](../../claude/discussions/0000-about-adding-compute.md). Each item below gets its own spec.

## High-Priority Features

In priority order:

1. **Stage 2: helm-based setup.** The `nextflow-funnel-kubernetes` platform type in helm mode (minikube), with a `hostPath` volume for storage (D16):
   * Funnel in the chart, using its Kubernetes compute backend (one Kubernetes Job per task);
   * Nextflow as one Kubernetes Job per WES run, created by core-api (discussion doc, Future Directions);
   * the chart's compute connection wiring (`compute.connections` value, ConfigMap, Secrets; spec 0008, Future Directions).
2. **Stage 3: MinIO/S3 storage.** `s3` as a connection storage setting for both platform types (D15): MinIO locally, S3 on AWS. Covers the Nextflow work directory, Funnel's file staging, WES input staging (D17), and output downloads (D19).
3. **A compute platform on AWS (EKS).** Run WES/TES jobs on the Kubernetes cluster on AWS that the refcloud is deployed to. See [Running on AWS](#running-on-aws).
4. **My Runs page in the researcher UI, for monitoring.** List the user's runs and their states, follow each run's tasks, view logs, download outputs, and cancel runs. Researchers keep submitting runs through WES; the page doesn't submit runs (see below).
5. **General docs update.** Docs-site pages for each item above as it lands, plus a general pass over the existing pages, including screenshots of the researcher UI (spec 0008, Future Directions).
6. **Final deployment to production.** Compute on https://refcloud.ga4gh.org.

## Running on AWS

**Decided 2026-11-13: Funnel's Kubernetes backend on EKS.** Funnel 0.11.14 can run tasks on AWS in two ways:

* **Kubernetes backend on EKS (chosen).** Funnel creates a Kubernetes Job per task, and stages files through its S3 storage backend. Production already runs the refcloud's helm chart on a Kubernetes cluster on AWS (`deploy/helm/README.md`), so this is item 1's platform type with item 2's S3 storage. An AWS compute platform is then a new **connection** (D9), not new code (D14, D15): the cluster's namespace for task Jobs, an S3 bucket, and IAM roles for service accounts instead of keys.
* **AWS Batch backend.** Funnel submits each task as an AWS Batch job running a Funnel worker, with files in S3. It needs a Batch compute environment, a job queue, a job definition and an IAM role, and Funnel's AWS Batch guide uses DynamoDB as Funnel's database. This would be a new platform type and more AWS setup, for the same researcher-facing result. **Not pursued.**

## Scope Decisions (2026-11-13)

* **AWS means EKS.** AWS Batch isn't pursued (see above).
* **The UI monitors and cancels runs; it doesn't submit them.** Submission stays with WES (the API, the demo script and the docs' interactive demo). A submit form would need each workflow's parameter schema (e.g. nf-core's `nextflow_schema.json`).
* **The nf-ga4gh fixes don't block production.** The bundled demo works on released nf-ga4gh 1.5.0. Until the transient-error fix (D23) is released, deploying or restarting core-api fails the workflows that are running. Bump the pinned version when the fixes are released.

## Proposed Schedule

| Week | Dates | Work |
|-|-|-|
| 1 | Nov 16–20 | Close stage 1 (end-to-end check, PR, merge). Spike the stage 2–3 unknowns: Funnel's Kubernetes backend in minikube, Nextflow as a Kubernetes Job, and nf-ga4gh with an `s3://` work directory against MinIO and Funnel's S3 storage. Start the AWS conversations (see Dependencies). |
| 2–3 | Nov 23–Dec 4 | Stage 2 (item 1). Start the My Runs page (item 4). |
| 3–4 | Nov 30–Dec 11 | Stage 3 (item 2). |
| 4–5 | Dec 7–18 | AWS connection and production hardening (item 3), then production deployment (item 6). Docs (item 5) throughout. |
| 6 | Dec 21–24 | Buffer. No new deployments. |

## Dependencies and Risks

* **Unverified stack pieces.** Funnel's Kubernetes backend (discussion doc, To Verify item 3), Nextflow as a Kubernetes Job, and nf-ga4gh with S3 work directories haven't been tried yet. The week 1 spike covers them, so surprises surface early.
* **AWS access and approvals**, outside the code:
  * who pays for compute, and a budget with alarms;
  * IAM roles, an S3 bucket for work and outputs, and capacity on the cluster (a separate node group for task Jobs).
* **Production security.** Researchers run allowlisted workflows only, behind visa approval and per-user limits. In production, task Jobs also need:
  * their own namespace, with no service account token;
  * network policies and a resource quota on that namespace.

  The admin CLI's platform disable is the kill switch.
* **Holiday timing.** Deploy by December 18th so there's time to watch costs and usage before the break. Consider keeping platform access approval-only over the break.
