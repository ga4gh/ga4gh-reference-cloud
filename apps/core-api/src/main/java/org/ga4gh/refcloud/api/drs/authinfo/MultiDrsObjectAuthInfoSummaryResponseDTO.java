package org.ga4gh.refcloud.api.drs.authinfo;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MultiDrsObjectAuthInfoSummaryResponseDTO(
    Integer requested,
    Integer resolved,
    Integer unresolved
) {}
