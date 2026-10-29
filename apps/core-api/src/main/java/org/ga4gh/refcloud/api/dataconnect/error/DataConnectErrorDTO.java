package org.ga4gh.refcloud.api.dataconnect.error;

// Data Connect "Error". title is fixed for each kind of error; detail is specific to this occurrence.
// The spec's optional "source" is left out: it is only for errors from an attached data source,
// and every table here is served from the refcloud database
public record DataConnectErrorDTO(
    String title,
    String detail
) {}
