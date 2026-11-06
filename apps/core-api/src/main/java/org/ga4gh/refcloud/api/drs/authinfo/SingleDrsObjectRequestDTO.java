package org.ga4gh.refcloud.api.drs.authinfo;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SingleDrsObjectRequestDTO(
        @NotEmpty(message = "'passports' property cannot be null or empty")
        List<String> passports) {}
