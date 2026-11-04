package org.ga4gh.refcloud.api.dataconnect.query;

import java.util.List;
import java.util.Map;

// rows are keyed by column name, with values already converted to Data Connect JSON types.
// hasMore is true when the query returned more rows than the requested limit
public record QueryResult(
    List<QueryColumn> columns,
    List<Map<String, Object>> rows,
    boolean hasMore
) {}
