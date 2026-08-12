package org.ga4gh.refcloud.api.drs.authinfo;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SingleDrsObjectAuthInfoResponseDTO(
    String drsObjectId,
    List<SupportedType> supportedTypes,
    List<String> passportAuthIssuers,
    List<String> bearerAuthIssuers
) {}
