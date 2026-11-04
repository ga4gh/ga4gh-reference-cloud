package org.ga4gh.refcloud.api.dataconnect.table;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

// Data Connect "Table" object. description is optional in the spec and omitted when null
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DataConnectTableResponseDTO(
    String name,
    String description,
    JsonNode dataModel
) {}
