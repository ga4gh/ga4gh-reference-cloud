package org.ga4gh.refcloud.api.drs.accessmethod;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessMethodResponseDTO(
    AccessMethodType type,
    AccessUrlResponseDTO accessUrl,
    String cloud,
    String region,
    boolean available
) {}
