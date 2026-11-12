# Funnel (TES executor) for native-mode compute, built by docker-compose.yml. LOCAL DEVELOPMENT ONLY.
# Funnel's local compute backend runs each task with `docker run` through the host's Docker socket (mounted in
# docker-compose.yml), but the official image has no docker CLI, so this adds it (claude/specs/active/0008, Tasks 1, 10).
# It runs as root: using the host's Docker socket needs it (and is root-equivalent anyway); see deploy/local/.trivyignore
FROM quay.io/ohsu-comp-bio/funnel:0.11.14

RUN apk add --no-cache docker-cli
