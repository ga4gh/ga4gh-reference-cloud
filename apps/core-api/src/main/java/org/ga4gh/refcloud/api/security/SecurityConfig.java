package org.ga4gh.refcloud.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @Order(1) // Kratos Webhook security filter
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
    @Order(2) // rest of web app
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
