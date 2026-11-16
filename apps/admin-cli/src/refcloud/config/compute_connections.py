"""Compute connections (connections.yml), the file core-api also loads

The admin CLI reads only what it needs to validate compute platforms: each connection's type and its pooled limits.
core-api validates the whole file at startup.
"""

import os
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from pathlib import Path

import yaml

CONNECTIONS_FILE_ENV = "REFCLOUD_COMPUTE_CONNECTIONS_FILE"

# relative to apps/admin-cli, where the CLI runs locally (core-api's default is the same file)
DEFAULT_CONNECTIONS_FILE = "../../deploy/local/compute/connections.yml"

# platform types (D14) and the WES workflow type/version their engine runs
PLATFORM_TYPES = {
    "nextflow-funnel-docker": {"workflow_type": "NFL", "workflow_type_version": "DSL2"},
}


class ConnectionsFileError(Exception):
    """The connections file is missing a value or has an invalid one"""


@dataclass(frozen=True)
class ConnectionLimits:
    """A connection's pooled ceilings that per-user platform limits are checked against (D11)"""

    max_concurrent_runs: int
    max_task_cpu_cores: int
    max_task_memory_gb: Decimal
    max_task_disk_gb: Decimal
    max_run_duration_seconds: int
    monthly_core_hours_budget: Decimal | None


@dataclass(frozen=True)
class Connection:
    key: str
    type: str
    limits: ConnectionLimits

    @property
    def workflow_type(self):
        return PLATFORM_TYPES[self.type]["workflow_type"]

    @property
    def workflow_type_version(self):
        return PLATFORM_TYPES[self.type]["workflow_type_version"]


def connections_file_path() -> Path:
    return Path(os.environ.get(CONNECTIONS_FILE_ENV, DEFAULT_CONNECTIONS_FILE))


def load_connections() -> dict[str, Connection]:
    """Load every connection, keyed by connection key. No file means no connections (compute is off), as in core-api"""
    path = connections_file_path()
    if not path.is_file():
        return {}

    data = yaml.safe_load(path.read_text()) or {}
    connections = data.get("ga4gh", {}).get("refcloud", {}).get("compute", {}).get("connections") or {}
    return {key: _parse_connection(path, key, value or {}) for key, value in connections.items()}


def _parse_connection(path, key, value) -> Connection:
    connection_type = value.get("type")
    if connection_type not in PLATFORM_TYPES:
        raise ConnectionsFileError(
            f"{path}: connection ({key}) has unknown type ({connection_type}); "
            f"supported types: {', '.join(PLATFORM_TYPES)}"
        )

    limits = value.get("limits") or {}

    def required(name, parse):
        if limits.get(name) is None:
            raise ConnectionsFileError(f"{path}: connection ({key}) is missing limits.{name}")
        return _parse_number(path, key, name, limits[name], parse)

    budget = limits.get("monthly-core-hours-budget")
    return Connection(
        key=key,
        type=connection_type,
        limits=ConnectionLimits(
            max_concurrent_runs=required("max-concurrent-runs", int),
            max_task_cpu_cores=required("max-task-cpu-cores", int),
            max_task_memory_gb=required("max-task-memory-gb", Decimal),
            max_task_disk_gb=required("max-task-disk-gb", Decimal),
            max_run_duration_seconds=required("max-run-duration-seconds", int),
            monthly_core_hours_budget=None
            if budget is None
            else _parse_number(path, key, "monthly-core-hours-budget", budget, Decimal),
        ),
    )


def _parse_number(path, key, name, value, parse):
    try:
        return parse(str(value))
    except ValueError, InvalidOperation:
        raise ConnectionsFileError(f"{path}: connection ({key}) has an invalid limits.{name} ({value})") from None
