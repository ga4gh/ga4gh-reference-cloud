package org.ga4gh.refcloud.api.drs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ga4gh.refcloud.drs")
public record DrsConfig(
    String hostDomain
){}
