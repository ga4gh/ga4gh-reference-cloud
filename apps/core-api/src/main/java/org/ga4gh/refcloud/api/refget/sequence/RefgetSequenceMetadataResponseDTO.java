package org.ga4gh.refcloud.api.refget.sequence;

// refget v2 wraps the metadata fields in a top-level "metadata" object
public record RefgetSequenceMetadataResponseDTO (
    RefgetSequenceMetadataDTO metadata
) {}
