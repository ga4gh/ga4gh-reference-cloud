package org.ga4gh.refcloud.api.security;

import org.springframework.stereotype.Component;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectAuthInfoRequestDTO;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectRequestDTO;
import org.ga4gh.refcloud.api.drs.authinfo.SingleDrsObjectRequestDTO;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObjectService;
import org.ga4gh.refcloud.api.exception.ContentTooLargeException;
import org.ga4gh.refcloud.api.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@Component("GA4GHPassportTokenEvaluator")
public class GA4GHPassportTokenEvaluator {

    private JwtDecoder jwtDecoder;

    private final DrsObjectService drsObjectService;

    public GA4GHPassportTokenEvaluator(JwtDecoder jwtDecoder, DrsObjectService drsObjectService) {
        this.jwtDecoder = jwtDecoder;
        this.drsObjectService = drsObjectService;
    }

    public boolean canAccessDrsObject(Authentication authentication, String objectId) {
        // Sanity check the authentication context
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }

        String userId = jwt.getSubject();
        return drsObjectService.validateUserIsAuthorizedForDrsObject(userId, objectId);
    }

    public boolean canAccessDrsObject(Authentication authentication, String objectId, SingleDrsObjectRequestDTO requestDTO) {
        boolean authorized = false;

        for (String rawPassportToken : requestDTO.passports()) {
            try {
                Jwt passportJwt = jwtDecoder.decode(rawPassportToken);
                String userId = passportJwt.getSubject();
                if (drsObjectService.validateUserIsAuthorizedForDrsObject(userId, objectId) == true) { // any valid token (where user is authorized for the underlying dataset)
                    authorized = true;
                }
            } catch (JwtException e) { // any invalid token will automatically fail the entire authorization process
                throw new ForbiddenException("one or more invalid passport tokens detected");
            }
        }

        return authorized;
    }

    public boolean validateBulkAuthInfoRequest(Authentication authentication, MultiDrsObjectAuthInfoRequestDTO requestDTO) {
        if (!drsObjectService.bulkRequestWithinLimit(requestDTO.bulkObjectIds())) {
            throw new ContentTooLargeException("too many object ids submitted");
        }

        return true;
    }

    public boolean validateAllPassports(Authentication authentication, MultiDrsObjectRequestDTO requestDTO) {
        if (!drsObjectService.bulkRequestWithinLimit(requestDTO.bulkObjectIds())) {
            throw new ContentTooLargeException("too many object ids submitted");
        }

        for (String rawPassportToken : requestDTO.passports()) {
            try {
                jwtDecoder.decode(rawPassportToken); // decode all passport tokens, any invalid token will block the endpoint
            } catch (JwtException e) { // any invalid token will automatically fail the entire authorization process
                throw new ForbiddenException("one or more invalid passport tokens detected");
            }
        }

        return true;
    }
}
