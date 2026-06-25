package org.ga4gh.refcloud.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.userdetails.User;

@Configuration
public class SecureWebhookUserConfig {

    @Value("${ga4gh.refcloud.security.webhook.kratos.username}")
    private String kratosWebhookUsername;

    @Value("${ga4gh.refcloud.security.webhook.kratos.password}")
    private String kratosWebhookPassword;

    @Bean
    public UserDetailsService userDetailsService() {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        // Build the first user
        UserDetails kratosWebhookUser = User.withUsername(kratosWebhookUsername)
                .password(encoder.encode(kratosWebhookPassword))
                .roles("KRATOS_WEBHOOK_USER")
                .build();

        // Register both sets of credentials globally
        return new InMemoryUserDetailsManager(kratosWebhookUser);
    }
}
