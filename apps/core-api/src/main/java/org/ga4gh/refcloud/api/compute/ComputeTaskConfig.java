package org.ga4gh.refcloud.api.compute;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// tes tasks (Task 6). reconcileInterval is also read by @Scheduled in TesTaskService
@Validated
@ConfigurationProperties(prefix = "ga4gh.refcloud.compute.tasks")
public record ComputeTaskConfig(
        // how often in-flight tasks are refreshed from the executor and queued tasks forwarded
        @NotNull Duration reconcileInterval,
        // each request to the executor's TES API
        @NotNull Duration executorTimeout,
        // GET /tasks page sizes
        @Positive int defaultPageSize,
        @Positive int maxPageSize) {}
