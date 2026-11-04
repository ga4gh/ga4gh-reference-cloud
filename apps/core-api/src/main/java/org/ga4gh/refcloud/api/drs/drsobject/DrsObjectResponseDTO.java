package org.ga4gh.refcloud.api.drs.drsobject;

import java.time.LocalDateTime;
import java.util.Set;
import org.ga4gh.refcloud.api.drs.accessmethod.AccessMethodResponseDTO;
import org.ga4gh.refcloud.api.drs.drsobjectchecksum.DrsObjectChecksumResponseDTO;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DrsObjectResponseDTO (
    String id,
    String name,
    String selfUri,
    Long size,
    LocalDateTime createdTime,
    LocalDateTime updatedTime,
    String version,
    String mimeType,
    Set<DrsObjectChecksumResponseDTO> checksums,
    Set<AccessMethodResponseDTO> accessMethods,
    String description,
    Set<String> aliases
) {}
