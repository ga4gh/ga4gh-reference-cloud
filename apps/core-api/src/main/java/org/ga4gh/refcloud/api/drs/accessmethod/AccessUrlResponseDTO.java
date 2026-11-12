package org.ga4gh.refcloud.api.drs.accessmethod;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessUrlResponseDTO(String url, List<String> headers) {}
