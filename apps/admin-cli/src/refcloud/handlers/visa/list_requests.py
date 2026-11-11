import datetime

import click

from refcloud.config.database_engine import DatabaseEngine
from refcloud.handlers.visa import echo_table


def visa_list_requests(status, visa_id):
    database_engine = DatabaseEngine()

    if visa_id is not None and database_engine.get_visa_by_id(visa_id) is None:
        click.echo(f"error: visa with id ({visa_id}) does not exist in the database. exiting")
        raise SystemExit(1)

    # "all" lists every status
    requests = database_engine.list_visa_requests(status=None if status == "all" else status, visa_id=visa_id)

    if not requests:
        click.echo(f"no access requests found (status: {status}{f', visa: {visa_id}' if visa_id else ''})")
        return

    rows = [
        [
            request.user_id,
            request.visa_id,
            request.visa_name,
            request.current_status,
            format_utc(request.current_status_at),
        ]
        for request in requests
    ]
    echo_table(["USER ID", "VISA ID", "VISA NAME", "STATUS", "STATUS SINCE"], rows)


def format_utc(timestamp):
    """Format a timestamp (timezone-aware) in UTC, e.g. 2026-11-11 20:24:16 UTC"""
    if timestamp is None:
        return ""
    return timestamp.astimezone(datetime.UTC).strftime("%Y-%m-%d %H:%M:%S UTC")
