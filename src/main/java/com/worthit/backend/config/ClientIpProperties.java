package com.worthit.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.client-ip")
@Getter
@Setter
public class ClientIpProperties {

    private boolean trustForwardedHeaders;
}