# The image the helm chart's setup Jobs run (spec 0013, Task 2): refcloud-setup, and the clients it uses. psql for
# Postgres, curl for the Kubernetes API (Secrets) and Hydra's admin API, jq for their JSON, and bash. Built with the
# lockstep tag by scripts/refcloud-run-helm.sh and CI (.github/workflows/push-docker-image-setup.yml).
FROM alpine:3.24.4

# psql 18 also works with older servers (RDS, local Postgres); refcloud-setup needs 15 or later (\getenv)
RUN apk add --no-cache bash curl jq postgresql18-client

COPY --chmod=755 refcloud-setup /usr/local/bin/refcloud-setup

# a numeric user, so the chart's runAsNonRoot can check it. It writes nothing: the chart mounts /tmp
USER 10001:10001

ENTRYPOINT ["refcloud-setup"]
