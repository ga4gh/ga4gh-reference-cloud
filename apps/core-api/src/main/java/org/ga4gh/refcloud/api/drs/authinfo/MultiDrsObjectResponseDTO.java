package org.ga4gh.refcloud.api.drs.authinfo;

import java.util.List;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObjectResponseDTO;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MultiDrsObjectResponseDTO(
    MultiDrsObjectAuthInfoSummaryResponseDTO summary,
    List<MultiDrsObjectAuthInfoUnresolvedIdSetResponseDTO> unresolvedDrsObjects,
    List<DrsObjectResponseDTO> resolvedDrsObjects
) {}
