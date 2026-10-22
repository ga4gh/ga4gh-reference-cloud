package org.ga4gh.refcloud.api.dataconnect.query;

import java.util.List;
import java.util.Map;

// SQL ready to run against Postgres, with named bind parameters (:name) and their values.
// tableNames lists the Data Connect tables the query reads; columnSchemaRefs holds the
// ga4gh_type() $ref url for each annotated result column, keyed by column name
public record RewrittenQuery(
    String sql,
    Map<String, Object> parameters,
    List<String> tableNames,
    Map<String, String> columnSchemaRefs
) {}
