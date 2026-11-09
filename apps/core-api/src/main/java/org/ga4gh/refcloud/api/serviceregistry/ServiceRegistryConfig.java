package org.ga4gh.refcloud.api.serviceregistry;

import org.ga4gh.refcloud.api.serviceinfo.StandardServiceInfo;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ga4gh.refcloud.service-registry")
public record ServiceRegistryConfig(String baseUrl, StandardServiceInfo serviceInfo) {
    public String baseUrlWithoutTrailingSlash() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
