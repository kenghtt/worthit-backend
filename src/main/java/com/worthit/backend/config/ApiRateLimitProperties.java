package com.worthit.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
@Getter
@Setter
public class ApiRateLimitProperties {

    private boolean enabled = true;

    @Min(1)
    private int maxTrackedBuckets = 50_000;

    @Valid
    @NotNull
    private Limit global = new Limit(300, Duration.ofMinutes(1));

    @Valid
    @NotNull
    private Limit search = new Limit(60, Duration.ofMinutes(1));

    @Valid
    @NotNull
    private Limit read = new Limit(120, Duration.ofMinutes(1));

    @Valid
    @NotNull
    private Limit submissionAttempts = new Limit(10, Duration.ofMinutes(10));

    @Valid
    @NotNull
    private Limit successfulExperienceSubmissions = new Limit(5, Duration.ofHours(1));

    @Valid
    @NotNull
    private Limit probes = new Limit(60, Duration.ofMinutes(1));

    @Getter
    @Setter
    public static class Limit {

        @Min(1)
        private int maxRequests;

        @NotNull
        private Duration window;

        public Limit() {
        }

        public Limit(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.window = window;
        }

        @AssertTrue(message = "rate-limit window must be greater than zero")
        public boolean isWindowPositive() {
            return window != null && !window.isZero() && !window.isNegative();
        }
    }
}