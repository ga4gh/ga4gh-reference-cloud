import click
import datetime
import json
from refcloud.config.database_engine import DatabaseEngine
from refcloud.config.seed_resources import list_table_type_dirs, list_platform_table_files

def seed_database():
    """Seed platform-wide data into the database

    Inserts or updates Data Connect table types and platform-wide tables.
    Safe to re-run. Per-dataset tables are created by 'dataset onboard'.
    """
    database_engine = DatabaseEngine()

    # load and validate all seed files before writing anything
    table_types = load_data_connect_table_types()
    tables = load_platform_data_connect_tables()

    database_engine.upsert_data_connect_table_types(table_types)
    for table_type in table_types:
        click.echo(f"seeded table type: {table_type['id']}")

    # tables must be seeded after table types, as data_connect_table.table_type references data_connect_table_type.id
    database_engine.upsert_data_connect_tables(tables)
    for table in tables:
        click.echo(f"seeded table: {table['table_name']}")

    click.echo("done: successfully seeded the database")


def require_database_seeded(database_engine):
    """Exit with an error unless every seed table type and platform table exists in the database"""
    expected_table_types = {d.name for d in list_table_type_dirs()}
    expected_tables = {json.loads(f.read_text())["table_name"] for f in list_platform_table_files()}

    missing_table_types = sorted(expected_table_types - database_engine.get_data_connect_table_type_ids())
    missing_tables = sorted(expected_tables - database_engine.get_data_connect_table_names())

    if missing_table_types or missing_tables:
        for table_type in missing_table_types:
            click.echo(f"error: data connect table type ({table_type}) not found in the database")
        for table in missing_tables:
            click.echo(f"error: data connect table ({table}) not found in the database")
        click.echo("error: database has not been seeded. run 'refcloud seed' first. exiting")
        raise SystemExit(1)


def load_data_connect_table_types():
    """Load one data_connect_table_type row per subdirectory of table-types/"""
    now = datetime.datetime.now()
    table_types = []

    for table_type_dir in list_table_type_dirs():
        table_type = json.loads((table_type_dir / "table-type.json").read_text())

        # guard against copy/paste errors between table type directories
        if table_type["id"] != table_type_dir.name:
            click.echo(f"error: table type id ({table_type['id']}) does not match its directory name ({table_type_dir.name}). exiting")
            raise SystemExit(1)

        table_types.append({
            "id": table_type["id"],
            "description": table_type["description"],
            "data_model_template": json.loads((table_type_dir / "data-model.json").read_text()),
            "internal_query": (table_type_dir / "internal-query.sql").read_text(),
            "created_at": now,
            "updated_at": now,
        })

    return table_types


def load_platform_data_connect_tables():
    """Load one data_connect_table row per json file in tables/platform/"""
    now = datetime.datetime.now()
    tables = []

    for table_file in list_platform_table_files():
        table = json.loads(table_file.read_text())

        # platform tables are not tied to any dataset; dataset-specific tables are created by 'dataset onboard'
        if table["dataset_id"] is not None:
            click.echo(f"error: platform table ({table['table_name']}) in {table_file.name} must have a null dataset_id. exiting")
            raise SystemExit(1)

        tables.append({
            "table_name": table["table_name"],
            "dataset_id": table["dataset_id"],
            "table_type": table["table_type"],
            "description": table["description"],
            "created_at": now,
            "updated_at": now,
        })

    return tables
