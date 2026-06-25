import click
from refcloud.handlers.dataset.init import dataset_init
from refcloud.handlers.dataset.onboard import dataset_onboard

@click.group()
def main() -> None:
    """Data onboarding for the GA4GH Reference Cloud"""
    pass

@main.group()
def dataset():
    """Manage datasets"""
    pass

@dataset.command()
def init():
    """Initialize a new empty dataset in the database"""
    dataset_init()

@dataset.command()
def onboard():
    """Register data objects to an existing dataset"""
    dataset_onboard()
