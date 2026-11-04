package org.ga4gh.refcloud.api.dataconnect.search;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

// Data Connect "SearchRequest" object: SQL with zero or more positional (?) parameters.
// parameters may be omitted when the query has no ? placeholders
public record SearchRequestDTO(
    @NotBlank String query,
    List<JsonNode> parameters
) {}
