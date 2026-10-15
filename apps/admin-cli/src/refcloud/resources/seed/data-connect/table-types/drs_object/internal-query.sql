SELECT
    o.id,
    'drs://' || CAST(:drs_host_domain AS TEXT) || '/' || o.id AS drs_uri,
    o.name,
    o.description,
    o.size,
    o.mime_type,
    COALESCE(
        (
            SELECT jsonb_agg(
                jsonb_build_object('type', c.type::TEXT, 'checksum', c.checksum)
                ORDER BY c.type::TEXT, c.checksum
            )
            FROM drs_object_checksum c
            WHERE c.drs_object_id = o.id
        ),
        '[]'::jsonb
    ) AS checksums,
    COALESCE(
        (
            SELECT jsonb_agg(a.alias ORDER BY a.alias)
            FROM drs_object_alias a
            WHERE a.drs_object_id = o.id
        ),
        '[]'::jsonb
    ) AS aliases,
    o.created_time,
    o.updated_time,
    o.version,
    o.is_manifest
FROM drs_object o
WHERE o.dataset_id = :dataset_id
