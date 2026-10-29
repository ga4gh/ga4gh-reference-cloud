package org.ga4gh.refcloud.api.dataconnect.query;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

// Data Connect "Pagination" object
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaginationDTO(
    String nextPageUrl
) {}
