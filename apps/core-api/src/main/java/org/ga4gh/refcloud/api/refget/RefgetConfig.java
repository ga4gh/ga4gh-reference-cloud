package org.ga4gh.refcloud.api.refget;

import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@ConfigurationProperties(prefix = "ga4gh.refcloud.refget")
public record RefgetConfig(
    Sequence sequence
){
    public record Sequence(
        ServiceInfo serviceInfo
    ){
        public record ServiceInfo(
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
            ServiceInfoRefgetCustom refget
        ){
            public record ServiceInfoType(
                String group,
                String artifact,
                String version
            ){}

            public record ServiceInfoOrganization(
                String name,
                String url
            ){}

            public record ServiceInfoRefgetCustom(
                @JsonProperty("circular_supported") boolean circularSupported,
                @JsonProperty("subsequence_limit") Long subsequenceLimit,
                @JsonProperty("algorithms") List<String> algorithms,
                @JsonProperty("identifier_types") List<String> identifierTypes
            ){}
        }
    }
}
