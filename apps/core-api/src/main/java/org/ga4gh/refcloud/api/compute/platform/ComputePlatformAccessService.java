package org.ga4gh.refcloud.api.compute.platform;

import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportVisaAssertionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// whether a researcher may use a compute platform: they need an approved request for the platform's visa (D5). every
// compute platform has a visa (no open-access compute)
@Service
@Transactional(readOnly = true)
public class ComputePlatformAccessService {

    private final ComputePlatformRepository computePlatformRepository;

    public ComputePlatformAccessService(ComputePlatformRepository computePlatformRepository) {
        this.computePlatformRepository = computePlatformRepository;
    }

    public boolean isApproved(String userId, String platformId) {
        if (userId == null) {
            return false;
        }
        return computePlatformRepository.hasVisaAssertion(userId, platformId, PassportVisaAssertionStatus.Approved);
    }
}
