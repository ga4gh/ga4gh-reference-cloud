package org.ga4gh.refcloud.api.compute;

import org.ga4gh.refcloud.api.compute.run.RunPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

// turns the authenticated principal into a ComputeCaller: a hydra jwt is a researcher, a run-scoped token (Task 7) is
// a run acting for its user
@Component
public class ComputeCallerResolver {

    public ComputeCaller resolve(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof RunPrincipal run) {
            return ComputeCaller.run(run.userId(), run.runId(), run.runDirectoryPath());
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            return ComputeCaller.researcher(jwt.getSubject());
        }
        throw new AccessDeniedException("not authenticated for compute");
    }
}
