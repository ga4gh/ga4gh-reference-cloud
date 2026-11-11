import click

from refcloud.handlers.dataset.delete import dataset_delete
from refcloud.handlers.dataset.init import dataset_init
from refcloud.handlers.dataset.onboard import dataset_onboard
from refcloud.handlers.dataset.reset import dataset_reset
from refcloud.handlers.seed import seed_database
from refcloud.handlers.visa import REQUEST_STATUSES, REQUESTED
from refcloud.handlers.visa.list import visa_list
from refcloud.handlers.visa.list_requests import visa_list_requests
from refcloud.handlers.visa.status import visa_set_request_status


@click.group()
def main() -> None:
    """Data onboarding for the GA4GH Reference Cloud"""
    pass


@main.group()
def dataset():
    """Manage datasets"""
    pass


@dataset.command()
@click.option("--dataset-id", "-d", required=True, help="Dataset ID")
def init(dataset_id):
    """Initialize a new empty dataset in the database"""
    dataset_init(dataset_id)


@dataset.command()
@click.option("--dataset-id", "-d", required=True, help="Dataset ID")
def onboard(dataset_id):
    """Register data objects to an existing dataset"""
    dataset_onboard(dataset_id)


@dataset.command()
@click.option("--dataset-id", "-d", required=True, help="Dataset ID")
def delete(dataset_id):
    """Delete an existing dataset from the database"""
    dataset_delete(dataset_id)


@dataset.command()
@click.option("--dataset-id", "-d", required=True, help="Dataset ID")
@click.option("--yes", "-y", is_flag=True, help="Skip the confirmation prompt")
def reset(dataset_id, yes):
    """Delete data from existing dataset, preserving auth info"""
    dataset_reset(dataset_id, yes)


@main.command(help=seed_database.__doc__)
def seed():
    seed_database()


@main.group()
def visa():
    """Manage passport visas and researcher access requests"""
    pass


@visa.command(name="list")
def visa_list_command():
    """List every visa, what it gates, and its access requests by status"""
    visa_list()


@visa.command(name="list-requests")
@click.option(
    "--status",
    "-s",
    type=click.Choice([*REQUEST_STATUSES, "all"]),
    default=REQUESTED,
    show_default=True,
    help="Only list requests with this status",
)
@click.option("--visa-id", "-v", help="Only list requests for this visa")
def visa_list_requests_command(status, visa_id):
    """List researcher access requests"""
    visa_list_requests(status, visa_id)


@visa.command(name="approve")
@click.option("--user-id", "-u", required=True, help="User ID (the researcher's token subject)")
@click.option("--visa-id", "-v", required=True, help="Visa ID")
def visa_approve_command(user_id, visa_id):
    """Approve a request (also restores access after a denial or revocation)"""
    visa_set_request_status("approve", user_id, visa_id)


@visa.command(name="deny")
@click.option("--user-id", "-u", required=True, help="User ID (the researcher's token subject)")
@click.option("--visa-id", "-v", required=True, help="Visa ID")
def visa_deny_command(user_id, visa_id):
    """Deny a pending request"""
    visa_set_request_status("deny", user_id, visa_id)


@visa.command(name="revoke")
@click.option("--user-id", "-u", required=True, help="User ID (the researcher's token subject)")
@click.option("--visa-id", "-v", required=True, help="Visa ID")
def visa_revoke_command(user_id, visa_id):
    """Revoke an approved request"""
    visa_set_request_status("revoke", user_id, visa_id)
