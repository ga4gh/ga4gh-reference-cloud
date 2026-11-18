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

    // how long past a run's max duration and cancel grace period its engine may still be running, as a backstop in case
    // core-api is down to stop it: a Kubernetes Job's deadline, and how long the run's storage credentials last
    public static final Duration ENGINE_DEADLINE_MARGIN = Duration.ofMinutes(5);

    // the longest a run's engine can be alive: its max duration, the cancel grace period, and the margin. its Job's
    // deadline (spec 0011) and its storage credentials (spec 0012) both last this long
    public Duration engineLifetime(int maxDurationSeconds) {
        return Duration.ofSeconds(maxDurationSeconds).plus(cancelGracePeriod).plus(ENGINE_DEADLINE_MARGIN);
    }

    public String engineCallbackBase() {
        String url = engineCallbackUrl.toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
