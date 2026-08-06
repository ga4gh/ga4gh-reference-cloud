package org.ga4gh.refcloud.api.security;

import org.springframework.stereotype.Component;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObjectService;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertion;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportUserVisaAssertionService;
import org.ga4gh.refcloud.api.passport.passportuservisaassertion.PassportVisaAssertionStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Optional;

@Component("GA4GHPassportTokenEvaluator")
public class GA4GHPassportTokenEvaluator {

    private final DrsObjectService drsObjectService;

    private final PassportUserVisaAssertionService passportUserVisaAssertionService;

    public GA4GHPassportTokenEvaluator(DrsObjectService drsObjectService, PassportUserVisaAssertionService passportUserVisaAssertionService) {
        this.drsObjectService = drsObjectService;
        this.passportUserVisaAssertionService = passportUserVisaAssertionService;
    }

    public boolean canAccessDrsObject(Authentication authentication, String objectId) {
        // 1. Sanity check the authentication context
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }

        // 2. Extract user identity and the visa associated with DRS Object ID
        String userId = jwt.getSubject();
        String visaId = drsObjectService.getVisaIdByDrsObjectId(objectId);

        Optional<PassportUserVisaAssertion> optionalAssertion = passportUserVisaAssertionService.getAssertionByUserIdAndVisaId(userId, visaId);
        if (optionalAssertion.isPresent()) {
            PassportUserVisaAssertion assertion = optionalAssertion.get();
            if (assertion.getCurrentStatus() == PassportVisaAssertionStatus.Approved) {
                return true; // if status is "Approved" allow user to view the object
            }
        }
        
        return false; // do not allow user to view the object if no record found in assertion table, or if status is anything other than "Approved"
    }
}
