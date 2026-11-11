import click

from refcloud.config.database_engine import DatabaseEngine
from refcloud.handlers.visa import APPROVED, DENIED, REQUESTED, REVOKED

# allowed transitions for each admin action: (statuses a request may be in, status it moves to).
# researchers can't re-request after a denial or revocation, so approve is how access is restored
TRANSITIONS = {
    "approve": ([REQUESTED, DENIED, REVOKED], APPROVED),
    "deny": ([REQUESTED], DENIED),
    "revoke": ([APPROVED], REVOKED),
}


def visa_set_request_status(action, user_id, visa_id):
    from_statuses, to_status = TRANSITIONS[action]
    database_engine = DatabaseEngine()

    if database_engine.get_visa_by_id(visa_id) is None:
        click.echo(f"error: visa with id ({visa_id}) does not exist in the database. exiting")
        raise SystemExit(1)

    request = database_engine.get_visa_request(user_id, visa_id)
    if request is None:
        click.echo(f"error: user ({user_id}) has not requested access to visa ({visa_id}). exiting")
        raise SystemExit(1)

    current_status = str(request.current_status)
    if current_status not in from_statuses:
        click.echo(
            f"error: cannot {action} the request from user ({user_id}) for visa ({visa_id}): its status is "
            f"{current_status}, and only requests that are {' or '.join(from_statuses)} can be {to_status.lower()}"
            ". exiting"
        )
        raise SystemExit(1)

    # the update re-checks the current status, in case it changed since it was read above
    if not database_engine.update_visa_request_status(user_id, visa_id, from_statuses, to_status):
        click.echo(f"error: the request from user ({user_id}) for visa ({visa_id}) changed while updating. retry")
        raise SystemExit(1)

    click.echo(f"done: request from user ({user_id}) for visa ({visa_id}) is now {to_status} (was {current_status})")
