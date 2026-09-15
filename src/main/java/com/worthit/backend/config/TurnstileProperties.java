package com.worthit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Cloudflare Turnstile server-side verification settings.
 */
@Component
@ConfigurationProperties(prefix = "app.turnstile")
@Getter
@Setter
public class TurnstileProperties {

    private String secretKey;
    private String verifyUrl;
    private List<String> allowedHostnames = List.of();
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
}