package org.ga4gh.refcloud.api.compute.serviceinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.ga4gh.refcloud.api.compute.health.ComputeHealth;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfo;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoOrganization;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoType;

// a compute platform's TES 1.1 service-info, plus the refcloud's "health" extension property (D10). storage is left
// out: shared-filesystem paths are internal to the deployment
public record TesServiceInfo(
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
        @JsonProperty("tesResources_backend_parameters") List<String> tesResourcesBackendParameters,
        ComputeHealth health)
        implements ServiceInfo {}
