package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.worthit.backend.entity.FeedbackCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFeedbackRequest(
        @JsonProperty("category") @NotNull FeedbackCategory category,
        @JsonProperty("email") @Email @Size(max = 254) String email,
        @JsonProperty("message") @NotBlank @Size(max = 500) String message,
        @JsonProperty("turnstileToken") @NotBlank String turnstileToken
) {
}
