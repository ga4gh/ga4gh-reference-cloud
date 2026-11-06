package org.ga4gh.refcloud.api.core.dataset;

// a Data Connect table belonging to a dataset. type is the table type id (e.g. drs_object)
public record DatasetTableResponseDTO(
    String name,
    String type
) {}
