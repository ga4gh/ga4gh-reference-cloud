package org.ga4gh.refcloud.api.dataset;

import java.util.Set;

public record DatasetResponseDTO(
    String id,
    String name,
    String description,
    Set<String> tags
) {}