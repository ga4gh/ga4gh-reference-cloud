package org.ga4gh.refcloud.api.compute;

import org.ga4gh.refcloud.api.compute.run.RunTokenService;
import org.ga4gh.refcloud.api.compute.serviceinfo.ComputeServiceInfoService;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

// registers the platform availability filter just ahead of spring security's filter chain
@Configuration
public class ComputeFilterConfig {

    @Bean
    public FilterRegistrationBean<ComputePlatformAvailabilityFilter> computePlatformAvailabilityFilter(
            ComputeServiceInfoService computeServiceInfoService,
            ComputeHealthConfig healthConfig,
            ObjectMapper objectMapper,
            RunTokenService runTokenService) {
        FilterRegistrationBean<ComputePlatformAvailabilityFilter> registration =
                new FilterRegistrationBean<>(new ComputePlatformAvailabilityFilter(
                        computeServiceInfoService, healthConfig, objectMapper, runTokenService));
        registration.addUrlPatterns("/ga4gh/wes/*", "/ga4gh/tes/*");
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }
}
