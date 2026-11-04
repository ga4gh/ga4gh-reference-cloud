SELECT
    d.id,
    d.name,
    d.description,
    COALESCE(
        (
            SELECT jsonb_agg(t.tag ORDER BY t.tag)
            FROM dataset_tag dt
            JOIN tag t ON t.id = dt.tag_id
            WHERE dt.dataset_id = d.id
        ),
        '[]'::jsonb
    ) AS tags,
    EXISTS (
        SELECT 1 FROM passport_visa pv WHERE pv.dataset_id = d.id
    ) AS requires_visa
FROM dataset d
