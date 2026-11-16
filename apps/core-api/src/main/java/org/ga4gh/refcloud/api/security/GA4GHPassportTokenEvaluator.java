package org.ga4gh.refcloud.api.security;

import org.ga4gh.refcloud.api.compute.platform.ComputePlatformAccessService;
import org.ga4gh.refcloud.api.compute.run.RunPrincipal;
import org.ga4gh.refcloud.api.core.dataset.DatasetService;
import org.ga4gh.refcloud.api.dataconnect.table.DataConnectTableService;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectAuthInfoRequestDTO;
import org.ga4gh.refcloud.api.drs.authinfo.MultiDrsObjectRequestDTO;
import org.ga4gh.refcloud.api.drs.authinfo.SingleDrsObjectRequestDTO;
import org.ga4gh.refcloud.api.drs.drsobject.DrsObjectService;
import org.ga4gh.refcloud.api.exception.ContentTooLargeException;
import org.ga4gh.refcloud.api.exception.ForbiddenException;
import org.ga4gh.refcloud.api.refget.sequence.RefgetSequenceService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component("GA4GHPassportTokenEvaluator")
public class GA4GHPassportTokenEvaluator {

    private JwtDecoder jwtDecoder;

    private final DrsObjectService drsObjectService;

    private final DatasetService datasetService;

    private final RefgetSequenceService refgetSequenceService;

    private final DataConnectTableService dataConnectTableService;

    private final ComputePlatformAccessService computePlatformAccessService;

    public GA4GHPassportTokenEvaluator(
            JwtDecoder jwtDecoder,
            DrsObjectService drsObjectService,
            DatasetService datasetService,
            RefgetSequenceService refgetSequenceService,
            DataConnectTableService dataConnectTableService,
            ComputePlatformAccessService computePlatformAccessService) {
        this.jwtDecoder = jwtDecoder;
        this.drsObjectService = drsObjectService;
        this.datasetService = datasetService;
        this.refgetSequenceService = refgetSequenceService;
        this.dataConnectTableService = dataConnectTableService;
        this.computePlatformAccessService = computePlatformAccessService;
    }

    // compute platforms are never open: a researcher needs an approved request for the platform's visa. a run
    // token (Task 7) is accepted for its own run's platform only
    public boolean canAccessComputePlatform(Authentication authentication, String platformId) {
        if (authentication != null && authentication.getPrincipal() instanceof RunPrincipal run) {
            return run.platformId().equals(platformId);
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return computePlatformAccessService.isApproved(jwt.getSubject(), platformId);
        }
        return false;
    }

    // anonymous requests are allowed through, but can only access open datasets
    public boolean canAccessDataset(Authentication authentication, String datasetId) {
        String userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            userId = jwt.getSubject();
        }

        return datasetService.validateUserIsAuthorizedForDataset(userId, datasetId);
    }

    // anonymous requests are allowed through, but can only access objects in open datasets
    public boolean canAccessDrsObject(Authentication authentication, String objectId) {
        String userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            userId = jwt.getSubject();
        }

        return drsObjectService.validateUserIsAuthorizedForDrsObject(userId, objectId);
    }

    // anonymous requests are allowed through, but can only access sequences in open datasets
    public boolean canAccessRefgetSequence(Authentication authentication, String id) {
        String userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            userId = jwt.getSubject();
        }

        return refgetSequenceService.validateUserIsAuthorizedForSequence(userId, id);
    }

    // anonymous requests are allowed through, but can only access platform-wide tables and tables in open datasets.
    // inaccessible tables raise the same 404 as missing tables (rather than 401/403), so restricted tables cannot be
    // discovered
    public boolean canAccessDataConnectTable(Authentication authentication, String tableName) {
        String userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            userId = jwt.getSubject();
        }

        if (!dataConnectTableService.validateUserIsAuthorizedForTable(userId, tableName)) {
            throw DataConnectTableService.tableNotFound(tableName);
        }

        return true;
    }

    public boolean canAccessDrsObject(
            Authentication authentication, String objectId, SingleDrsObjectRequestDTO requestDTO) {
        boolean authorized = false;

        for (String rawPassportToken : requestDTO.passports()) {
            try {
                Jwt passportJwt = jwtDecoder.decode(rawPassportToken);
                String userId = passportJwt.getSubject();
                if (drsObjectService.validateUserIsAuthorizedForDrsObject(
                        userId, objectId)) { // any valid token (where user is authorized for the underlying dataset)
                    authorized = true;
                }
            } catch (JwtException e) { // any invalid token will automatically fail the entire authorization process
                throw new ForbiddenException("one or more invalid passport tokens detected");
            }
        }

        return authorized;
    }

    public boolean validateBulkAuthInfoRequest(
            Authentication authentication, MultiDrsObjectAuthInfoRequestDTO requestDTO) {
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
                jwtDecoder.decode(
                        rawPassportToken); // decode all passport tokens, any invalid token will block the endpoint
            } catch (JwtException e) { // any invalid token will automatically fail the entire authorization process
                throw new ForbiddenException("one or more invalid passport tokens detected");
            }
        }

        return true;
    }
}
