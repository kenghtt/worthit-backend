package com.worthit.backend.config;

import com.worthit.backend.filter.ApiRateLimitFilter;
import com.worthit.backend.filter.RequestBodySizeFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.security.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            ApiRateLimitFilter apiRateLimitFilter,
                                            RequestBodySizeFilter requestBodySizeFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'none'; frame-ancestors 'none';"))
                        .referrerPolicy(referrer -> referrer
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy("camera=(), geolocation=(), microphone=()"))
                )
                .authorizeHttpRequests(authz -> authz
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/api/hello", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/companies", "/api/v1/companies/search",
                                "/api/v1/companies/*", "/api/v1/companies/*/roles", "/api/v1/companies/*/levels",
                                "/api/v1/locations", "/api/v1/locations/*", "/api/v1/locations/*/companies",
                                "/api/v1/roles", "/api/v1/experiences",
                                "/api/v1/experiences/filter-options", "/api/v1/experiences/stats").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/feedback", "/api/v1/experiences").permitAll()
                        .anyRequest().denyAll()
                )
                .addFilterAfter(apiRateLimitFilter, CorsFilter.class)
                .addFilterAfter(requestBodySizeFilter, ApiRateLimitFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(false);
        config.setAllowedOrigins(normalizeOrigins(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Accept",
                "Content-Type",
                "X-WorthIt-Submission-Id",
                "X-PostHog-Distinct-Id"
        ));
        config.setExposedHeaders(List.of("Retry-After"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static List<String> normalizeOrigins(List<String> configuredOrigins) {
        if (configuredOrigins == null) {
            return List.of();
        }
        return configuredOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .flatMap(origin -> Arrays.stream(origin.split(",")))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
    }

}
