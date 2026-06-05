package org.ga4gh.refcloud.api.drs.drsobject;

import java.time.LocalDateTime;

public record DrsObjectResponseDTO (
    String id,
    String description,
    LocalDateTime createdTime,
    String mimeType,
    String name,
    Long size,
    LocalDateTime updatedTime,
    String version
) {}
