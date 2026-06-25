package org.ga4gh.refcloud.api.security;

import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;

@Component("GA4GHPassportTokenEvaluator")
public class GA4GHPassportTokenEvaluator {
    public boolean canAccessObject(Authentication authentication, String objectId) {
        System.out.println("***");
        System.out.println("You have hit the GA4GHPassportTokenEvaluator method");
        System.out.println(authentication);
        System.out.println(objectId);
        System.out.println("***");
        
        // 1. Sanity check the authentication context
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }

        // 2. Extract user identity and incoming GA4GH visas from Ory Hydra
        String userId = jwt.getSubject();
        List<String> incomingVisas = jwt.getClaimAsStringList("ga4gh_visas_v1");

        // 3. Database Check Logic
        // Example logic:
        // a) Look up what visas or metadata are required to access objectId in your database.
        // b) Compare those requirements against the incomingVisas or the user's database permissions.
        
        return verifyDatabaseAccessRules(userId, incomingVisas, objectId);
    }

    private boolean verifyDatabaseAccessRules(String userId, List<String> visas, String objectId) {
        // Implement your specific cross-reference logic here
        // e.g., SELECT COUNT(*) FROM user_visas WHERE user_id = :userId AND object_id = :objectId
        return "123".equals(objectId); // Placeholder rule: only '123' passes for this example
    }
}
