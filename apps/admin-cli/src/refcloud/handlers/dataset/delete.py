import click
from refcloud.config.database_engine import DatabaseEngine
from refcloud.config.constants import SUPPORTED_DATASETS
from operator import itemgetter

def dataset_delete(dataset_id):
    database_engine = DatabaseEngine()

    # first confirm that the specified dataset exists
    if database_engine.get_dataset_by_id(dataset_id) == None:
        click.echo(f"error: dataset with id ({dataset_id}) does not exist in the database. exiting")
        raise SystemExit(1)

    database_engine.delete_dataset(dataset_id)

    click.echo(f"done: successfully dataset ({dataset_id}) from the database")
