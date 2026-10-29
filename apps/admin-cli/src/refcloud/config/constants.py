import re
from refcloud.config.seed_resources import list_table_type_dirs
from refcloud.custom_onboarding.onboard_1000_genomes import onboard_1000_genomes
from refcloud.custom_onboarding.onboard_refget_sequences_v2_conformance import onboard_refget_sequences_v2_conformance

SUPPORTED_DATASETS = {
    "aws_opendata_registry_1000_genomes": {
        "dataset": {
            "id": "aws_opendata_registry_1000_genomes",
            "name": "1000 Genomes",
            "description": "The 1000 Genomes Project is an international collaboration which has established the most detailed catalogue of human genetic variation, including SNPs, structural variants, and their haplotype context. The final phase of the project sequenced more than 2500 individuals from 26 different populations around the world and produced an integrated set of phased haplotypes with more than 80 million variants for these individuals."
        },
        "visa": {
            "id": "ga4gh_visa:aws_opendata_registry_1000_genomes",
            "name": "GA4GH Visa: 1000 Genomes",
            "description": "GA4GH Visa required to access data from the 1000 Genomes dataset",
        },
        "tags": [
            "Variant Calling",
            "Whole Genome"
        ],
        "data_source": {
            "type": "s3",
            "s3info": {
                "custom_onboarding_function": onboard_1000_genomes
            }
        },
        "data_connect_tables": [
            {"table_type": "drs_object", "description": "DRS objects in the 1000 Genomes dataset"}
        ]
    },
    "ga4gh_refget_sequences_v2_conformance": {
        "dataset": {
            "id": "ga4gh_refget_sequences_v2_conformance",
            "name": "GA4GH Refget Sequences v2 Conformance Test Dataset",
            "description": "Test sequences for GA4GH Refget Sequences v2 Conformance"
        },
        "visa": None, # conformance data that will not require a researcher passport, hence no visa is required to access it
        "tags": [
            "Reference Sequences",
            "Conformance"
        ],
        "data_source": {
            "type": "s3",
            "s3info": {
                "custom_onboarding_function": onboard_refget_sequences_v2_conformance
            }
        },
        "data_connect_tables": [
            {"table_type": "drs_object", "description": "DRS objects in the GA4GH Refget Sequences v2 Conformance Test Dataset"},
            {"table_type": "refget_sequence", "description": "Refget reference sequences in the GA4GH Refget Sequences v2 Conformance Test Dataset"}
        ]
    }
}

# dataset ids become a single segment of data connect table names (e.g. refcloud.{dataset_id}.drs_object),
# so they must be lowercase snake case to satisfy data_connect_table's table_name format check
DATASET_ID_PATTERN = re.compile(r"^[a-z_][a-z0-9_]*$")
SEED_TABLE_TYPE_IDS = {d.name for d in list_table_type_dirs()}

def validate_supported_datasets():
    """Return a list of configuration errors in SUPPORTED_DATASETS (empty if valid)"""
    errors = []
    for key, config in SUPPORTED_DATASETS.items():
        if not DATASET_ID_PATTERN.match(key):
            errors.append(f"dataset id ({key}) must be lowercase snake case (letters, digits, underscores, not starting with a digit)")
        if config["dataset"]["id"] != key:
            errors.append(f"dataset id ({config['dataset']['id']}) does not match its SUPPORTED_DATASETS key ({key})")
        if config["visa"] is not None and config["visa"]["id"] != f"ga4gh_visa:{key}":
            errors.append(f"visa id ({config['visa']['id']}) must be ga4gh_visa:{key}")

        # data_connect_table has a unique (dataset_id, table_type) constraint, and table_type must be a seeded table type
        table_types = [table["table_type"] for table in config.get("data_connect_tables", [])]
        for table_type in sorted(set(table_types)):
            if table_type not in SEED_TABLE_TYPE_IDS:
                errors.append(f"data connect table type ({table_type}) for dataset ({key}) is not one of the seed table types: {sorted(SEED_TABLE_TYPE_IDS)}")
            if table_types.count(table_type) > 1:
                errors.append(f"data connect table type ({table_type}) is configured more than once for dataset ({key})")
    return errors

def data_connect_table_name(dataset_id, table_type):
    return f"refcloud.{dataset_id}.{table_type}"
