package org.ga4gh.refcloud.api.drs;

import java.time.Instant;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfo;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoOrganization;
import org.ga4gh.refcloud.api.serviceinfo.ServiceInfoType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ga4gh.refcloud.drs")
public record DrsConfig(String scheme, String hostDomain, DrsServiceInfo serviceInfo) {
    public record DrsServiceInfo(
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
            ServiceInfoDrsCustom drs)
            implements ServiceInfo {
        public record ServiceInfoDrsCustom(Long maxBulkLengthRequest) {}
    }
}
