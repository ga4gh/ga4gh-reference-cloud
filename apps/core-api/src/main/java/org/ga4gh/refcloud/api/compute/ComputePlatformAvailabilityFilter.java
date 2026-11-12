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
import org.ga4gh.refcloud.api.compute.serviceinfo.ComputeServiceInfoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

// the platform availability rule for every WES and TES request (D10, Task 5), so each compute endpoint inherits it:
//   unknown or disabled platform -> 404 (disabled platforms are not visible outside the admin CLI)
//   unhealthy platform -> 503 with Retry-After, except service-info, which reports the health
// runs before spring security (see ComputeFilterConfig): availability is not secret (health is public through
// service-info, and unknown and disabled platforms look the same), so any client learns that a platform is missing or
// down before being asked to authenticate. requests to a healthy platform then go through normal authentication
public class ComputePlatformAvailabilityFilter extends OncePerRequestFilter {

    // /ga4gh/{wes|tes}/{platformId}/v1[/...]
    private static final Pattern COMPUTE_PATH = Pattern.compile("^/ga4gh/(wes|tes)/([^/]+)/v1(/.*)?$");

    private final ComputeServiceInfoService computeServiceInfoService;

    private final ComputeHealthConfig healthConfig;

    private final ObjectMapper objectMapper;

    public ComputePlatformAvailabilityFilter(
            ComputeServiceInfoService computeServiceInfoService,
            ComputeHealthConfig healthConfig,
            ObjectMapper objectMapper) {
        this.computeServiceInfoService = computeServiceInfoService;
        this.healthConfig = healthConfig;
        this.objectMapper = objectMapper;
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
        String platformId = matcher.group(2);
        String rest = matcher.group(3);

        Optional<ComputePlatform> platform = computeServiceInfoService.findEnabledPlatform(platformId);
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

    private void write(HttpServletResponse response, HttpStatus status, String msg) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ComputeErrorResponse(msg, status.value()));
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
