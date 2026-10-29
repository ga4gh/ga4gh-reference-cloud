package org.ga4gh.refcloud.api.passport.passportuservisaassertion;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PassportUserVisaAssertionService {

    private final PassportUserVisaAssertionRepository passportUserVisaAssertionRepository;

    public PassportUserVisaAssertionService (PassportUserVisaAssertionRepository passportUserVisaAssertionRepository) {
        this.passportUserVisaAssertionRepository = passportUserVisaAssertionRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PassportUserVisaAssertion> getAssertionByUserIdAndVisaId(String userId, String visaId) {
        return passportUserVisaAssertionRepository.findFirstByPassportUserIdAndPassportVisaId(userId, visaId);
    }
}
