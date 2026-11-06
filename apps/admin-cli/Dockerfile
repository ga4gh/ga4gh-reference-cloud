FROM python:3.14-slim AS builder

COPY --from=ghcr.io/astral-sh/uv:0.11.24@sha256:99ea34acedc870ba4ad11a1f540a1c04267c9f30aadc465a94406f52dfda2c36 /uv /uvx /bin/

ENV UV_COMPILE_BYTECODE=1
ENV UV_NO_DEV=1
ENV UV_LINK_MODE=copy

WORKDIR /app

COPY pyproject.toml uv.lock README.md README.md ./

RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-install-project

COPY src src

RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen

FROM python:3.14-slim AS runner

# run as a non-root system user (python images default to root)
RUN useradd --system --no-log-init --uid 10001 refcloud

WORKDIR /app

COPY --from=builder /app/.venv /app/.venv

COPY src src

ENV PATH="/app/.venv/bin:$PATH"

USER 10001

ENTRYPOINT ["refcloud"]
