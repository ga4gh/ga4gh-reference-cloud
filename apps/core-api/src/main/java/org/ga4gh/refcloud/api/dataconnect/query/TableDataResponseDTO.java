package org.ga4gh.refcloud.api.dataconnect.query;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

// Data Connect "TableData" object, returned by /table/{table_name}/data and /search.
// pagination is omitted on the last page; the spec's optional errors field is not yet implemented
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TableDataResponseDTO(
    JsonNode dataModel,
    List<Map<String, Object>> data,
    PaginationDTO pagination
) {}
