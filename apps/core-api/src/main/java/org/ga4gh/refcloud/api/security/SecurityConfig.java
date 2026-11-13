package org.ga4gh.refcloud.api.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import org.ga4gh.refcloud.api.compute.run.RunTokenAuthenticationProvider;
import org.ga4gh.refcloud.api.compute.run.RunTokenService;
import org.ga4gh.refcloud.api.compute.run.RunTokens;
import org.ga4gh.refcloud.api.dataconnect.error.DataConnectAccessDeniedHandler;
import org.ga4gh.refcloud.api.dataconnect.error.DataConnectAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@SuppressFBWarnings(
        value = "SPRING_CSRF_PROTECTION_DISABLED",
        justification =
                "core-api never authenticates from cookies: Kratos sessions arrive in the X-Session-Token header,"
                        + " passports as Authorization: Bearer, and the Kratos webhook uses basic auth. Browsers don't attach"
                        + " these to cross-site requests, so CSRF tokens add nothing")
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final OrySessionFilter orySessionFilter;

    private final DataConnectAuthenticationEntryPoint dataConnectAuthenticationEntryPoint;

    private final DataConnectAccessDeniedHandler dataConnectAccessDeniedHandler;

    private static final RequestMatcher DATA_CONNECT_ENDPOINTS =
            PathPatternRequestMatcher.withDefaults().matcher("/ga4gh/data-connect/**");

    private static final List<RequestMatcher> ORY_KRATOS_SESSION_ENDPOINTS = List.of(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/datasets"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/datasets/{datasetId}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/datasets/{datasetId}/request-access"),
            // compute platforms for the researcher UI (Task 11), like the datasets above
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/compute-platforms"),
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.POST, "/compute-platforms/{platformId}/request-access"));

    private static final List<RequestMatcher> PUBLIC_ENDPOINTS = List.of(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.OPTIONS, "/ga4gh/drs/v1/objects/{id}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/drs/v1/service-info"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/refget/v2/sequence/service-info"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/data-connect/v1/service-info"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/service-registry/v1/service-info"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/service-registry/v1/services"),
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.GET, "/ga4gh/service-registry/v1/services/types"),
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.GET, "/ga4gh/service-registry/v1/services/{serviceId}"),
            // compute platforms' service-info (only enabled platforms; others 404)
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/wes/{platformId}/v1/service-info"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/tes/{platformId}/v1/service-info"),
            // kubernetes liveness/readiness probes; status only (no details), and never routed by the ingress
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/liveness"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/health/readiness"),
            PathPatternRequestMatcher.withDefaults()
                    .matcher("/error") // Spring Boot error page, otherwise errors raised via sendError (e.g. 406)
            // surface as 401 for anonymous requests
            );

    private static final List<RequestMatcher> CUSTOM_SECURITY_ENDPOINTS = List.of(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/datasets/{datasetId}/manifests"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/drs/v1/objects/{id}"),
            PathPatternRequestMatcher.withDefaults()
                    .matcher(
                            HttpMethod.GET,
                            "/ga4gh/drs/v1/objects/{id}/manifest-content"), // same access rule as GET /objects/{id}
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/ga4gh/drs/v1/objects/{id}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.OPTIONS, "/ga4gh/drs/v1/objects"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/ga4gh/drs/v1/objects"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/refget/v2/sequence/{id}"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/ga4gh/refget/v2/sequence/{id}/metadata"),
            PathPatternRequestMatcher.withDefaults()
                    .matcher(
                            HttpMethod.GET,
                            "/ga4gh/data-connect/v1/tables"), // open, but an optional token modifies which tables are
            // listed
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.GET, "/ga4gh/data-connect/v1/table/{table_name}/info"),
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.GET, "/ga4gh/data-connect/v1/table/{table_name}/data"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/ga4gh/data-connect/v1/search"),
            PathPatternRequestMatcher.withDefaults()
                    .matcher(HttpMethod.GET, "/ga4gh/data-connect/v1/search/{search_id}"));

    public SecurityConfig(
            OrySessionFilter orySessionFilter,
            DataConnectAuthenticationEntryPoint dataConnectAuthenticationEntryPoint,
            DataConnectAccessDeniedHandler dataConnectAccessDeniedHandler) {
        this.orySessionFilter = orySessionFilter;
        this.dataConnectAuthenticationEntryPoint = dataConnectAuthenticationEntryPoint;
        this.dataConnectAccessDeniedHandler = dataConnectAccessDeniedHandler;
    }

    @Bean
    @Order(1) // Ory Kratos Webhook security filter
    public SecurityFilterChain basicAuthFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/webhook/kratos/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("KRATOS_WEBHOOK_USER"))
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
                .authorizeHttpRequests(
                        auth -> auth.anyRequest().authenticated() // endpoints that require kratos session token
                        )
                .addFilterBefore(orySessionFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(3) // Public Endpoints
    public SecurityFilterChain publicEndpointsFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(new OrRequestMatcher(PUBLIC_ENDPOINTS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.disable());

        return http.build();
    }

    @Bean
    @Order(4) // Endpoints that will be secured by "@PreAuthorize" annotation, indicating custom security method
    public SecurityFilterChain customSecurityEndpointsFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(new OrRequestMatcher(CUSTOM_SECURITY_ENDPOINTS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                // Data Connect 401s get an ErrorResponse body; other APIs are unchanged
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> {}).authenticationEntryPoint(dataConnectAuthenticationEntryPoint))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.disable());

        return http.build();
    }

    @Bean
    @Order(5) // Compute TES: a run-scoped token (rcrun_..., an engine acting for a run) or a Hydra token (a researcher)
    public SecurityFilterChain computeTesFilterChain(
            HttpSecurity http, JwtDecoder jwtDecoder, RunTokenService runTokenService) throws Exception {
        // both managers have no parent, so neither falls back to another provider. the run-token provider exists only
        // here: run tokens are understood on these paths and nowhere else
        AuthenticationManager runTokens = new ProviderManager(new RunTokenAuthenticationProvider(runTokenService));
        AuthenticationManager jwts = new ProviderManager(new JwtAuthenticationProvider(jwtDecoder));
        // service-info is public (matched by the public chain first); everything else under a platform's TES
        http.securityMatcher(PathPatternRequestMatcher.withDefaults().matcher("/ga4gh/tes/{platformId}/v1/**"))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(
                        request -> RunTokens.fromAuthorizationHeader(request.getHeader(HttpHeaders.AUTHORIZATION))
                                        .isPresent()
                                ? runTokens
                                : jwts))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(6) // Compute WES (Task 8): researchers only (a Hydra token); run tokens are never accepted here
    public SecurityFilterChain computeWesFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        // service-info is public (matched by the public chain first); everything else under a platform's WES. a
        // stateless bearer-token API, so no CSRF (the fallback chain's CSRF check would refuse every POST /runs)
        http.securityMatcher(PathPatternRequestMatcher.withDefaults().matcher("/ga4gh/wes/{platformId}/v1/**"))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(7) // Fallback: Ensure everything else requires a valid Ory Hydra token
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> {}).authenticationEntryPoint(dataConnectAuthenticationEntryPoint))
                // Data Connect 403s (e.g. CSRF rejection of a POST to an unknown Data Connect path) get an
                // ErrorResponse body
                .exceptionHandling(exceptions -> exceptions.defaultAccessDeniedHandlerFor(
                        dataConnectAccessDeniedHandler, DATA_CONNECT_ENDPOINTS));
        return http.build();
    }
}
