package org.ga4gh.refcloud.api.dataconnect.query;

// position of the next page in a /table/{table_name}/data pagination sequence
public record PageToken(
    long offset
) {}
