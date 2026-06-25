package org.ga4gh.refcloud.api.drs.accessmethod;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AccessMethodResponseDTO(
    AccessMethodType type,
    String accessUrl,
    String cloud,
    String region,
    boolean available
) {}
