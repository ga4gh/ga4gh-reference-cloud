package org.ga4gh.refcloud.api.compute;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoOrganization;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// service-info fields shared by every compute platform's WES and TES services (deployment settings, so they live in
// application.yml, not the connections file). each service's id is {idPrefix}.{wes|tes}.{platformId}.{idSuffix};
// its name, description and timestamps come from the platform
@Validated
@ConfigurationProperties(prefix = "ga4gh.refcloud.compute.service-info")
public record ComputeServiceInfoConfig(
        @NotBlank String idPrefix,
        @NotBlank String idSuffix,
        @NotNull @Valid ServiceInfoOrganization organization,
        String contactUrl,
        String documentationUrl,
        // WES auth_instructions_url: how to request access to a compute platform and mint a passport token
        @NotBlank String authInstructionsUrl,
        String environment,
        String version) {

    public String serviceId(String api, String platformId) {
        return idPrefix + "." + api + "." + platformId + "." + idSuffix;
    }
}
