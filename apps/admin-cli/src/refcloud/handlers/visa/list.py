import click

from refcloud.config.database_engine import DatabaseEngine
from refcloud.handlers.visa import APPROVED, DENIED, REQUESTED, REVOKED, echo_table


def visa_list():
    database_engine = DatabaseEngine()
    visas, request_counts = database_engine.list_visas()

    if not visas:
        click.echo("no visas found in the database")
        return

    rows = []
    for visa in visas:
        counts = request_counts.get(visa.id, {})
        rows.append(
            [
                visa.id,
                visa.name,
                visa.description,
                gated_resource(visa),
                counts.get(REQUESTED, 0),
                counts.get(APPROVED, 0),
                counts.get(DENIED, 0),
                counts.get(REVOKED, 0),
            ]
        )

    echo_table(
        ["VISA ID", "NAME", "DESCRIPTION", "GATES", "REQUESTED", "APPROVED", "DENIED", "REVOKED"],
        rows,
    )


def gated_resource(visa):
    """Describe the one resource a visa gates"""
    # the database guarantees exactly one of these is set
    if visa.dataset_id is not None:
        return f"dataset: {visa.dataset_id} ({visa.dataset_name})"
    return f"compute platform: {visa.compute_platform_id} ({visa.compute_platform_name})"
