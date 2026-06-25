from sqlalchemy import create_engine, MetaData, select, insert, delete
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
        self._dataset_tag_table = self._metadata.tables['tag']
        self._passport_visa_table = self._metadata.tables['passport_visa']
        self._drs_object_table = self._metadata.tables['drs_object']
        self._drs_object_alias_table = self._metadata.tables['drs_object_alias']
        self._drs_object_checksum_table = self._metadata.tables['drs_object_checksum']
        self._aws_s3_access_object_table = self._metadata.tables['aws_s3_access_object']

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

    def delete_dataset(self, id):
        stmt = delete(self._dataset_table).where(self._dataset_table.c.id == id)
        with self._engine.connect() as conn:
            conn.execute(stmt)
            conn.commit()