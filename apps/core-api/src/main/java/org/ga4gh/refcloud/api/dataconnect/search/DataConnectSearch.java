package org.ga4gh.refcloud.api.dataconnect.search;

import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

// a saved, unexpired search. userId is the subject that ran it, or null for an anonymous search
public record DataConnectSearch(
    UUID id,
    String userId,
    JsonNode dataModel,
    List<String> tableNames
) {}
