import click
from refcloud.handlers.dataset.init import dataset_init
from refcloud.handlers.dataset.onboard import dataset_onboard
from refcloud.handlers.dataset.delete import dataset_delete
from refcloud.handlers.dataset.reset import dataset_reset
from refcloud.handlers.seed import seed_database

@click.group()
def main() -> None:
    """Data onboarding for the GA4GH Reference Cloud"""
    pass

@main.group()
def dataset():
    """Manage datasets"""
    pass

@dataset.command()
@click.option('--dataset-id', '-d', required=True, help="Dataset ID")
def init(dataset_id):
    """Initialize a new empty dataset in the database"""
    dataset_init(dataset_id)

@dataset.command()
@click.option('--dataset-id', '-d', required=True, help="Dataset ID")
def onboard(dataset_id):
    """Register data objects to an existing dataset"""
    dataset_onboard(dataset_id)

@dataset.command()
@click.option('--dataset-id', '-d', required=True, help="Dataset ID")
def delete(dataset_id):
    """Delete an existing dataset from the database"""
    dataset_delete(dataset_id)

@dataset.command()
@click.option('--dataset-id', '-d', required=True, help="Dataset ID")
@click.option('--yes', '-y', is_flag=True, help="Skip the confirmation prompt")
def reset(dataset_id, yes):
    """Delete data from existing dataset, preserving auth info"""
    dataset_reset(dataset_id, yes)

@main.command(help=seed_database.__doc__)
def seed():
    seed_database()
