# Compute Milestones for the End of 2026

This document outlines the Reference Cloud compute (WES/TES) deliverables we aim to achieve before the Christmas break, building on stage 1 (local native mode, [spec 0008](../../claude/specs/archive/0008-add-compute-wes-tes.md)). Prioritized on November 13th, 2026, for the six weeks to the break, and reordered on November 18th (see [Schedule](#schedule)). We aim to have compute in production by **December 18th, 2026**, the last working day before the break. The production rollout ([spec 0010](../../claude/specs/active/0010-production-rollout.md)) aims a few days earlier, so there's time to watch it.

The design source of truth is [`claude/discussions/0000-about-adding-compute.md`](../../claude/discussions/0000-about-adding-compute.md). Each item below gets its own spec.

## High-Priority Features

In priority order:

1. **Stage 2: helm-based setup.** ✅ **Done 2026-11-17** ([spec 0011](../../claude/specs/archive/0011-add-compute-nextflow-funnel-kubernetes.md)). The `nextflow-funnel-kubernetes` platform type in helm mode (minikube), with a `hostPath` volume for storage (D16):
   * Funnel in the chart, using its Kubernetes compute backend (one Kubernetes Job per task);
   * Nextflow as one Kubernetes Job per WES run, created by core-api (discussion doc, Future Directions);
   * the chart's compute connection wiring (`compute.connections` value, ConfigMap, Secrets; spec 0008, Future Directions).
2. **Stage 3: S3 storage.** 🔄 **Built 2026-11-18, end-to-end test pending** ([spec 0012](../../claude/specs/archive/0012-add-compute-s3-storage.md)). `s3` as a connection storage setting (D15) for the Kubernetes platform type: RustFS locally (MinIO's community edition was archived), S3 on AWS. The Docker type keeps the shared filesystem. Covers the Nextflow work directory, Funnel's file staging (on Funnel 0.12.3, with a claim per task and no cluster-wide permissions), WES input staging (D17), output downloads (D19), and credentials for each run limited to its own files. Helm mode is now S3 only, with no `hostPath`, so its compute namespace enforces the restricted Pod Security level.
3. **A compute platform on AWS (EKS).** Run WES/TES jobs on the Kubernetes cluster on AWS that the refcloud is deployed to, fully working and tested there before production. See [Running on AWS](#running-on-aws).
   * **One branch covers it all:**
     * the AWS prerequisites, in Terraform: the `refcloud-nextflow` ECR repository, an S3 bucket, IAM roles through EKS Pod Identity, a node pool for task Jobs, and a budget with alarms;
     * the platform's connection;
     * what stage 3 left to verify on EKS (see [Dependencies and Risks](#dependencies-and-risks)).
   * **Its first question:** where to test it on EKS without disturbing production. Enforcing network policies affects every app in a cluster ([spec 0010](../../claude/specs/active/0010-production-rollout.md)).
4. **My Runs page in the researcher UI, for monitoring.** List the user's runs and their states, follow each run's tasks, view logs, download outputs, and cancel runs. Researchers keep submitting runs through WES; the page doesn't submit runs (see below).
5. **Testing framework and CI.**
   * **First:** run each app's existing tests in CI on every pull request, so a fresh checkout's failures show up at once. Spec 0012 found two test fixtures that `.gitignore` had kept out of the repository, so every fresh checkout would have failed those tests.
   * **Then, as time allows:** the Spring slice and repository tests deferred in [spec 0008](../../claude/specs/archive/0008-add-compute-wes-tes.md) (Future Directions), and integration tests.
   * If the AWS platform overruns, this item is trimmed first.
6. **General docs update.** Each branch keeps updating the docs site for what it adds. This item is the general pass over the existing pages after the My Runs page lands, including screenshots of the researcher UI (spec 0008, Future Directions).
7. **Final deployment to production.** Compute on https://refcloud.ga4gh.org, in one production update with everything else merged since the current release: [spec 0010](../../claude/specs/active/0010-production-rollout.md).

## Running on AWS

**Decided 2026-11-13: Funnel's Kubernetes backend on EKS.** Funnel 0.11.14 can run tasks on AWS in two ways:

* **Kubernetes backend on EKS (chosen).** Funnel creates a Kubernetes Job per task, and stages files through its S3 storage backend. Production already runs the refcloud's helm chart on a Kubernetes cluster on AWS (`deploy/helm/README.md`), so this is item 1's platform type with item 2's S3 storage. An AWS compute platform is then a new **connection** (D9), not new code (D14, D15): the cluster's namespace for task Jobs, an S3 bucket, and IAM roles for service accounts instead of keys.
* **AWS Batch backend.** Funnel submits each task as an AWS Batch job running a Funnel worker, with files in S3. It needs a Batch compute environment, a job queue, a job definition and an IAM role, and Funnel's AWS Batch guide uses DynamoDB as Funnel's database. This would be a new platform type and more AWS setup, for the same researcher-facing result. **Not pursued.**

## Scope Decisions (2026-11-13)

* **AWS means EKS.** AWS Batch isn't pursued (see above).
* **The UI monitors and cancels runs; it doesn't submit them.** Submission stays with WES (the API, the demo script and the docs' interactive demo). A submit form would need each workflow's parameter schema (e.g. nf-core's `nextflow_schema.json`).
* **The nf-ga4gh fixes don't block production.** The bundled demo works on released nf-ga4gh 1.5.0. Until the transient-error fix (D23) is released, deploying or restarting core-api fails the workflows that are running. Bump the pinned version when the fixes are released.

## Schedule

**Revised 2026-11-18:** stage 3 was built in a day (November 18th). Its production notes are in [spec 0010](../../claude/specs/active/0010-production-rollout.md).

The AWS prerequisites need no outside approvals, so they go into the AWS platform's branch rather than a parallel track. The remaining items each get a branch, one after another, in the order above:
1. the AWS platform (item 3);
2. the My Runs page (item 4);
3. the testing framework and CI (item 5);
4. the docs refresh (item 6);
5. production (item 7).

The AWS platform goes before the My Runs page for three reasons:
* it's the riskiest item left;
* production's compute depends on it;
* runs can be monitored through WES (the API, the demo script) until the page lands.

**Revised 2026-11-17.** Stage 2 took two days (its spike on November 16th, the rest on the 17th) instead of the two weeks planned: the spike settled Funnel's Kubernetes backend early, stage 1's structure carried over, and stage 2 stayed local. That puts the plan about two weeks ahead. The time goes:

1. to stage 3 straight away, since the AWS platform builds on it;
2. to the AWS side of item 3 early, in parallel: with code moving fast, it's now the longest pole, since access, Terraform and approvals have lead times of their own;
3. the rest stays buffer until stage 3 lands. Then, if there's room: the My Runs page (item 4), database locks so core-api can run several replicas (spec 0011, Future Directions), the CI testing framework, or the WES-coordinated federation demo ([`claude/discussions/0001-about-federated-learning.md`](../../claude/discussions/0001-about-federated-learning.md)).

| Week | Dates | Work |
|-|-|-|
| 1 | Nov 16–20 | ✅ Stage 1 closed. ✅ Deployment hardened ([issue 58](https://github.com/ga4gh/ga4gh-reference-cloud/issues/58)). ✅ Stage 2 (item 1), done November 17th. 🔄 Stage 3 (item 2), built November 18th (its spike settled RustFS, Funnel 0.12.3 and per-run credentials); end-to-end test and PR next. |
| 2 | Nov 23–27 | The AWS compute platform (item 3): its prerequisites in Terraform (the `refcloud-nextflow` ECR repository, an S3 bucket, IAM roles, a node pool for task Jobs, a budget with alarms), then the platform on EKS, on stage 2's chart and stage 3's S3 storage. |
| 3 | Nov 30–Dec 4 | Finish item 3. The My Runs page (item 4), then start the testing framework and CI (item 5). |
| 4 | Dec 7–11 | Finish item 5, trimmed to fit. The docs refresh (item 6). Rehearse the release in minikube. **December 11th: go/no-go on what ships** ([spec 0010](../../claude/specs/active/0010-production-rollout.md), Task 1). |
| 5 | Dec 14–18 | Production deployment (item 7) on December 14th–16th, then watch it. |
| 6 | Dec 21–24 | Buffer. No new deployments. |

## Dependencies and Risks

* **Stack pieces:** Funnel's Kubernetes backend and Nextflow as a Kubernetes Job are verified (spec 0011), and so are nf-ga4gh with an `s3://` work directory and Funnel's S3 staging (spec 0012), without new patches. Still to verify on EKS (the AWS platform spec): per-task workspace claims on EBS, EKS Pod Identity for core-api and Funnel's workers, and DNS plus a route to S3 for the Nextflow Jobs.
* **Young releases:** Funnel 0.12.3 (November 9th) changed a lot since 0.11.14, and RustFS (local only) releases weekly. Both are pinned by digest; watch for fixes before production.
* **Run length on AWS:** each run's storage credentials last at most an hour there (issued from core-api's own role session), so production runs are capped at about 45 minutes until credentials can be refreshed (spec 0012, Future Directions).
* **AWS setup**, outside the code (no outside approvals needed; it's part of item 3's branch):
  * who pays for compute, and a budget with alarms;
  * IAM roles, an S3 bucket for work and outputs, and capacity on the cluster (a separate node group for task Jobs).
* **Production security.** Researchers run allowlisted workflows only, behind visa approval and per-user limits. In production, task Jobs also need:
  * their own namespace, with no service account token;
  * network policies and a resource quota on that namespace.

  Stage 2 built these for minikube (spec 0011, Tasks 3–6: a per-release compute namespace that denies all traffic by default, no DNS for workflow pods, an outbound allowlist, a quota and a LimitRange), so the AWS platform reuses them. Stage 3 removed Funnel's cluster-wide permission on PersistentVolumes (Funnel 0.12.3's per-task claims) and made the namespace enforce the restricted Pod Security level. The admin CLI's platform disable is the kill switch.
* **Holiday timing.** Deploy by December 18th so there's time to watch costs and usage before the break. Consider keeping platform access approval-only over the break.
