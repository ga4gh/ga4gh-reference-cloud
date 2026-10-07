package org.ga4gh.refcloud.api.drs.accessmethod;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessUrlResponseDTO(
    String url,
    List<String> headers
) {}
