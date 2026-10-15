package org.ga4gh.refcloud.api.dataconnect;

import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ga4gh.refcloud.data-connect")
public record DataConnectConfig(
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
        String version
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
    }
}
