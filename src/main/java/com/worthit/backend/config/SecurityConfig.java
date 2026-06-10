package com.worthit.backend.config;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT-protected security chain for non-local environments.
 *
 * <p>Active only when {@code security.auth.enabled=true}. The default starter profile keeps it
 * disabled so the app boots without any external auth dependency — see {@link SecurityConfigLocal}.</p>
 *
 * <p>Supports either:
 * <ul>
 *     <li>JWKS-based asymmetric validation when {@code security.jwt.jwks-uri} is set, or</li>
 *     <li>HS256 shared-secret validation when {@code security.jwt.secret} is set (legacy/dev fallback).</li>
 * </ul>
 * </p>
 *
 * <p>TODO(worthIt): plug in the chosen auth provider's issuer/JWKS, decide on an audience/role policy,
 *  and add any custom JWT claim validators (e.g. approved email list, tenant id) required by worthIt.</p>
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(value = "security.auth.enabled", havingValue = "true")
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${security.jwt.issuer:}")
    private String issuer;

    @Value("${security.jwt.jwks-uri:}")
    private String jwksUri;

    @Value("${security.jwt.expected-audience:}")
    private String expectedAudience;

    @Value("${security.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.security.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                                .policyDirectives("default-src 'self'; script-src 'self'; object-src 'none';"))
                )
                .authorizeHttpRequests(authz -> authz
                        // Public probes — keep these open even when auth is enabled.
                        .requestMatchers("/api/hello", "/actuator/health").permitAll()
                        // TODO(worthIt): add public endpoints (e.g. /api/auth/**) here as needed.
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> {
                    oauth.jwt(Customizer.withDefaults());
                    oauth.authenticationEntryPoint((request, response, authException) -> {
                        log.warn("Unauthorized request to {} {}", request.getMethod(), request.getRequestURI());
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                    });
                    oauth.accessDeniedHandler((request, response, accessDeniedException) -> {
                        log.warn("Forbidden request to {} {}", request.getMethod(), request.getRequestURI());
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                    });
                });

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(allowedOrigins);
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedHeader("*");
        config.addExposedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder;

        if (jwksUri != null && !jwksUri.trim().isEmpty()) {
            log.info("Configuring JWT decoder with JWKS URI: {}", jwksUri);
            decoder = NimbusJwtDecoder
                    .withJwkSetUri(jwksUri)
                    // TODO(worthIt): adjust algorithm to match the chosen auth provider (ES256 / RS256 / etc.).
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
        } else {
            if (jwtSecret == null || jwtSecret.isEmpty()) {
                throw new IllegalStateException(
                        "security.auth.enabled=true but neither security.jwt.jwks-uri nor security.jwt.secret is configured.");
            }
            if (jwtSecret.length() < 32) {
                throw new IllegalStateException("security.jwt.secret is too weak. Use at least 32 characters.");
            }
            log.info("Configuring JWT decoder with HS256 shared secret (legacy/dev mode)");
            SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            decoder = NimbusJwtDecoder
                    .withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();
        }

        OAuth2TokenValidator<Jwt> issuerValidator = (issuer != null && !issuer.isBlank())
                ? JwtValidators.createDefaultWithIssuer(issuer)
                : JwtValidators.createDefault();

        OAuth2TokenValidator<Jwt> audienceValidator = (Jwt token) -> {
            if (expectedAudience == null || expectedAudience.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            List<String> aud = token.getAudience();
            if (aud != null && aud.contains(expectedAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid audience", null));
        };

        // TODO(worthIt): add additional validators here (e.g. approved emails, role/tenant claims).

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }
}
