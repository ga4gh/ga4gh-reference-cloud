package org.ga4gh.refcloud.api.dataconnect.query;

import java.util.List;

// a bind value to pass as a SQL array (e.g. varchar[]) rather than expanding into a list of values;
// elementTypeName is the Postgres element type (boolean, float8, varchar)
public record ArrayParameter(
    String elementTypeName,
    List<Object> elements
) {}
