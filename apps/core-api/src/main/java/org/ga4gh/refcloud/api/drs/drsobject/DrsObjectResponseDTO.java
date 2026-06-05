package org.ga4gh.refcloud.api.drs.drsobject;

import java.time.LocalDateTime;

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
    // checksums
    // access methods
    String description
    // aliases
) {}
