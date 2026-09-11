package com.worthit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}