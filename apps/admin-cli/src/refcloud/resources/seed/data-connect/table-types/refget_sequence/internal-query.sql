SELECT
    'SQ.' || ga4gh.checksum AS id,
    md5.checksum AS md5,
    trunc512.checksum AS trunc512,
    o.size AS length,
    rs.is_circular AS circular,
    COALESCE(
        (
            SELECT jsonb_agg(
                jsonb_build_object('naming_authority', a.naming_authority, 'alias', a.alias)
                ORDER BY a.naming_authority, a.alias
            )
            FROM refget_sequence_alias a
            WHERE a.refget_sequence_id = rs.drs_object_id
        ),
        '[]'::jsonb
    ) AS aliases,
    'drs://' || CAST(:drs_host_domain AS TEXT) || '/' || o.id AS drs_uri
FROM refget_sequence rs
JOIN drs_object o ON o.id = rs.drs_object_id
LEFT JOIN drs_object_checksum ga4gh ON ga4gh.drs_object_id = o.id AND ga4gh.type = 'sha512t24u'
LEFT JOIN drs_object_checksum md5 ON md5.drs_object_id = o.id AND md5.type = 'md5'
LEFT JOIN drs_object_checksum trunc512 ON trunc512.drs_object_id = o.id AND trunc512.type = 'trunc512'
WHERE o.dataset_id = :dataset_id
