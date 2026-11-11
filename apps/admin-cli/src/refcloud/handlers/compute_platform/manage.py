"""compute-platform init, list, update, enable, disable and delete"""

import click
from sqlalchemy.exc import IntegrityError

from refcloud.config.compute_connections import connections_file_path
from refcloud.config.database_engine import DatabaseEngine
from refcloud.handlers.compute_platform import (
    LIMITS,
    PLATFORM_ID,
    check_limits_within_ceilings,
    fail,
    format_limits,
    get_connection_or_fail,
    get_platform_or_fail,
    load_connections_or_fail,
    parse_limits,
    visa_id,
    visa_values,
)
from refcloud.handlers.visa import echo_table


def compute_platform_init(platform_id, name, description, connection_key, raw_limits):
    if not PLATFORM_ID.match(platform_id):
        fail(f"platform id ({platform_id}) must be lowercase letters, digits and hyphens (e.g. nextflow-demo)")

    database_engine = DatabaseEngine()
    if database_engine.get_compute_platform(platform_id) is not None:
        fail(f"compute platform with id ({platform_id}) already exists in the database")

    if database_engine.get_compute_platform_by_name(name) is not None:
        fail(f"a compute platform named ({name}) already exists; names must be unique")

    connection = get_connection_or_fail(connection_key)
    limits = parse_limits(raw_limits, allow_none=False)
    check_limits_within_ceilings(connection, limits)

    # created disabled: add its allowlist first, then enable it, so researchers never see a half-configured platform
    platform = {
        "id": platform_id,
        "name": name,
        "description": description,
        "connection_key": connection_key,
        "enabled": False,
        **limits,
    }
    visa = {"id": visa_id(platform_id), "compute_platform_id": platform_id, **visa_values(name)}
    try:
        database_engine.insert_compute_platform_with_visa(platform, visa)
    except IntegrityError as error:
        fail(f"could not create compute platform ({platform_id}): {error.orig}")

    click.echo(f"done: created compute platform ({platform_id}) with visa ({visa['id']}). it is disabled:")
    click.echo(f"  add workflows:  refcloud compute-platform allowlist add -p {platform_id} ...")
    click.echo(f"  then enable it: refcloud compute-platform enable -p {platform_id}")


def compute_platform_list():
    platforms = DatabaseEngine().list_compute_platforms()
    if not platforms:
        click.echo("no compute platforms found in the database")
        return

    connections = load_connections_or_fail()
    rows = [
        [
            platform.id,
            platform.name,
            platform.connection_key
            if platform.connection_key in connections
            else f"{platform.connection_key} (MISSING)",
            "yes" if platform.enabled else "no",
            platform.workflow_count,
            format_limits(platform),
            platform.visa_id,
        ]
        for platform in platforms
    ]
    echo_table(["PLATFORM ID", "NAME", "CONNECTION", "ENABLED", "WORKFLOWS", "PER-USER LIMITS", "VISA ID"], rows)
    if any(platform.connection_key not in connections for platform in platforms):
        click.echo(f"\nMISSING: the connection is not in {connections_file_path()}; core-api reports it unhealthy")


def compute_platform_update(platform_id, name, description, connection_key, raw_limits):
    platform = get_platform_or_fail(platform_id)
    changes = parse_limits(raw_limits, allow_none=True)
    if name is not None and name != platform.name:
        other = DatabaseEngine().get_compute_platform_by_name(name)
        if other is not None:
            fail(f"a compute platform named ({name}) already exists; names must be unique")
        changes["name"] = name
    if description is not None:
        changes["description"] = description
    if connection_key is not None:
        changes["connection_key"] = connection_key
    if not changes:
        fail("nothing to update; pass at least one option (see --help)")

    # check the limits the platform will have after the update against the connection it will use
    connection = get_connection_or_fail(changes.get("connection_key", platform.connection_key))
    resulting_limits = {column: changes.get(column, getattr(platform, column)) for _, column, _, _ in LIMITS}
    check_limits_within_ceilings(connection, resulting_limits)

    try:
        DatabaseEngine().update_compute_platform(
            platform_id, changes, visa_values(changes["name"]) if "name" in changes else None
        )
    except IntegrityError as error:
        fail(f"could not update compute platform ({platform_id}): {error.orig}")
    click.echo(f"done: updated compute platform ({platform_id}): {', '.join(sorted(changes))}")


def compute_platform_set_enabled(platform_id, enabled):
    platform = get_platform_or_fail(platform_id)
    if platform.enabled == enabled:
        click.echo(f"compute platform ({platform_id}) is already {'enabled' if enabled else 'disabled'}")
        return

    if enabled:
        # never offer a platform that can't run anything
        get_connection_or_fail(platform.connection_key)
        if not DatabaseEngine().list_compute_platform_workflows(platform_id):
            fail(f"compute platform ({platform_id}) has no allowlisted workflows; add one before enabling it")

    DatabaseEngine().update_compute_platform(platform_id, {"enabled": enabled})
    click.echo(f"done: compute platform ({platform_id}) is now {'enabled' if enabled else 'disabled'}")


def compute_platform_delete(platform_id, yes):
    get_platform_or_fail(platform_id)
    if not yes:
        click.confirm(
            f"delete compute platform ({platform_id})? its visa, every researcher's access request for it, "
            "and its workflow allowlist will be deleted too",
            abort=True,
        )
    DatabaseEngine().delete_compute_platform(platform_id)
    click.echo(f"done: deleted compute platform ({platform_id}), its visa, access requests and allowlist")
