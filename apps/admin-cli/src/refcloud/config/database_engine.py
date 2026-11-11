from sqlalchemy import MetaData, and_, create_engine, delete, func, insert, select, update
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
        # sessions run in UTC, so timestamps read back are UTC and any zone-less value is taken as UTC
        self._engine = create_engine(
            f"postgresql+psycopg://{config.POSTGRES_USER}:{config.POSTGRES_PASSWORD}@{config.POSTGRES_HOST}:{config.POSTGRES_PORT}/{config.POSTGRES_DB}",
            connect_args={"options": "-c timezone=UTC"},
        )
        self._metadata = MetaData()
        self._metadata.reflect(bind=self._engine)

        self._dataset_table = self._metadata.tables["dataset"]
        self._tag_table = self._metadata.tables["tag"]
        self._dataset_tag_table = self._metadata.tables["dataset_tag"]
        self._passport_visa_table = self._metadata.tables["passport_visa"]
        self._passport_user_visa_assertion_table = self._metadata.tables["passport_user_visa_assertion"]
        self._compute_platform_table = self._metadata.tables["compute_platform"]
        self._compute_platform_workflow_table = self._metadata.tables["compute_platform_workflow"]
        self._drs_object_table = self._metadata.tables["drs_object"]
        self._drs_object_alias_table = self._metadata.tables["drs_object_alias"]
        self._drs_object_checksum_table = self._metadata.tables["drs_object_checksum"]
        self._aws_s3_access_object_table = self._metadata.tables["aws_s3_access_object"]
        self._refget_sequence_table = self._metadata.tables["refget_sequence"]
        self._refget_sequence_alias_table = self._metadata.tables["refget_sequence_alias"]
        self._data_connect_table_table = self._metadata.tables["data_connect_table"]
        self._data_connect_table_type_table = self._metadata.tables["data_connect_table_type"]

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

    def list_visas(self):
        # every visa with the one resource it gates (a dataset or a compute platform), plus a count of its access
        # requests by status
        visa = self._passport_visa_table
        dataset = self._dataset_table
        compute_platform = self._compute_platform_table
        assertion = self._passport_user_visa_assertion_table
        counts = (
            select(assertion.c.visa_id, assertion.c.current_status, func.count().label("count"))
            .group_by(assertion.c.visa_id, assertion.c.current_status)
            .subquery()
        )
        visas_stmt = (
            select(
                visa.c.id,
                visa.c.name,
                visa.c.description,
                visa.c.dataset_id,
                dataset.c.name.label("dataset_name"),
                visa.c.compute_platform_id,
                compute_platform.c.name.label("compute_platform_name"),
            )
            .select_from(
                visa.outerjoin(dataset, visa.c.dataset_id == dataset.c.id).outerjoin(
                    compute_platform, visa.c.compute_platform_id == compute_platform.c.id
                )
            )
            .order_by(visa.c.id)
        )
        counts_stmt = select(counts.c.visa_id, counts.c.current_status, counts.c.count)
        with self._engine.connect() as conn:
            visas = conn.execute(visas_stmt).all()
            request_counts = {}
            for visa_id, status, count in conn.execute(counts_stmt).all():
                request_counts.setdefault(visa_id, {})[str(status)] = count
        return visas, request_counts

    def get_visa_by_id(self, id):
        stmt = select(self._passport_visa_table).where(self._passport_visa_table.c.id == id)
        with self._engine.connect() as conn:
            return conn.execute(stmt).first()

    def list_visa_requests(self, status=None, visa_id=None):
        # access requests (user visa assertions), oldest status change first. no filter means all statuses
        visa = self._passport_visa_table
        assertion = self._passport_user_visa_assertion_table
        stmt = (
            select(
                assertion.c.user_id,
                assertion.c.visa_id,
                visa.c.name.label("visa_name"),
                assertion.c.current_status,
                assertion.c.current_status_at,
            )
            .select_from(assertion.join(visa, assertion.c.visa_id == visa.c.id))
            .order_by(assertion.c.current_status_at, assertion.c.user_id)
        )
        if status is not None:
            stmt = stmt.where(assertion.c.current_status == status)
        if visa_id is not None:
            stmt = stmt.where(assertion.c.visa_id == visa_id)
        with self._engine.connect() as conn:
            return conn.execute(stmt).all()

    def get_visa_request(self, user_id, visa_id):
        assertion = self._passport_user_visa_assertion_table
        stmt = select(assertion).where(and_(assertion.c.user_id == user_id, assertion.c.visa_id == visa_id))
        with self._engine.connect() as conn:
            return conn.execute(stmt).first()

    def update_visa_request_status(self, user_id, visa_id, from_statuses, to_status):
        # moves a request to to_status only if its current status is one of from_statuses, in a single
        # statement, so two admins acting at once can't both apply a transition. returns True if updated.
        assertion = self._passport_user_visa_assertion_table
        stmt = (
            update(assertion)
            .where(
                and_(
                    assertion.c.user_id == user_id,
                    assertion.c.visa_id == visa_id,
                    assertion.c.current_status.in_(from_statuses),
                )
            )
            .values(current_status=to_status, current_status_at=func.now())
        )
        with self._engine.begin() as conn:
            return conn.execute(stmt).rowcount == 1

    def get_compute_platform(self, id):
        stmt = select(self._compute_platform_table).where(self._compute_platform_table.c.id == id)
        with self._engine.connect() as conn:
            return conn.execute(stmt).first()

    def get_compute_platform_by_name(self, name):
        stmt = select(self._compute_platform_table).where(self._compute_platform_table.c.name == name)
        with self._engine.connect() as conn:
            return conn.execute(stmt).first()

    def list_compute_platforms(self):
        # every compute platform with its visa id and allowlist size
        platform = self._compute_platform_table
        visa = self._passport_visa_table
        workflow = self._compute_platform_workflow_table
        workflow_counts = (
            select(workflow.c.compute_platform_id, func.count().label("workflow_count"))
            .group_by(workflow.c.compute_platform_id)
            .subquery()
        )
        stmt = (
            select(
                platform,
                visa.c.id.label("visa_id"),
                func.coalesce(workflow_counts.c.workflow_count, 0).label("workflow_count"),
            )
            .select_from(
                platform.outerjoin(visa, visa.c.compute_platform_id == platform.c.id).outerjoin(
                    workflow_counts, workflow_counts.c.compute_platform_id == platform.c.id
                )
            )
            .order_by(platform.c.id)
        )
        with self._engine.connect() as conn:
            return conn.execute(stmt).all()

    def insert_compute_platform_with_visa(self, platform, visa):
        # one transaction: a compute platform always has its visa (no open-access compute)
        with self._engine.begin() as conn:
            conn.execute(insert(self._compute_platform_table).values(**platform))
            conn.execute(insert(self._passport_visa_table).values(**visa))

    def update_compute_platform(self, id, platform_values, visa_values=None):
        # one transaction, so a renamed platform's visa is renamed with it
        with self._engine.begin() as conn:
            if platform_values:
                conn.execute(
                    update(self._compute_platform_table)
                    .where(self._compute_platform_table.c.id == id)
                    .values(**platform_values)
                )
            if visa_values:
                conn.execute(
                    update(self._passport_visa_table)
                    .where(self._passport_visa_table.c.compute_platform_id == id)
                    .values(**visa_values)
                )

    def delete_compute_platform(self, id):
        # its visa (and so its access requests) and its allowlist are removed by ON DELETE CASCADE
        stmt = delete(self._compute_platform_table).where(self._compute_platform_table.c.id == id)
        with self._engine.begin() as conn:
            conn.execute(stmt)

    def list_compute_platform_workflows(self, compute_platform_id):
        workflow = self._compute_platform_workflow_table
        stmt = select(workflow).where(workflow.c.compute_platform_id == compute_platform_id).order_by(workflow.c.id)
        with self._engine.connect() as conn:
            return conn.execute(stmt).all()

    def get_compute_platform_workflow_by_url(self, compute_platform_id, workflow_url):
        workflow = self._compute_platform_workflow_table
        stmt = select(workflow).where(
            and_(workflow.c.compute_platform_id == compute_platform_id, workflow.c.workflow_url == workflow_url)
        )
        with self._engine.connect() as conn:
            return conn.execute(stmt).first()

    def insert_compute_platform_workflow(self, **kwargs):
        with self._engine.begin() as conn:
            return conn.execute(
                insert(self._compute_platform_workflow_table)
                .values(**kwargs)
                .returning(self._compute_platform_workflow_table.c.id)
            ).scalar_one()

    def delete_compute_platform_workflow(self, compute_platform_id, workflow_id):
        # returns True if an entry was removed
        workflow = self._compute_platform_workflow_table
        stmt = delete(workflow).where(
            and_(workflow.c.compute_platform_id == compute_platform_id, workflow.c.id == workflow_id)
        )
        with self._engine.begin() as conn:
            return conn.execute(stmt).rowcount == 1

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
        data_connect_table_stmt = delete(self._data_connect_table_table).where(
            self._data_connect_table_table.c.dataset_id == id
        )
        with self._engine.begin() as conn:  # single transaction, rolls back if either delete fails
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
            },
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
        with self._engine.begin() as conn:  # single transaction, rolls back if any upsert fails
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
            },
        )
        conn.execute(stmt)

    def upsert_data_connect_tables(self, tables):
        with self._engine.begin() as conn:  # single transaction, rolls back if any upsert fails
            for table in tables:
                self._upsert_data_connect_table(conn, **table)
