package org.ga4gh.refcloud.api.dataconnect.query;

import java.util.Map;

// SQL ready to run against Postgres, with named bind parameters (:name) and their values
public record RewrittenQuery(
    String sql,
    Map<String, Object> parameters
) {}
