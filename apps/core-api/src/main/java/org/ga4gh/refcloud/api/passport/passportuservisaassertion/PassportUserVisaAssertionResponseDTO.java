package org.ga4gh.refcloud.api.passport.passportuservisaassertion;

import java.time.Instant;

public record PassportUserVisaAssertionResponseDTO(
        String userId, PassportVisaAssertionStatus currentStatus, Instant currentStatusAt) {}
