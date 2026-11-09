package org.ga4gh.refcloud.api.dataconnect;

import java.time.Duration;
import org.ga4gh.refcloud.api.serviceinfo.StandardServiceInfo;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "ga4gh.refcloud.data-connect")
public record DataConnectConfig(String baseUrl, Query query, Datasource datasource, StandardServiceInfo serviceInfo) {
    public String baseUrlWithoutTrailingSlash() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public record Query(int maxLength, Duration statementTimeout, int pageSize, Search search) {
        // limits for /search results saved so later pages can be served without re-running the query
        public record Search(int maxRows, DataSize maxBytes, Duration ttl, DataSize storageMaxBytes) {}
    }

    // connection settings for the restricted data_connect_reader role
    public record Datasource(String url, String username, String password, int maximumPoolSize) {}
}
