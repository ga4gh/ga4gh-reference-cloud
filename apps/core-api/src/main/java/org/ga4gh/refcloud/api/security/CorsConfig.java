package org.ga4gh.refcloud.api.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConfigurationProperties(prefix = "ga4gh.refcloud.security.cors")
public class CorsConfig {
    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private List<String> exposedHeaders;
    private Boolean allowCredentials;
    private Long maxAge;

    // Getters and Setters are required for binding
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }
    public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }
    public void setExposedHeaders(List<String> exposedHeaders) { this.exposedHeaders = exposedHeaders; }
    public void setAllowCredentials(Boolean allowCredentials) { this.allowCredentials = allowCredentials; }
    public void setMaxAge(Long maxAge) { this.maxAge = maxAge; }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(allowedOrigins.toArray(new String[0]))
                        .allowedMethods(allowedMethods.toArray(new String[0]))
                        .allowedHeaders(allowedHeaders.toArray(new String[0]))
                        .exposedHeaders(exposedHeaders.toArray(new String[0]))
                        .allowCredentials(allowCredentials)
                        .maxAge(maxAge);
            }
        };
    }
}