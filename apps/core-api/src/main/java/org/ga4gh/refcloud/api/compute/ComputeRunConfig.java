package org.ga4gh.refcloud.api.compute;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// workflow runs (Task 7). pollInterval is also read by @Scheduled in ComputeRunService
@Validated
@ConfigurationProperties(prefix = "ga4gh.refcloud.compute.runs")
public record ComputeRunConfig(
        // core-api's own internal base url, which engines call back on for TES (service to service, not through the
        // public ingress): http://localhost:8080 natively, http://refcloud-api:8080 in helm
        @NotNull URI engineCallbackUrl,
        // how often runs are checked, queued runs started, and overdue runs cancelled
        @NotNull Duration pollInterval,
        // after a cancel, how long an engine has to stop its tasks and exit before it is killed
        @NotNull Duration cancelGracePeriod,
        // how long a launch may take to report the engine's pid
        @NotNull Duration launchTimeout) {

    public String engineCallbackBase() {
        String url = engineCallbackUrl.toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
