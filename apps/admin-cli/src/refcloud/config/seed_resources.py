from importlib.resources import files

DATA_CONNECT_SEED_DIR = files("refcloud") / "resources" / "seed" / "data-connect"

def list_table_type_dirs():
    """One directory per data connect table type, sorted by name"""
    return sorted((d for d in (DATA_CONNECT_SEED_DIR / "table-types").iterdir() if d.is_dir()), key=lambda d: d.name)

def list_platform_table_files():
    """One json file per platform-wide data connect table, sorted by name"""
    return sorted((f for f in (DATA_CONNECT_SEED_DIR / "tables" / "platform").iterdir() if f.name.endswith(".json")), key=lambda f: f.name)
