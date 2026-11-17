# Compute Milestones for the End of 2026

This document outlines the Reference Cloud compute (WES/TES) deliverables we aim to achieve before the Christmas break, building on stage 1 (local native mode, [spec 0008](../../claude/specs/archive/0008-add-compute-wes-tes.md)). Prioritized on November 13th, 2026, for the six weeks to the break. We aim to have compute in production by **December 18th, 2026**, the last working day before the break. The production rollout ([spec 0010](../../claude/specs/active/0010-production-rollout.md)) aims a few days earlier, so there's time to watch it.

The design source of truth is [`claude/discussions/0000-about-adding-compute.md`](../../claude/discussions/0000-about-adding-compute.md). Each item below gets its own spec.

## High-Priority Features

In priority order:

1. **Stage 2: helm-based setup.** ✅ **Done 2026-11-17** ([spec 0011](../../claude/specs/archive/0011-add-compute-nextflow-funnel-kubernetes.md)). The `nextflow-funnel-kubernetes` platform type in helm mode (minikube), with a `hostPath` volume for storage (D16):
   * Funnel in the chart, using its Kubernetes compute backend (one Kubernetes Job per task);
   * Nextflow as one Kubernetes Job per WES run, created by core-api (discussion doc, Future Directions);
   * the chart's compute connection wiring (`compute.connections` value, ConfigMap, Secrets; spec 0008, Future Directions).
2. **Stage 3: MinIO/S3 storage.** `s3` as a connection storage setting for both platform types (D15): MinIO locally, S3 on AWS. Covers the Nextflow work directory, Funnel's file staging, WES input staging (D17), and output downloads (D19).
3. **A compute platform on AWS (EKS).** Run WES/TES jobs on the Kubernetes cluster on AWS that the refcloud is deployed to. See [Running on AWS](#running-on-aws).
4. **My Runs page in the researcher UI, for monitoring.** List the user's runs and their states, follow each run's tasks, view logs, download outputs, and cancel runs. Researchers keep submitting runs through WES; the page doesn't submit runs (see below).
5. **General docs update.** Docs-site pages for each item above as it lands, plus a general pass over the existing pages, including screenshots of the researcher UI (spec 0008, Future Directions).
6. **Final deployment to production.** Compute on https://refcloud.ga4gh.org, in one production update with everything else merged since the current release: [spec 0010](../../claude/specs/active/0010-production-rollout.md).

## Running on AWS

**Decided 2026-11-13: Funnel's Kubernetes backend on EKS.** Funnel 0.11.14 can run tasks on AWS in two ways:

* **Kubernetes backend on EKS (chosen).** Funnel creates a Kubernetes Job per task, and stages files through its S3 storage backend. Production already runs the refcloud's helm chart on a Kubernetes cluster on AWS (`deploy/helm/README.md`), so this is item 1's platform type with item 2's S3 storage. An AWS compute platform is then a new **connection** (D9), not new code (D14, D15): the cluster's namespace for task Jobs, an S3 bucket, and IAM roles for service accounts instead of keys.
* **AWS Batch backend.** Funnel submits each task as an AWS Batch job running a Funnel worker, with files in S3. It needs a Batch compute environment, a job queue, a job definition and an IAM role, and Funnel's AWS Batch guide uses DynamoDB as Funnel's database. This would be a new platform type and more AWS setup, for the same researcher-facing result. **Not pursued.**

## Scope Decisions (2026-11-13)

* **AWS means EKS.** AWS Batch isn't pursued (see above).
* **The UI monitors and cancels runs; it doesn't submit them.** Submission stays with WES (the API, the demo script and the docs' interactive demo). A submit form would need each workflow's parameter schema (e.g. nf-core's `nextflow_schema.json`).
* **The nf-ga4gh fixes don't block production.** The bundled demo works on released nf-ga4gh 1.5.0. Until the transient-error fix (D23) is released, deploying or restarting core-api fails the workflows that are running. Bump the pinned version when the fixes are released.

## Schedule

**Revised 2026-11-17.** Stage 2 took two days (its spike on November 16th, the rest on the 17th) instead of the two weeks planned: the spike settled Funnel's Kubernetes backend early, stage 1's structure carried over, and stage 2 stayed local. That puts the plan about two weeks ahead. The time goes:

1. to stage 3 straight away, since the AWS platform builds on it;
2. to the AWS side of item 3 early, in parallel: with code moving fast, it's now the longest pole, since access, Terraform and approvals have lead times of their own;
3. the rest stays buffer until stage 3 lands. Then, if there's room: the My Runs page (item 4), database locks so core-api can run several replicas (spec 0011, Future Directions), the CI testing framework, or the WES-coordinated federation demo ([`claude/discussions/0001-about-federated-learning.md`](../../claude/discussions/0001-about-federated-learning.md)).

| Week | Dates | Work |
|-|-|-|
| 1 | Nov 16–20 | ✅ Stage 1 closed. ✅ Deployment hardened ([issue 58](https://github.com/ga4gh/ga4gh-reference-cloud/issues/58)). ✅ Stage 2 (item 1), done November 17th. Then: stage 2's PR review and merge; start stage 3 (item 2) with its spike: nf-ga4gh with an `s3://` work directory against MinIO, and Funnel's S3 storage. Start the AWS conversations (see Dependencies). |
| 2 | Nov 23–27 | Stage 3 (item 2). AWS prerequisites in Terraform: the `refcloud-nextflow` ECR repository, an S3 bucket, an IAM role for the compute pods, a node pool for task Jobs, a budget with alarms. |
| 3 | Nov 30–Dec 4 | The AWS compute platform (item 3), on stage 2's chart and stage 3's S3 storage. The My Runs page (item 4) if there's room. |
| 4 | Dec 7–11 | Finish item 3, rehearse the release in minikube. Docs (item 5) throughout. **December 11th: go/no-go on what ships** ([spec 0010](../../claude/specs/active/0010-production-rollout.md), Task 1). |
| 5 | Dec 14–18 | Production deployment (item 6) on December 14th–16th, then watch it. |
| 6 | Dec 21–24 | Buffer. No new deployments. |

## Dependencies and Risks

* **Unverified stack pieces.** Funnel's Kubernetes backend and Nextflow as a Kubernetes Job are verified (spec 0011). nf-ga4gh with S3 work directories hasn't been tried yet: stage 3's spike covers it first, so surprises surface early. Stage 1 needed two nf-ga4gh patches, so S3 may need more.
* **AWS access and approvals**, outside the code:
  * who pays for compute, and a budget with alarms;
  * IAM roles, an S3 bucket for work and outputs, and capacity on the cluster (a separate node group for task Jobs).
* **Production security.** Researchers run allowlisted workflows only, behind visa approval and per-user limits. In production, task Jobs also need:
  * their own namespace, with no service account token;
  * network policies and a resource quota on that namespace.

  Stage 2 built these for minikube (spec 0011, Tasks 3–6: a per-release compute namespace that denies all traffic by default, no DNS for workflow pods, an outbound allowlist, a quota and a LimitRange), so the AWS platform reuses them. One stage 2 permission needs narrowing before production: Funnel's cluster-wide permission on PersistentVolumes (spec 0011, Future Directions). The admin CLI's platform disable is the kill switch.
* **Holiday timing.** Deploy by December 18th so there's time to watch costs and usage before the break. Consider keeping platform access approval-only over the break.
