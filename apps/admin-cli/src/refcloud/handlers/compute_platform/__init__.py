"""Shared helpers for the compute-platform commands (D5, D9, D11)"""

import re
from decimal import Decimal, InvalidOperation
from typing import NoReturn

import click

from refcloud.config.compute_connections import ConnectionsFileError, connections_file_path, load_connections
from refcloud.config.database_engine import DatabaseEngine

# used in url paths (/ga4gh/wes/{platformId}/v1) and service-info ids; matches the database check constraint
PLATFORM_ID = re.compile(r"^[a-z0-9][a-z0-9-]{0,62}$")

# per-user limits (D11): (cli option, compute_platform column, type, connection ceiling attribute). every limit is
# optional: unset (NULL) means no per-user cap, so only the connection's pooled ceiling applies
LIMITS = [
    ("max-concurrent-runs", "user_max_concurrent_runs", int, "max_concurrent_runs"),
    ("max-task-cpu-cores", "user_max_task_cpu_cores", int, "max_task_cpu_cores"),
    ("max-task-memory-gb", "user_max_task_memory_gb", Decimal, "max_task_memory_gb"),
    ("max-task-disk-gb", "user_max_task_disk_gb", Decimal, "max_task_disk_gb"),
    ("max-run-duration-seconds", "user_max_run_duration_seconds", int, "max_run_duration_seconds"),
    ("monthly-core-hours", "user_monthly_core_hours", Decimal, "monthly_core_hours_budget"),
]


def fail(message) -> NoReturn:
    click.echo(f"error: {str(message).rstrip('.')}. exiting")
    raise SystemExit(1)


def visa_id(platform_id):
    return f"ga4gh_visa:compute_platform:{platform_id}"


def visa_values(name):
    return {"name": f"Visa: {name}", "description": f"Visa for compute platform: {name}"}


def load_connections_or_fail():
    try:
        return load_connections()
    except ConnectionsFileError as error:
        fail(str(error))


def get_connection_or_fail(connection_key):
    connections = load_connections_or_fail()
    connection = connections.get(connection_key)
    if connection is None:
        known = ", ".join(sorted(connections)) or "none"
        fail(f"connection ({connection_key}) is not in {connections_file_path()} (known connections: {known})")
    return connection


def get_platform_or_fail(platform_id):
    platform = DatabaseEngine().get_compute_platform(platform_id)
    if platform is None:
        fail(f"compute platform with id ({platform_id}) does not exist in the database")
    return platform


def limit_options(function):
    """Add one --{limit} option per per-user limit. Values are numbers, or 'none' to clear a limit (update only)"""
    for option, _, _, _ in reversed(LIMITS):
        function = click.option(f"--{option}", help=f"Per-user {option.replace('-', ' ')} ('none' clears it)")(function)
    return function


def parse_limits(raw_limits, allow_none):
    """Parse --{limit} values into {column: value}. Only limits that were given are returned"""
    values = {}
    for option, column, parse, _ in LIMITS:
        raw = raw_limits.get(option.replace("-", "_"))
        if raw is None:
            continue
        if raw.lower() == "none":
            if not allow_none:
                fail(f"--{option} none only applies to update (an unset limit is already 'no per-user cap')")
            values[column] = None
            continue
        try:
            value = parse(raw)
        except ValueError, InvalidOperation:
            fail(f"--{option} must be a {'whole number' if parse is int else 'number'} (got {raw})")
        if value <= 0:
            fail(f"--{option} must be greater than zero (got {raw})")
        values[column] = value
    return values


def check_limits_within_ceilings(connection, limits):
    """A per-user limit can never exceed its connection's pooled ceiling (D11)"""
    for option, column, _, ceiling_attribute in LIMITS:
        value = limits.get(column)
        ceiling = getattr(connection.limits, ceiling_attribute)
        if value is not None and ceiling is not None and value > ceiling:
            fail(
                f"per-user {option} ({value}) exceeds connection ({connection.key}) ceiling ({ceiling}); "
                f"see {connections_file_path()}"
            )


def format_limits(platform):
    parts = [
        f"{option}={getattr(platform, column)}"
        for option, column, _, _ in LIMITS
        if getattr(platform, column) is not None
    ]
    return ", ".join(parts) or "none (connection ceilings only)"
