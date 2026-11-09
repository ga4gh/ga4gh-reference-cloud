package org.ga4gh.refcloud.api.serviceregistry;

import com.fasterxml.jackson.annotation.JsonInclude;

// Service Registry "Error". status matches the response's HTTP status; title is fixed for each kind of error;
// detail (optional) is specific to this occurrence
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceRegistryErrorDTO(int status, String title, String detail) {}
