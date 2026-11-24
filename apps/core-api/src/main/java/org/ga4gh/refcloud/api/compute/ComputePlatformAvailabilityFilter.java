package org.ga4gh.refcloud.api.compute;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ga4gh.refcloud.api.compute.error.ComputeErrorResponse;
import org.ga4gh.refcloud.api.compute.platform.ComputePlatform;
import org.ga4gh.refcloud.api.compute.run.RunTokenService;
import org.ga4gh.refcloud.api.compute.run.RunTokens;
import org.ga4gh.refcloud.api.compute.serviceinfo.ComputeServiceInfoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

// the platform availability rule for every WES and TES request (D10, Task 5), so each compute endpoint inherits it:
//   unknown or disabled platform -> 404 (disabled platforms are not visible outside the admin CLI)
//   unhealthy platform -> 503 with Retry-After, except service-info, which reports the health
//   a disabled platform's WES per-run paths (/runs/{run_id} and below) go on to authentication, so a run's owner keeps
//   access to it (Task 8, option b); the health rule still applies to them
// runs before spring security (see ComputeFilterConfig): availability is not secret (health is public through
// service-info, and unknown and disabled platforms look the same), so any client learns that a platform is missing or
// down before being asked to authenticate. requests to a healthy platform then go through normal authentication
public class ComputePlatformAvailabilityFilter extends OncePerRequestFilter {

    // /ga4gh/{wes|tes}/{platformId}/v1[/...]
    private static final Pattern COMPUTE_PATH = Pattern.compile("^/ga4gh/(wes|tes)/([^/]+)/v1(/.*)?$");

    // a WES run and everything under it: /runs/{run_id}[/...]
    private static final Pattern WES_RUN_PATH = Pattern.compile("^/runs/[^/]+(/.*)?$");

    private final ComputeServiceInfoService computeServiceInfoService;

    private final ComputeHealthConfig healthConfig;

    private final ObjectMapper objectMapper;

    private final RunTokenService runTokenService;

    public ComputePlatformAvailabilityFilter(
            ComputeServiceInfoService computeServiceInfoService,
            ComputeHealthConfig healthConfig,
            ObjectMapper objectMapper,
            RunTokenService runTokenService) {
        this.computeServiceInfoService = computeServiceInfoService;
        this.healthConfig = healthConfig;
        this.objectMapper = objectMapper;
        this.runTokenService = runTokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !COMPUTE_PATH.matcher(pathWithinApplication(request)).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Matcher matcher = COMPUTE_PATH.matcher(pathWithinApplication(request));
        if (!matcher.matches()) {
            chain.doFilter(request, response);
            return;
        }
        String api = matcher.group(1);
        String platformId = matcher.group(2);
        String rest = matcher.group(3);

        // an engine calling TES for an active run on this platform always gets through: runs already active finish,
        // even if the platform is disabled (Task 7, option a) or unhealthy (the TES layer queues task creation and
        // serves reads from its own records while the executor is down). spring security still authenticates it
        if ("tes".equals(api) && isActiveRunOn(request, platformId)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<ComputePlatform> platform = computeServiceInfoService.findEnabledPlatform(platformId);
        if (platform.isEmpty()
                && "wes".equals(api)
                && rest != null
                && WES_RUN_PATH.matcher(rest).matches()) {
            // a disabled platform's runs stay readable and cancelable by their owners (Task 8, option b). ownership
            // needs authentication, so these paths go on to it, and the WES service serves a run only to its owner
            platform = computeServiceInfoService.findPlatform(platformId);
        }
        if (platform.isEmpty()) {
            write(response, HttpStatus.NOT_FOUND, "no compute platform with ID '" + platformId + "'");
            return;
        }
        if (!"/service-info".equals(rest)
                && !computeServiceInfoService.health(platform.get()).isHealthy()) {
            response.setHeader(
                    HttpHeaders.RETRY_AFTER,
                    String.valueOf(healthConfig.checkInterval().toSeconds()));
            write(
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "compute platform '" + platformId + "' is temporarily unavailable; see its service-info health");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isActiveRunOn(HttpServletRequest request, String platformId) {
        return RunTokens.fromAuthorizationHeader(request.getHeader(HttpHeaders.AUTHORIZATION))
                .flatMap(runTokenService::findActiveRun)
                .filter(run -> run.platformId().equals(platformId))
                .isPresent();
    }

    private void write(HttpServletResponse response, HttpStatus status, String msg) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ComputeErrorResponse(msg, status.value()));
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
