package org.ga4gh.refcloud.api.passport.passportuservisaassertion;

import java.time.LocalDateTime;

public record PassportUserVisaAssertionResponseDTO(
    String userId,
    PassportVisaAssertionStatus currentStatus,
    LocalDateTime currentStatusAt
) {}
