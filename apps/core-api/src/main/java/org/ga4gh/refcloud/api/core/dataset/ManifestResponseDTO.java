package org.ga4gh.refcloud.api.core.dataset;

import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ManifestResponseDTO(
        String id,
        String name,
        Long size,
        Instant created,
        Instant updated,
        String version,
        String mimeType,
        String description,
        Boolean isManifest,
        Map<String, Object> manifestContent) {}
