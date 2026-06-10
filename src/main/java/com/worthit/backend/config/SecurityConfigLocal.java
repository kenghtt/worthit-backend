package com.worthit.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Permissive security chain for local development / starter mode.
 *
 * Active when {@code security.auth.enabled=false} (the default in application.properties),
 * which is the case for this barebones starter so that the app boots without any JWT issuer configured.
 *
 * TODO(worthIt): once a real auth provider (e.g. Supabase / Auth0 / Cognito) is chosen, flip
 *  {@code security.auth.enabled=true} in the relevant profile and provide the JWKS / issuer values
 *  required by {@link SecurityConfig}.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "security.auth.enabled", havingValue = "false", matchIfMissing = true)
public class SecurityConfigLocal {

    @Bean
    public SecurityFilterChain securityFilterChainLocal(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}
