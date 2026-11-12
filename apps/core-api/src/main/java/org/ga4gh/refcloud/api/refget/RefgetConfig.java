package org.ga4gh.refcloud.api.refget;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfo;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoOrganization;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ga4gh.refcloud.refget")
public record RefgetConfig(Sequence sequence) {
    public record Sequence(RefgetSequenceServiceInfo serviceInfo) {
        public record RefgetSequenceServiceInfo(
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
                ServiceInfoRefgetCustom refget)
                implements ServiceInfo {
            public record ServiceInfoRefgetCustom(
                    @JsonProperty("circular_supported") boolean circularSupported,
                    @JsonProperty("subsequence_limit") Long subsequenceLimit,
                    @JsonProperty("algorithms") List<String> algorithms,
                    @JsonProperty("identifier_types") List<String> identifierTypes) {}
        }
    }
}
