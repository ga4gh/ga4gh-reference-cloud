package org.ga4gh.refcloud.api.compute.run;

import java.io.Serial;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

// an authenticated run-scoped token. it carries no granted authorities: what a run may do is decided per request
// (its own platform, its own tasks, its own run directory)
public final class RunTokenAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final RunPrincipal principal;

    public RunTokenAuthentication(RunPrincipal principal) {
        super(List.of());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public RunPrincipal getPrincipal() {
        return principal;
    }

    // the token itself is never kept
    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public String getName() {
        return principal.userId();
    }
}
