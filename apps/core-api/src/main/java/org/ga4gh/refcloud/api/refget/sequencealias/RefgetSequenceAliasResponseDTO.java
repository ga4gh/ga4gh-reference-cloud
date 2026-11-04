package org.ga4gh.refcloud.api.refget.sequencealias;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RefgetSequenceAliasResponseDTO (
    String alias,
    String namingAuthority
) {}
