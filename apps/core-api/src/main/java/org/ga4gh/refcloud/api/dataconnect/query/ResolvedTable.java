package org.ga4gh.refcloud.api.dataconnect.query;

// a Data Connect table the requesting user is allowed to query.
// datasetId is null for platform-wide tables
public record ResolvedTable(
    String tableName,
    String datasetId,
    String internalQuery
) {}
