package org.ga4gh.refcloud.api.compute.run;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

// authenticates run-scoped TES tokens (rcrun_...). an unknown token, or one whose run is no longer active, is a 401.
//
// deliberately NOT a spring bean: spring security adds a lone AuthenticationProvider bean to the global
// AuthenticationManager, which every filter chain falls back to when its own provider fails. as a bean, a valid run
// token was accepted as an authenticated identity on DRS, WES and every other path (found in the Task 7 end-to-end
// check). SecurityConfig creates it for the compute TES chain only
public class RunTokenAuthenticationProvider implements AuthenticationProvider {

    private final RunTokenService runTokenService;

    public RunTokenAuthenticationProvider(RunTokenService runTokenService) {
        this.runTokenService = runTokenService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String token = ((BearerTokenAuthenticationToken) authentication).getToken();
        return runTokenService
                .findActiveRun(token)
                .map(RunTokenAuthentication::new)
                .orElseThrow(() -> new BadCredentialsException("invalid or expired run token"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
