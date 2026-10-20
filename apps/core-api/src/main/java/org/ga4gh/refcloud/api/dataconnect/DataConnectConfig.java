package org.ga4gh.refcloud.api.dataconnect;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ga4gh.refcloud.data-connect")
public record DataConnectConfig(
    String baseUrl,
    Query query,
    Datasource datasource,
    ServiceInfo serviceInfo
){
    public record Query(
        int maxLength,
        Duration statementTimeout,
        int pageSize
    ){}

    // connection settings for the restricted data_connect_reader role
    public record Datasource(
        String url,
        String username,
        String password,
        int maximumPoolSize
    ){}

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
