import boto3
import click
from botocore import UNSIGNED
from botocore.config import Config
from refcloud.config.database_engine import DatabaseEngine
import datetime
from refcloud.config.constants import SUPPORTED_DATASETS, validate_supported_datasets, data_connect_table_name
from refcloud.handlers.seed import require_database_seeded
from operator import itemgetter


def dataset_onboard(dataset_id):
    database_engine = DatabaseEngine()

    # guard against invalid dataset configuration before touching the database
    errors = validate_supported_datasets()
    if errors:
        for error in errors:
            click.echo(f"error: {error}")
        click.echo("error: invalid SUPPORTED_DATASETS configuration. exiting")
        raise SystemExit(1)

    # seed must be run first, as dataset data connect tables reference the seeded table types
    require_database_seeded(database_engine)

    # first check if specified dataset is in the list of supported datasets
    if dataset_id not in SUPPORTED_DATASETS.keys():
        click.echo(f"error: dataset id ({dataset_id}) not in list of supported dataset ids. exiting")
        raise SystemExit(1)

    # next confirm that the specified dataset exists
    if database_engine.get_dataset_by_id(dataset_id) == None:
        click.echo(f"error: dataset with id ({dataset_id}) does not exist in the database. exiting")
        raise SystemExit(1)

    data_source = SUPPORTED_DATASETS[dataset_id]["data_source"]
    if data_source["type"] == "s3":
        s3_client = boto3.client('s3', config=Config(signature_version=UNSIGNED))
        onboarding_fn = data_source["s3info"]["custom_onboarding_function"]

        for drs_object_set in onboarding_fn(s3_client): # load drs objects and sub-objects (aliases, checksums, aws s3 access objects) into db
            if len(drs_object_set['file_keys']) > 0:

                all_meta_objects = [drs_object_set['file_objs'][file_key] for file_key in drs_object_set['file_keys']]
                if "manifest_obj" in drs_object_set:
                    all_meta_objects += [drs_object_set["manifest_obj"]]

                for meta_obj in all_meta_objects:
                    # unpack
                    drs_object, aliases, checksums, aws_s3_access_objects = itemgetter("drs_object", "aliases", "checksums", "aws_s3_access_objects")(meta_obj)

                    # add drs object
                    drs_object["dataset_id"] = dataset_id
                    database_engine.insert_drs_object(**drs_object)

                    # add aliases
                    for alias in aliases:
                        alias_obj = {"drs_object_id": drs_object["id"], "alias":alias}
                        database_engine.insert_drs_object_alias(**alias_obj)

                    # add checksums
                    for checksum in checksums:
                        checksum["drs_object_id"] = drs_object["id"]
                        database_engine.insert_drs_object_checksum(**checksum)

                    # add aws s3 access object
                    for aws_s3_access_object in aws_s3_access_objects:
                        aws_s3_access_object["drs_object_id"] = drs_object["id"]
                        database_engine.insert_aws_s3_access_object(**aws_s3_access_object)

                    # add refget sequence metadata if present, will lead to object being available by Refget API
                    if "refget_sequence" in meta_obj.keys():
                        refget_sequence = meta_obj["refget_sequence"]
                        refget_sequence["drs_object_id"] = drs_object["id"]
                        database_engine.insert_refget_sequence(**refget_sequence)

                    if "refget_sequence_aliases" in meta_obj.keys():
                        for refget_sequence_alias in meta_obj["refget_sequence_aliases"]:
                            refget_sequence_alias["refget_sequence_id"] = refget_sequence["drs_object_id"]
                            database_engine.insert_refget_sequence_alias(**refget_sequence_alias)

    # register dataset data connect tables only after all drs objects are loaded, so tables never point at partial data
    now = datetime.datetime.now()
    tables = [
        {
            "table_name": data_connect_table_name(dataset_id, table["table_type"]),
            "dataset_id": dataset_id,
            "table_type": table["table_type"],
            "description": table["description"],
            "created_at": now,
            "updated_at": now,
        }
        for table in SUPPORTED_DATASETS[dataset_id].get("data_connect_tables", [])
    ]
    database_engine.upsert_data_connect_tables(tables)
    for table in tables:
        click.echo(f"added data connect table: {table['table_name']}")
