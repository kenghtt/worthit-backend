package com.worthit.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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

    @Value("${app.security.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

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

    /**
     * CORS config for the local/starter profile. Without this bean, Spring Security's
     * {@code .cors(...)} call has no source to read from and never emits
     * {@code Access-Control-Allow-Origin}, which causes browser preflight failures
     * from the React dev server (localhost:3000).
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // allowCredentials=false matches v1 (no cookies/auth header); UI must NOT use credentials:'include'.
        config.setAllowCredentials(false);
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.addAllowedHeader("*");
        config.addExposedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
