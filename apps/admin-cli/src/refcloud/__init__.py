import click

from refcloud.handlers.compute_platform import limit_options
from refcloud.handlers.compute_platform.allowlist import allowlist_add, allowlist_list, allowlist_remove
from refcloud.handlers.compute_platform.manage import (
    compute_platform_delete,
    compute_platform_init,
    compute_platform_list,
    compute_platform_set_enabled,
    compute_platform_update,
)
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


@main.group(name="compute-platform")
def compute_platform():
    """Manage compute platforms (visa-gated WES/TES services) and their workflow allowlists"""
    pass


@compute_platform.command(name="init")
@click.option("--platform-id", "-p", required=True, help="Platform ID: lowercase letters, digits, hyphens")
@click.option("--name", required=True, help="Display name (unique)")
@click.option("--description", required=True, help="Description shown to researchers")
@click.option("--connection", "connection_key", required=True, help="Connection key from connections.yml")
@limit_options
def compute_platform_init_command(platform_id, name, description, connection_key, **raw_limits):
    """Create a compute platform and its visa. It starts disabled"""
    compute_platform_init(platform_id, name, description, connection_key, raw_limits)


@compute_platform.command(name="list")
def compute_platform_list_command():
    """List compute platforms, their connections, limits and visas"""
    compute_platform_list()


@compute_platform.command(name="update")
@click.option("--platform-id", "-p", required=True, help="Platform ID")
@click.option("--name", help="New display name (its visa is renamed too)")
@click.option("--description", help="New description")
@click.option("--connection", "connection_key", help="New connection key from connections.yml")
@limit_options
def compute_platform_update_command(platform_id, name, description, connection_key, **raw_limits):
    """Change a compute platform's details or per-user limits"""
    compute_platform_update(platform_id, name, description, connection_key, raw_limits)


@compute_platform.command(name="enable")
@click.option("--platform-id", "-p", required=True, help="Platform ID")
def compute_platform_enable_command(platform_id):
    """Offer a compute platform to researchers (needs its connection and at least one workflow)"""
    compute_platform_set_enabled(platform_id, True)


@compute_platform.command(name="disable")
@click.option("--platform-id", "-p", required=True, help="Platform ID")
def compute_platform_disable_command(platform_id):
    """Stop offering a compute platform to researchers"""
    compute_platform_set_enabled(platform_id, False)


@compute_platform.command(name="delete")
@click.option("--platform-id", "-p", required=True, help="Platform ID")
@click.option("--yes", "-y", is_flag=True, help="Skip the confirmation prompt")
def compute_platform_delete_command(platform_id, yes):
    """Delete a compute platform, its visa, access requests and allowlist"""
    compute_platform_delete(platform_id, yes)


@compute_platform.group(name="allowlist")
def compute_platform_allowlist():
    """Manage the workflows a compute platform may run"""
    pass


@compute_platform_allowlist.command(name="add")
@click.option("--platform-id", "-p", required=True, help="Platform ID")
@click.option("--name", required=True, help="Workflow name shown to researchers")
@click.option("--description", required=True, help="Workflow description")
@click.option("--git-repository", help="GitHub repository URL (git source; needs --ref)")
@click.option("--ref", "git_ref", help="Tag or full commit sha to pin (git source)")
@click.option("--trs-descriptor-url", help="TRS version descriptor URL, e.g. from Dockstore (GitHub-hosted only)")
@click.option("--bundled", help="Name of a workflow bundled in compute/workflows/nextflow/")
@click.option("--main-script", default="main.nf", show_default=True, help="Main script (git and TRS sources)")
@click.option("--profile", "profiles", multiple=True, help="Engine profile, e.g. test (repeatable)")
@click.option("--engine-env", multiple=True, help="Engine environment variable KEY=VALUE (repeatable)")
def compute_platform_allowlist_add_command(
    platform_id,
    name,
    description,
    git_repository,
    git_ref,
    trs_descriptor_url,
    bundled,
    main_script,
    profiles,
    engine_env,
):
    """Allowlist a workflow, pinned to a commit"""
    allowlist_add(
        platform_id,
        name,
        description,
        git_repository,
        git_ref,
        trs_descriptor_url,
        bundled,
        main_script,
        profiles,
        engine_env,
    )


@compute_platform_allowlist.command(name="remove")
@click.option("--platform-id", "-p", required=True, help="Platform ID")
@click.option("--workflow-id", "-w", required=True, type=int, help="Allowlist entry ID (see allowlist list)")
def compute_platform_allowlist_remove_command(platform_id, workflow_id):
    """Remove a workflow from the allowlist"""
    allowlist_remove(platform_id, workflow_id)


@compute_platform_allowlist.command(name="list")
@click.option("--platform-id", "-p", required=True, help="Platform ID")
def compute_platform_allowlist_list_command(platform_id):
    """List a compute platform's allowlisted workflows and the workflow_url to submit for each"""
    allowlist_list(platform_id)


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
