package org.ga4gh.refcloud.api.compute.serviceinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.ga4gh.refcloud.api.compute.health.ComputeHealth;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfo;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoOrganization;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoType;

// a compute platform's WES 1.1.0 service-info: the base service-info fields, the WES fields (snake_case, as the WES
// spec names them), and the refcloud's "health" extension property (D10)
public record WesServiceInfo(
        String id,
        String name,
        ServiceInfoType type,
        String description,
        ServiceInfoOrganization organization,
        String contactUrl,
        String documentationUrl,
        Instant createdAt,
        Instant updatedAt,
        String environment,
        String version,
        @JsonProperty("workflow_type_versions") Map<String, WorkflowTypeVersion> workflowTypeVersions,
        @JsonProperty("supported_wes_versions") List<String> supportedWesVersions,
        @JsonProperty("supported_filesystem_protocols") List<String> supportedFilesystemProtocols,
        @JsonProperty("workflow_engine_versions") Map<String, WorkflowEngineVersion> workflowEngineVersions,
        @JsonProperty("default_workflow_engine_parameters") List<Map<String, String>> defaultWorkflowEngineParameters,
        @JsonProperty("system_state_counts") Map<String, Long> systemStateCounts,
        @JsonProperty("auth_instructions_url") String authInstructionsUrl,
        Map<String, String> tags,
        ComputeHealth health)
        implements ServiceInfo {

    public record WorkflowTypeVersion(
            @JsonProperty("workflow_type_version") List<String> workflowTypeVersion) {}

    public record WorkflowEngineVersion(
            @JsonProperty("workflow_engine_version") List<String> workflowEngineVersion) {}
}
