package org.ga4gh.refcloud.api.webhook.kratos;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record KratosRegistrationPayload(
    String id
) {}
