import click
from refcloud.config.database_engine import DatabaseEngine

def dataset_reset(dataset_id, yes):
    database_engine = DatabaseEngine()

    # first confirm that the specified dataset exists
    if database_engine.get_dataset_by_id(dataset_id) == None:
        click.echo(f"error: dataset with id ({dataset_id}) does not exist in the database. exiting")
        raise SystemExit(1)

    if not yes:
        click.confirm(f"delete all data objects and data connect tables from dataset ({dataset_id})? dataset, tags, and passport visas will be preserved", abort=True)

    database_engine.reset_dataset(dataset_id)

    click.echo(f"done: successfully reset dataset ({dataset_id}). run 'dataset onboard' to reload its data")
