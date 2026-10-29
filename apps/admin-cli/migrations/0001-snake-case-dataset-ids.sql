-- Migrates existing dataset ids (and their passport visa ids) from dot/hyphen notation to snake case, in place.
-- See specs/active/0000-data-connect-seed-command.md, Task 5.
--
-- Preserves all dataset data, tags, and researcher access grants (passport_user_visa_assertion).
-- Runs as a single DO block, so it is atomic: any failure rolls back every change.
-- Safe to re-run: datasets that no longer exist under their old id are skipped.
--
-- Foreign keys referencing dataset.id and passport_visa.id are ON UPDATE NO ACTION and not deferrable,
-- so ids cannot be updated in place. Instead, a new parent row is created, children are re-pointed to it,
-- and the old parent row is deleted.
--
-- Usage: psql -v ON_ERROR_STOP=1 -f migrations/0001-snake-case-dataset-ids.sql

DO $$
DECLARE
    m RECORD;
    old_visa passport_visa%ROWTYPE;
BEGIN
    FOR m IN SELECT * FROM (VALUES
        ('aws.opendata.registry.1000-genomes', 'aws_opendata_registry_1000_genomes'),
        ('ga4gh.refget.sequences.v2.conformance', 'ga4gh_refget_sequences_v2_conformance')
    ) AS t(old_id, new_id)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM dataset WHERE id = m.old_id) THEN
            RAISE NOTICE 'skipping %: not found', m.old_id;
            CONTINUE;
        END IF;

        -- 1. copy the dataset row under its new id
        INSERT INTO dataset (id, name, description)
        SELECT m.new_id, name, description FROM dataset WHERE id = m.old_id;

        -- 2. replace the passport visa and move researcher grants to it
        SELECT * INTO old_visa FROM passport_visa WHERE dataset_id = m.old_id;
        IF FOUND THEN
            -- passport_visa.name and .description are unique, so free them up for the replacement visa
            UPDATE passport_visa
            SET name = name || ' [migrating]', description = description || ' [migrating]'
            WHERE id = old_visa.id;

            INSERT INTO passport_visa (id, name, description, dataset_id)
            VALUES ('ga4gh_visa:' || m.new_id, old_visa.name, old_visa.description, m.new_id);

            UPDATE passport_user_visa_assertion SET visa_id = 'ga4gh_visa:' || m.new_id WHERE visa_id = old_visa.id;

            DELETE FROM passport_visa WHERE id = old_visa.id;
        END IF;

        -- 3. move the remaining dataset-owned rows (drs sub-objects reference drs_object.id, which is unchanged)
        UPDATE dataset_tag SET dataset_id = m.new_id WHERE dataset_id = m.old_id;
        UPDATE drs_object SET dataset_id = m.new_id WHERE dataset_id = m.old_id;
        UPDATE data_connect_table SET dataset_id = m.new_id WHERE dataset_id = m.old_id;

        -- 4. delete the old dataset row; it has no remaining children, so nothing cascades
        DELETE FROM dataset WHERE id = m.old_id;

        RAISE NOTICE 'migrated % -> %', m.old_id, m.new_id;
    END LOOP;
END $$;
