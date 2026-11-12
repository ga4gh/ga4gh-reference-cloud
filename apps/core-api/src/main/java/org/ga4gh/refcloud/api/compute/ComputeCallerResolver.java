package org.ga4gh.refcloud.api.compute;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

// turns the authenticated principal into a ComputeCaller. a hydra jwt is a researcher; run-scoped tokens (Task 7)
// add a run principal here
@Component
public class ComputeCallerResolver {

    public ComputeCaller resolve(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            return ComputeCaller.researcher(jwt.getSubject());
        }
        throw new AccessDeniedException("not authenticated for compute");
    }
}
