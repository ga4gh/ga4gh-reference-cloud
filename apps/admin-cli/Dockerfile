# built from the repo root, so the image can include compute/workflows/: e.g.
#   docker build -f apps/admin-cli/Dockerfile .
# /.dockerignore lists what the build can see
FROM python:3.14-slim AS builder

COPY --from=ghcr.io/astral-sh/uv:0.11.24@sha256:99ea34acedc870ba4ad11a1f540a1c04267c9f30aadc465a94406f52dfda2c36 /uv /uvx /bin/

ENV UV_COMPILE_BYTECODE=1
ENV UV_NO_DEV=1
ENV UV_LINK_MODE=copy

WORKDIR /app

COPY apps/admin-cli/pyproject.toml apps/admin-cli/uv.lock apps/admin-cli/README.md ./

RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-install-project

COPY apps/admin-cli/src src

RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen

FROM python:3.14-slim AS runner

# git: `compute-platform allowlist add` resolves tags to commit shas with `git ls-remote`
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

# run as a non-root system user (python images default to root)
RUN useradd --system --no-log-init --uid 10001 refcloud

WORKDIR /app

COPY --from=builder /app/.venv /app/.venv

COPY apps/admin-cli/src src

# the bundled workflows, so `compute-platform allowlist add --bundled` can check the name, as core-api's image does
COPY compute/workflows /app/compute/workflows
ENV REFCLOUD_BUNDLED_WORKFLOWS_BASE_DIR=/app

ENV PATH="/app/.venv/bin:$PATH"

USER 10001

ENTRYPOINT ["refcloud"]
