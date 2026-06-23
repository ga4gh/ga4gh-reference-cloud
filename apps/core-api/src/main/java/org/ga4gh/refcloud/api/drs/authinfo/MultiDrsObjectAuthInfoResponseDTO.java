package org.ga4gh.refcloud.api.drs.authinfo;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MultiDrsObjectAuthInfoResponseDTO(
    MultiDrsObjectAuthInfoSummaryResponseDTO summary,
    List<MultiDrsObjectAuthInfoUnresolvedIdSetResponseDTO> unresolvedDrsObjects,
    List<SingleDrsObjectAuthInfoResponseDTO> resolvedDrsObjects
) {}
