package org.ga4gh.refcloud.api.drs.drsobjectchecksum;

public record DrsObjectChecksumResponseDTO(
    String checksum,
    DrsChecksumType type
) {}
