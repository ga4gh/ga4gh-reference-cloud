import click
from refcloud.config.database_engine import DatabaseEngine
from refcloud.config.constants import SUPPORTED_DATASETS
from operator import itemgetter

def dataset_init(dataset_id):
    database_engine = DatabaseEngine()

    # first check if specified dataset is in the list of supported datasets
    if dataset_id not in SUPPORTED_DATASETS.keys():
        click.echo(f"error: dataset id ({dataset_id}) not in list of supported dataset ids. exiting")
        raise SystemExit(1)

    # next check if the specified dataset already exists
    if database_engine.get_dataset_by_id(dataset_id) != None:
        click.echo(f"error: dataset with id ({dataset_id}) already exists in the database. exiting")
        raise SystemExit(1)

    dataset = SUPPORTED_DATASETS[dataset_id]["dataset"]
    visa = SUPPORTED_DATASETS[dataset_id]["visa"]
    visa["dataset_id"] = dataset["id"]

    database_engine.insert_dataset(**dataset)
    database_engine.insert_passport_visa(**visa)

    click.echo(f"done: successfully added new dataset ({dataset_id}) and associated visa to the database")
