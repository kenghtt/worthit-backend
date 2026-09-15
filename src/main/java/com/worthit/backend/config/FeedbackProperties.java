package com.worthit.backend.config;

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
@ConfigurationProperties(prefix = "app.feedback.rate-limit")
@Validated
@Getter
@Setter
public class FeedbackProperties {

    @Min(1)
    private int maxSubmissions = 5;

    @NotNull
    private Duration window = Duration.ofHours(1);

    @AssertTrue(message = "feedback rate-limit window must be greater than zero")
    public boolean isWindowPositive() {
        return window != null && !window.isZero() && !window.isNegative();
    }
}
