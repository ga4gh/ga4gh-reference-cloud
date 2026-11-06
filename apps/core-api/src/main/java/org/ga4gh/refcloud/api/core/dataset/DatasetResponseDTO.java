package org.ga4gh.refcloud.api.core.dataset;

import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisaResponseDTO;

// tables and hasManifests are only populated by the read endpoints (GET /datasets, GET /datasets/{id})
// and are omitted elsewhere. tables lists only the Data Connect tables the caller can read
public record DatasetResponseDTO(
    String id,
    String name,
    String description,
    Set<String> tags,
    PassportVisaResponseDTO visa,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<DatasetTableResponseDTO> tables,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean hasManifests
) {}
