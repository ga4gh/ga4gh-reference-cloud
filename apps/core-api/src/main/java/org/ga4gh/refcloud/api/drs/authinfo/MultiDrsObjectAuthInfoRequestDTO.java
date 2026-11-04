package org.ga4gh.refcloud.api.drs.authinfo;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MultiDrsObjectAuthInfoRequestDTO(

    @NotEmpty(message = "'bulk_object_ids' property cannot be null or empty")
    List<String> bulkObjectIds

) {}
