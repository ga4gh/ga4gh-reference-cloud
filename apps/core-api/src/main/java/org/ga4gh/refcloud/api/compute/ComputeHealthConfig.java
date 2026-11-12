package org.ga4gh.refcloud.api.compute;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// connection health checks (D10). checkInterval is also read by @Scheduled in ComputeHealthService
@Validated
@ConfigurationProperties(prefix = "ga4gh.refcloud.compute.health")
public record ComputeHealthConfig(
        // time between the end of one round of checks and the start of the next
        @NotNull Duration checkInterval,
        // the executor's TES service-info request
        @NotNull Duration executorTimeout,
        // `nextflow -version`; the first run downloads the pinned nextflow version, so allow for that
        @NotNull Duration engineTimeout) {}
