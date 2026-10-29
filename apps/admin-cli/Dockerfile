FROM python:3.14-slim AS builder

COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /bin/

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

WORKDIR /app

COPY --from=builder /app/.venv /app/.venv

COPY src src

ENV PATH="/app/.venv/bin:$PATH"

ENTRYPOINT ["refcloud"]
