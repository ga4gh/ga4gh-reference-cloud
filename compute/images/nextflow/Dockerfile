# Nextflow for compute's Kubernetes platform type (nextflow-funnel-kubernetes, spec 0011): core-api runs one Job per
# run from this image. Nextflow's self-contained build and the nf-ga4gh plugin are baked in, so runs need no internet:
# the compute namespace allows no outgoing traffic. Built with the lockstep tag by scripts/refcloud-run-helm.sh and CI
# (.github/workflows/push-docker-image-nextflow.yml).
#
# The versions must match the connection's pins (engine nextflow-version and nf-ga4gh-version): refcloud-nextflow
# refuses to run otherwise. Keep them in sync with the helm chart's local connection
# (deploy/helm/charts/app/templates/common/_compute.tpl) and deploy/local/compute/connections.yml.
#
# For runs on S3 storage (spec 0012): nf-amazon, Nextflow's S3 plugin, at the version this Nextflow pins itself (the
# build fails if they differ, so bump it with NXF_VER), and curl, which refcloud-run-s3 uses to fetch the run's files
# and upload its logs and exit code.
ARG NXF_VER=26.10.5
ARG NF_GA4GH_VER=1.5.0
ARG NF_AMAZON_VER=3.12.1

FROM eclipse-temurin:21.0.13_9-jre-noble AS builder

ARG NXF_VER
ARG NF_GA4GH_VER
ARG NF_AMAZON_VER

ENV NXF_HOME=/opt/nextflow

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

# the "dist" build bundles Nextflow itself, so nothing is downloaded at run time (it ignores NXF_VER, which is why
# refcloud-nextflow checks the version instead)
RUN curl -fsSL -o /usr/local/bin/nextflow \
        "https://github.com/nextflow-io/nextflow/releases/download/v${NXF_VER}/nextflow-${NXF_VER}-dist" \
    && chmod 755 /usr/local/bin/nextflow \
    && { unzip -p /usr/local/bin/nextflow META-INF/plugins-info.txt > /tmp/plugins-info.txt || true; } \
    && { grep -qx "nf-amazon@${NF_AMAZON_VER}" /tmp/plugins-info.txt \
         || { echo "Nextflow ${NXF_VER} doesn't pin nf-amazon@${NF_AMAZON_VER}: set NF_AMAZON_VER to its pin" >&2; exit 1; }; } \
    && nextflow plugin install "nf-ga4gh@${NF_GA4GH_VER}" \
    && nextflow plugin install "nf-amazon@${NF_AMAZON_VER}" \
    && chmod -R a+rX /opt/nextflow

FROM eclipse-temurin:21.0.13_9-jre-noble

ARG NXF_VER
ARG NF_GA4GH_VER
ARG NF_AMAZON_VER

# procps: ps, which Nextflow uses to inspect processes. curl: refcloud-run-s3's requests to the run's storage
RUN apt-get update \
    && apt-get install -y --no-install-recommends procps curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /usr/local/bin/nextflow /usr/local/bin/nextflow
COPY --from=builder /opt/nextflow /opt/nextflow
COPY --chmod=755 refcloud-nextflow /usr/local/bin/refcloud-nextflow
COPY --chmod=755 refcloud-run-s3 /usr/local/bin/refcloud-run-s3

# what refcloud-nextflow checks the run's pins against. the Job sets the rest of Nextflow's environment (NXF_HOME on
# a writable emptyDir, heap size), see NextflowKubernetesEngine in core-api
ENV REFCLOUD_IMAGE_NEXTFLOW_VERSION=${NXF_VER} \
    REFCLOUD_IMAGE_NF_GA4GH_VERSION=${NF_GA4GH_VER} \
    REFCLOUD_IMAGE_NF_AMAZON_VERSION=${NF_AMAZON_VER} \
    NXF_PLUGINS_DIR=/opt/nextflow/plugins \
    NXF_OFFLINE=true

# a fixed numeric id (Kubernetes verifies runAsNonRoot by it): the compute storage's owner, like Funnel's pods
USER 10001

CMD ["refcloud-nextflow", "-version"]
