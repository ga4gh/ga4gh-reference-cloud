package org.ga4gh.refcloud.api.compute.platform;

import java.math.BigDecimal;
import java.util.List;
import org.ga4gh.refcloud.api.compute.health.ComputeHealth;
import org.ga4gh.refcloud.api.passport.passportvisa.PassportVisaResponseDTO;

// a compute platform as the researcher UI's "Browse Compute Platforms" page shows it (Task 11): what it is, its health
// (the same shape as in its service-info, D10), where its WES and TES are, what it may run and with which limits, and
// the caller's access request for its visa. every platform has a visa, so visa is never null
public record ComputePlatformResponseDTO(
        String id,
        String name,
        String description,
        // the connection's platform type, e.g. nextflow-funnel-docker; null when its connection isn't configured
        String platformType,
        ComputeHealth health,
        String wesUrl,
        String tesUrl,
        Limits limits,
        List<Workflow> workflows,
        PassportVisaResponseDTO visa) {

    // what one user may use on the platform (D11). per-task resources and the run duration are the per-user limit or
    // the connection's ceiling, whichever is lower. concurrent runs and monthly core-hours are null when the platform
    // sets no per-user limit (only the connection's pooled limits apply)
    public record Limits(
            Integer maxConcurrentRuns,
            Integer maxTaskCpuCores,
            BigDecimal maxTaskMemoryGb,
            BigDecimal maxTaskDiskGb,
            Integer maxRunDurationSeconds,
            BigDecimal monthlyCoreHours) {}

    // an allowlisted workflow: a WES submission's workflow_url must match workflowUrl exactly (D20)
    public record Workflow(
            String name, String description, String workflowUrl, String workflowType, String workflowTypeVersion) {}
}
