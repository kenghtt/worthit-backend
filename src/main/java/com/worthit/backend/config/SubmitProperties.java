package com.worthit.backend.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Tunable limits for the public experience submit flow.
 */
@Component
@ConfigurationProperties(prefix = "app.submit")
@Validated
@Getter
@Setter
public class SubmitProperties {

    @Min(1)
    private int worthItReasonMaxLength = 1000;
}