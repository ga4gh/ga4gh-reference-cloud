# Discussion about federated analysis and learning across refclouds

## Context

This discussion started on 2026-11-16. The idea being explored: deploy **several refclouds, each simulating a separate organization** with its own data, and run analyses where each organization computes on its own data and only partial results leave it. Those partial results are combined into a final result, for example a model trained on everyone's data without the data ever being pooled.

Two federated-learning frameworks came up: [Flower](https://flower.ai) and [NVIDIA FLARE (NVFlare)](https://github.com/NVIDIA/NVFlare). One suggestion was to put one of them "behind TES" as an executor. This doc records how they would actually fit, and two ways to build the federation.

It builds on the compute design in [`0000-about-adding-compute.md`](0000-about-adding-compute.md) (WES, TES, Compute Platforms, visas, allowlists) and on stage 2 ([spec 0011](../specs/archive/0011-add-compute-nextflow-funnel-kubernetes.md)), which keeps both approaches below possible.

## Goals

1. **Show federated analysis with GA4GH standards:** data stays at each organization; DRS, Passports, WES and TES carry everything else.
2. **Show each organization governing its own participation:** its data, its compute, and the code that runs on them, independently of the others.
3. **Show how a federated-learning framework (NVFlare or Flower) can run under that governance,** rather than around it.

## Terminology

| Term | Meaning here |
|-|-|
| **Site** | One organization's refcloud: its data (DRS), its compute (Compute Platforms) and its identity provider (Kratos, Hydra) |
| **Coordinator** | Whatever drives the rounds and combines partial results. It runs outside every site |
| **Round** | One step of iterative training: every site starts from the same global model, trains on its own data, and returns an update; the coordinator combines the updates into the next global model |
| **Model update** | What a site returns in a round: new model weights, or the change in weights. Never the data itself |
| **Federated analysis** | A one-shot computation: each site computes summary results (counts, frequencies, statistics), and the coordinator combines them |
| **Federated learning** | Iterative training over many rounds |

## Federated-Learning Frameworks Aren't TES Executors

A TES executor (Funnel, TESK) runs one containerized task to completion: an image, a command, inputs, outputs and resources. Flower and NVFlare are something else: **coordination frameworks with their own network**.

* **A server,** run by a coordinating organization: runs the rounds and aggregates the updates. Flower calls it the SuperLink; NVFlare calls it the server.
* **A client at each site,** connected to the server for the whole training job: receives the global model, trains locally, returns its update. Flower calls it a SuperNode; NVFlare calls it a client, and issues each site certificates ("startup kits").
* **An admin interface** where a researcher submits a training job to the server, which sends it to the connected clients.

So these frameworks sit **above** TES, as workloads at each site, not underneath it as executors. The server never talks to WES or TES.

## Approaches

### WES-Coordinated Federation

Our own coordinator drives everything through each site's standard APIs:

```
coordinator ──WES (researcher's passport for site A)──► refcloud A: run on site A's data ──► outputs
            ──WES (researcher's passport for site B)──► refcloud B: run on site B's data ──► outputs
            ◄── downloads each run's outputs through WES, combines them, repeats for the next round
```

* One WES run **per site per round**. Each run receives the current global model, computes on its site's data, and writes its partial result or model update as an output.
* The coordinator downloads the outputs through WES's output download endpoint, combines them, and starts the next round.
* The coordinator could be a script like `scripts/refcloud-compute-demo.sh`, generalized to several sites, or a small service. An iterative loop doesn't fit Nextflow's workflow model well, so each round is its own run, rather than one workflow looping.

**Strengths:**
* only GA4GH standards: WES, TES, DRS and Passports;
* nothing at a site connects to anything outside it, so the coordinator pulls results;
* every round is a visible, auditable run at each site.

**Weaknesses:**
* each round pays a run's startup and input staging;
* we'd build the framework features ourselves (handling sites that drop out, secure aggregation, privacy filters);
* fine for federated analysis and simple training with a few rounds, less so for realistic learning.

**What it needs from the refcloud:**
* **A way to pass the global model into a run.** Today a run's inputs are only the site's own DRS objects (D17) and its `workflow_params`. Options:
  * a small model in `workflow_params`;
  * the model registered as a DRS object at each site;
  * inputs from other DRS servers (spec 0008, Future Directions).
* **Collecting outputs:** WES's output download endpoint already does this, for the researcher who submitted the run.
* **The researcher's identity at each site** (see [Identity Across Organizations](#identity-across-organizations)).

### Framework-Coordinated Federation

The framework's server drives the rounds. Each site's framework client is started through that site's WES, as a long-running, allowlisted run:

```
researcher ──(once per site)──► WES at refcloud A ──► framework client A ──┐
                                WES at refcloud B ──► framework client B ──┼──► framework server (coordinating organization)
                                                                            │    round 1: global model → clients → updates → combined
researcher ──► framework admin interface: submit the training job ─────────┘    round 2, 3, … N
                                                                                 final model at the server
```

1. The researcher, approved at each site, submits the allowlisted "framework client" workflow to each site's WES, with that site's DRS objects as inputs.
2. Each run stages its data locally and starts the client, which **connects out** to the framework server, using credentials the site provides (not the researcher).
3. The researcher submits the training job to the framework server.
4. Each round, the server sends the **same global model** to every connected client. Each trains on its local data and returns its update, and the server combines them into the next model, once enough clients have answered (a configurable minimum).
5. When training ends, the final model is at the server. The clients exit, and their WES runs complete; anything a client wrote to its outputs (e.g. local metrics) downloads through WES.

So there's **one WES run per site per training job**, with all the rounds inside it, over the framework's own connection.

**Strengths:**
* realistic federated learning: many rounds, sites dropping out, the framework's privacy and security features;
* shows a mainstream framework running under GA4GH governance.

**Weaknesses:**
* model updates travel over the framework's connection, not through WES, so each site sees one long run rather than each round;
* sites must allow outgoing connections to the framework server.

**What it needs from the refcloud:**
* **Outgoing connections from runs:** an admin-controlled allowlist of destinations per connection or platform. Stage 2 designs the compute namespace's outgoing traffic this way (spec 0011, Task 6). EKS Auto Mode can also filter by domain name.
* **Long runs:** maximum run duration is already a per-platform limit, so it just needs raising.
* **Site-provided credentials for the client:** NVFlare's startup kit, or Flower's TLS and node credentials. They must be site-managed (e.g. attached to the platform by its admin), never passed in by the researcher, matching how DRS credentials are handled (D17). The refcloud has no mechanism for this yet.
* **Keeping the allowlist meaningful:** in these frameworks, training code usually arrives from the server at runtime, which would bypass each site's allowlist. Instead:
  * **build the training code into the allowlisted client image,** and configure the client to refuse code sent by the server (NVFlare has site-side authorization settings for who may submit jobs and whether they may bring their own code);
  * or accept that each site trusts the framework's job submitters, and rely on the framework's authorization instead of the refcloud's allowlist.

  The first keeps "each organization decides what runs" intact.
* **GPUs, for realistic training:** TES 1.1 has no first-class GPU request, only backend-specific parameters, so this needs an extension and GPU capacity.

### Set Aside: Framework Client as a Site Service

A permanent framework client at each site, next to its refcloud, using the refcloud as its executor: each round, a custom framework component submits a WES run to the local refcloud. It's set aside because of identity. The framework's users aren't GA4GH Passport holders, so the client would submit runs either with a site service account, which bypasses per-researcher visas, or through a mapping between framework users and refcloud users that we'd have to build. It also needs custom framework code at every site.

## Comparison

| | WES-coordinated | Framework-coordinated |
|-|-|-|
| Who coordinates | our coordinator | the framework's server |
| WES runs per site | one per round, short | one per training job, long |
| Where results and updates travel | WES output downloads | the framework's connection |
| What each site sees | every round as a run, with its outputs | one long client run |
| Network from a site | none: the coordinator pulls | outgoing connections to the framework server |
| Best for | federated analysis; simple training | realistic federated learning |

## Each Site Governs Its Own Participation

Under either approach, a researcher can only include a site's data if that site approved them, independently of every other site:

| Control at each site | What it decides |
|-|-|
| Dataset visa | whether the researcher may read this site's data (checked when DRS inputs are staged) |
| Compute platform visa | whether the researcher may run anything on this site's compute |
| Allowlist | which exact code may run (e.g. the framework client image, with the training code built in) |
| Limits, and the outgoing-traffic allowlist | how much may run, for how long, and which coordinator a client may connect to |

* A site that doesn't approve simply isn't part of the federation; the others proceed.
* Revoking a visa applies to new runs. A running client continues until it ends, unless the site's admin cancels the run or disables the platform.
* Data never leaves a site; partial results or model updates do. Model updates can still leak information about the training data. The frameworks offer mitigations (privacy filters, secure aggregation), which a site can require through its allowlisted client.
* The coordinating organization keeps its own control: who may submit training jobs to its server.

## Identity Across Organizations

Each refcloud is its own Passport broker today (its own Kratos and Hydra):

* **Separate accounts at each site.** The researcher registers and is approved at each one, and uses that site's passport with its WES. That's realistic (each organization approves independently), but tedious for a demo.
* **A shared Passport broker that every site trusts.** One identity, while each site still issues its own visas for its own data and compute. It's closer to how GA4GH intends Passports to work across organizations, but each refcloud would have to accept another broker's tokens.

## Simulating Organizations

* **Several refclouds in one cluster:** one chart installation per organization, each in its own namespace, with its own hostname, its own Kratos and Hydra, and its own database. Stage 2 names the compute namespace per installation so they don't clash (spec 0011, Task 6).
* **Split data across sites:** e.g. 1000 Genomes divided by population, so each organization holds different samples.
* **Workloads to demonstrate:**
  * federated analysis, e.g. per-site allele frequencies combined into overall frequencies;
  * a small federated-learning task on the same data, e.g. predicting a sample's population from genotypes. It's a common teaching example, and needs no phenotype data.

## Conclusions So Far

* **Flower and NVFlare are not TES executors;** they run as workloads above TES (2026-11-16).
* **Two approaches are worth pursuing:** WES-coordinated federation as the first demonstration (fewest new pieces), and framework-coordinated federation as the follow-on (2026-11-16).
* **Stage 2 keeps both open** (spec 0011): compute namespaces are named per installation, and the compute namespace's outgoing traffic is an admin-controlled allowlist.

## Open Questions

1. Which approach first, and with which workload (federated analysis, or a small model)?
2. Which framework, if any: NVFlare or Flower? Both have the same shape here.
3. Identity: separate accounts at each site, or a shared Passport broker?
4. For WES-coordinated federation: how does the global model reach each run?
5. For framework-coordinated federation: how does a site attach credentials (the startup kit) to its platform's runs?
6. Where does the coordinator or framework server run: in its own namespace in the same cluster, or elsewhere?
7. Are GPUs needed for the demonstrations in mind?

## To Verify

* NVFlare's site-side authorization: refusing code sent by the server, and restricting who may submit jobs. Flower's equivalent.
* Running a framework client as one long TES task (via a one-process workflow), within a platform's limits.
* Outgoing-traffic allowlists in minikube (Calico, `ipBlock`): ✅ verified 2026-11-17 (spec 0011, Task 6: `ga4gh.referenceCloud.compute.outgoingAllowlist`). On EKS Auto Mode (domain-name filtering): still to verify.

## Next Step

Not scheduled: the year-end work is compute stages 2–3, the AWS platform and production ([milestones](../../docs/milestones/2026-12-18-Compute-Milestones.md)). Once those land, pick an approach and a workload (Open Questions 1–3), then write a spec.
