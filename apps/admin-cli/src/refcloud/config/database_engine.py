from sqlalchemy import create_engine, MetaData, select, insert, delete
from sqlalchemy.dialects.postgresql import insert as pg_insert
from refcloud.config.env_config import EnvConfig

class DatabaseEngine:
    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._load_engine()
        return cls._instance
    
    def _load_engine(self):
        config = EnvConfig()
        self._engine = create_engine(f"postgresql+psycopg://{config.POSTGRES_USER}:{config.POSTGRES_PASSWORD}@{config.POSTGRES_HOST}:{config.POSTGRES_PORT}/{config.POSTGRES_DB}")
        self._metadata = MetaData()
        self._metadata.reflect(bind=self._engine)

        self._dataset_table = self._metadata.tables['dataset']
        self._tag_table = self._metadata.tables['tag']
        self._dataset_tag_table = self._metadata.tables['dataset_tag']
        self._passport_visa_table = self._metadata.tables['passport_visa']
        self._drs_object_table = self._metadata.tables['drs_object']
        self._drs_object_alias_table = self._metadata.tables['drs_object_alias']
        self._drs_object_checksum_table = self._metadata.tables['drs_object_checksum']
        self._aws_s3_access_object_table = self._metadata.tables['aws_s3_access_object']
        self._refget_sequence_table = self._metadata.tables['refget_sequence']
        self._refget_sequence_alias_table = self._metadata.tables['refget_sequence_alias']
        self._data_connect_table_table = self._metadata.tables['data_connect_table']
        self._data_connect_table_type_table = self._metadata.tables['data_connect_table_type']

    def _insert(self, table, **kwargs):
        stmt = insert(table).values(**kwargs)
        result = None
        with self._engine.connect() as conn:
            result = conn.execute(stmt)
            conn.commit()
        return result

    def list_datasets(self):
        stmt = select(self._dataset_table)
        with self._engine.connect() as conn:
            return conn.execute(stmt).all()

    def get_dataset_by_id(self, id):
        stmt = select(self._dataset_table).where(self._dataset_table.c.id == id)
        with self._engine.connect() as conn:
            return conn.execute(stmt).first()

    def get_tag(self, tag):
        stmt = select(self._tag_table).where(self._tag_table.c.tag == tag)
        with self._engine.connect() as conn:
            return conn.execute(stmt).first()

    def insert_tag(self, **kwargs):
        return self._insert(self._tag_table, **kwargs)

    def insert_dataset_tag(self, **kwargs):
        return self._insert(self._dataset_tag_table, **kwargs)

    def insert_dataset(self, **kwargs):
        return self._insert(self._dataset_table, **kwargs)

    def insert_passport_visa(self, **kwargs):
        return self._insert(self._passport_visa_table, **kwargs)

    def insert_drs_object(self, **kwargs):
        return self._insert(self._drs_object_table, **kwargs)

    def insert_drs_object_alias(self, **kwargs):
        return self._insert(self._drs_object_alias_table, **kwargs)

    def insert_drs_object_checksum(self, **kwargs):
        return self._insert(self._drs_object_checksum_table, **kwargs)

    def insert_aws_s3_access_object(self, **kwargs):
        return self._insert(self._aws_s3_access_object_table, **kwargs)

    def insert_refget_sequence(self, **kwargs):
        return self._insert(self._refget_sequence_table, **kwargs)

    def insert_refget_sequence_alias(self, **kwargs):
        return self._insert(self._refget_sequence_alias_table, **kwargs)

    def delete_dataset(self, id):
        stmt = delete(self._dataset_table).where(self._dataset_table.c.id == id)
        with self._engine.connect() as conn:
            conn.execute(stmt)
            conn.commit()

    def reset_dataset(self, id):
        # deletes all data (drs objects and data connect tables) but preserves the dataset, its tags,
        # and its passport visas/user assertions. drs sub-objects are removed via ON DELETE CASCADE
        drs_object_stmt = delete(self._drs_object_table).where(self._drs_object_table.c.dataset_id == id)
        data_connect_table_stmt = delete(self._data_connect_table_table).where(self._data_connect_table_table.c.dataset_id == id)
        with self._engine.begin() as conn: # single transaction, rolls back if either delete fails
            conn.execute(drs_object_stmt)
            conn.execute(data_connect_table_stmt)

    def _upsert_data_connect_table_type(self, conn, **kwargs):
        # insert a new table type, or update an existing one. created_at is never overwritten on update
        stmt = pg_insert(self._data_connect_table_type_table).values(**kwargs)
        stmt = stmt.on_conflict_do_update(
            index_elements=[self._data_connect_table_type_table.c.id],
            set_={
                "description": stmt.excluded.description,
                "data_model_template": stmt.excluded.data_model_template,
                "internal_query": stmt.excluded.internal_query,
                "updated_at": stmt.excluded.updated_at,
            }
        )
        conn.execute(stmt)

    def get_data_connect_table_type_ids(self):
        stmt = select(self._data_connect_table_type_table.c.id)
        with self._engine.connect() as conn:
            return set(conn.execute(stmt).scalars())

    def get_data_connect_table_names(self):
        stmt = select(self._data_connect_table_table.c.table_name)
        with self._engine.connect() as conn:
            return set(conn.execute(stmt).scalars())

    def upsert_data_connect_table_types(self, table_types):
        with self._engine.begin() as conn: # single transaction, rolls back if any upsert fails
            for table_type in table_types:
                self._upsert_data_connect_table_type(conn, **table_type)

    def _upsert_data_connect_table(self, conn, **kwargs):
        # insert a new table, or update an existing one. created_at is never overwritten on update
        stmt = pg_insert(self._data_connect_table_table).values(**kwargs)
        stmt = stmt.on_conflict_do_update(
            index_elements=[self._data_connect_table_table.c.table_name],
            set_={
                "dataset_id": stmt.excluded.dataset_id,
                "table_type": stmt.excluded.table_type,
                "description": stmt.excluded.description,
                "updated_at": stmt.excluded.updated_at,
            }
        )
        conn.execute(stmt)

    def upsert_data_connect_tables(self, tables):
        with self._engine.begin() as conn: # single transaction, rolls back if any upsert fails
            for table in tables:
                self._upsert_data_connect_table(conn, **table)