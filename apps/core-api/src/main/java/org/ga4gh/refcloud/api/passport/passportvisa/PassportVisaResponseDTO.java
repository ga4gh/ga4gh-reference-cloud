package org.ga4gh.refcloud.api.passport.passportvisa;

import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionResponseDTO;

public record PassportVisaResponseDTO(
    String id,
    String name,
    String description,
    PassportUserVisaAssertionResponseDTO assertion
) {}
