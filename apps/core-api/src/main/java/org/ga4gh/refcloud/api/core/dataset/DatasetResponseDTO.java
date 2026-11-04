package org.ga4gh.refcloud.api.core.dataset;

import java.util.Set;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisaResponseDTO;

public record DatasetResponseDTO(
    String id,
    String name,
    String description,
    Set<String> tags,
    PassportVisaResponseDTO visa
) {}