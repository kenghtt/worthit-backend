package com.worthit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.posthog")
@Getter
@Setter
public class PostHogProperties {

    private String projectToken;
    private String host;
}