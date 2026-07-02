import boto3
import click
from botocore import UNSIGNED
from botocore.config import Config
from refcloud.config.database_engine import DatabaseEngine
from refcloud.config.constants import SUPPORTED_DATASETS
from operator import itemgetter


def dataset_onboard(dataset_id):
    database_engine = DatabaseEngine()

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
                all_meta_objects = [drs_object_set['file_objs'][file_key] for file_key in drs_object_set['file_keys']] + [drs_object_set["manifest_obj"]]
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
