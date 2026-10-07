package org.ga4gh.refcloud.api.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final OrySessionFilter orySessionFilter;

    private static final List<RequestMatcher> ORY_KRATOS_SESSION_ENDPOINTS = List.of(
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/datasets"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/datasets/{datasetId}"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/datasets/{datasetId}/request-access")
    );

    private static final List<RequestMatcher> PUBLIC_ENDPOINTS = List.of(
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.OPTIONS, "/ga4gh/drs/v1/objects/{id}"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/drs/v1/service-info")
    );

    private static final List<RequestMatcher> CUSTOM_SECURITY_ENDPOINTS = List.of(
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/datasets/{datasetId}/manifests"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/drs/v1/objects/{id}"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/ga4gh/drs/v1/objects/{id}"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.OPTIONS, "/ga4gh/drs/v1/objects"),
        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/ga4gh/drs/v1/objects")
    );

    public SecurityConfig(OrySessionFilter orySessionFilter) {
        this.orySessionFilter = orySessionFilter;
    }

    @Bean
    @Order(1) // Ory Kratos Webhook security filter
    public SecurityFilterChain basicAuthFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/webhook/kratos/**")
            .authorizeHttpRequests(auth -> auth
            .anyRequest().hasRole("KRATOS_WEBHOOK_USER")
        )
        .httpBasic(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    @Order(2) // Ory Kratos Session security filter (uses session token for logged in users)
    public SecurityFilterChain oryKratosSessionFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable standard CSRF/sessions since we are an API validated by Ory Kratos tokens
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityMatcher(new OrRequestMatcher(ORY_KRATOS_SESSION_ENDPOINTS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated() // endpoints that require kratos session token
            )
            .addFilterBefore(orySessionFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(3) // Public Endpoints
    public SecurityFilterChain publicEndpointsFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new OrRequestMatcher(PUBLIC_ENDPOINTS))
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.disable());

        return http.build();
    }

    @Bean
    @Order(4) // Endpoints that will be secured by "@PreAuthorize" annotation, indicating custom security method
    public SecurityFilterChain customSecurityEndpointsFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(new OrRequestMatcher(CUSTOM_SECURITY_ENDPOINTS))
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.disable());

        return http.build();
    }

    @Bean
    @Order(5) // Fallback: Ensure everything else requires a valid Ory Hydra token
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();
    }
}
