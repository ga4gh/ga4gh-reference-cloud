package org.ga4gh.refcloud.api.core.dataset;

import java.time.LocalDateTime;
import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestResponseDTO(
    String id,
    String name,
    Long size,
    LocalDateTime created,
    LocalDateTime updated,
    String version,
    String mimeType,
    String description,
    Boolean isManifest,
    Map<String, Object> manifestContent
) {}
