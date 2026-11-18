package org.ga4gh.refcloud.api.compute;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// WES (Task 8). preparationSweepInterval is also read by @Scheduled in RunPreparationService
@Validated
@ConfigurationProperties(prefix = "ga4gh.refcloud.compute.wes")
public record ComputeWesConfig(
        // the directory bundled workflows' paths (e.g. compute/workflows/nextflow/drs-checksum) resolve from: the repo
        // root locally (../.. from apps/core-api); in an image, wherever the workflows are copied
        @NotBlank String bundledWorkflowsBaseDir,
        // how often queued runs that aren't prepared yet (e.g. after a restart) are picked up
        @NotNull Duration preparationSweepInterval,
        // fetching a pinned commit
        @NotNull Duration gitTimeout,
        // downloading one DRS input
        @NotNull Duration downloadTimeout,
        // GET /runs and GET /runs/{run_id}/tasks page sizes
        @Positive int defaultPageSize,
        @Positive int maxPageSize) {}
