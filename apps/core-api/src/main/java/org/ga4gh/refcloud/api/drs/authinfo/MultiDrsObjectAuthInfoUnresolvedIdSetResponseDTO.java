package org.ga4gh.refcloud.api.drs.authinfo;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MultiDrsObjectAuthInfoUnresolvedIdSetResponseDTO(
    Integer errorCode,
    List<String> objectIds
) {}
