package org.ga4gh.refcloud.api.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
<<<<<<< HEAD
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
=======
>>>>>>> aac3d10 (secure DRS Object endpoint - not fully implemented)
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final OrySessionFilter orySessionFilter;

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
            .securityMatcher("/datasets/**")

            // Protect endpoints
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated() // endpoints that require kratos session token
            )
            
            // Add our custom Ory filter
            .addFilterBefore(orySessionFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(3) // rest of web app
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // public paths (no auth required)
                .requestMatchers("/**/service-info").permitAll()

                
                // Fallback: Ensure everything else requires a valid Ory Hydra token
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
        return http.build();

    }
    
}
