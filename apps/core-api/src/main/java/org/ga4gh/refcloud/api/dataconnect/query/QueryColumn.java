package org.ga4gh.refcloud.api.dataconnect.query;

// a result column: its name and Postgres type name (e.g. text, int8, jsonb, timestamptz, _text for text[])
public record QueryColumn(
    String name,
    String typeName
) {}
