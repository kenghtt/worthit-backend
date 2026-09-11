package com.worthit.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.worthit.backend.config.TurnstileProperties;
import com.worthit.backend.exception.TurnstileConfigurationException;
import com.worthit.backend.exception.TurnstileVerificationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class TurnstileService {

    private static final String EXPIRED_OR_DUPLICATE = "timeout-or-duplicate";
    private static final String MISSING_RESPONSE = "missing-input-response";
    private static final String INVALID_RESPONSE = "invalid-input-response";

    private final RestClient restClient;
    private final TurnstileProperties turnstileProperties;

    public TurnstileService(RestClient.Builder restClientBuilder, TurnstileProperties turnstileProperties) {
        this.restClient = restClientBuilder.build();
        this.turnstileProperties = turnstileProperties;
    }

    public void verifySubmissionToken(String token, String remoteIp) {
        if (!StringUtils.hasText(token)) {
            throw new TurnstileVerificationException("Complete the verification before submitting.");
        }

        if (!StringUtils.hasText(turnstileProperties.getSecretKey())) {
            log.error("Turnstile secret key is not configured.");
            throw new TurnstileConfigurationException("Turnstile verification is not configured.");
        }

        LinkedMultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
        requestBody.add("secret", turnstileProperties.getSecretKey());
        requestBody.add("response", token);
        requestBody.add("idempotency_key", UUID.randomUUID().toString());
        if (StringUtils.hasText(remoteIp)) {
            requestBody.add("remoteip", remoteIp);
        }

        TurnstileSiteVerifyResponse response;
        try {
            response = restClient.post()
                    .uri(turnstileProperties.getVerifyUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(requestBody)
                    .retrieve()
                    .body(TurnstileSiteVerifyResponse.class);
        } catch (RestClientException ex) {
            log.error("Turnstile siteverify request failed.", ex);
            throw new TurnstileConfigurationException("Turnstile verification is temporarily unavailable.");
        }

        if (response == null || !response.success()) {
            List<String> errorCodes = response == null ? List.of() : response.errorCodes();
            log.warn("Turnstile verification failed. remoteIp={} hostname={} errors={}",
                    remoteIp, response == null ? null : response.hostname(), errorCodes);
            throw new TurnstileVerificationException(mapFailureMessage(errorCodes));
        }

        log.debug("Turnstile verification passed. remoteIp={} hostname={}", remoteIp, response.hostname());
    }

    private String mapFailureMessage(List<String> errorCodes) {
        if (errorCodes.contains(EXPIRED_OR_DUPLICATE)) {
            return "Verification expired. Please complete it again.";
        }

        if (errorCodes.contains(MISSING_RESPONSE) || errorCodes.contains(INVALID_RESPONSE)) {
            return "Complete the verification before submitting.";
        }

        return "Verification failed. Please try again.";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TurnstileSiteVerifyResponse(
            boolean success,
            String hostname,
            @JsonProperty("error-codes") List<String> errorCodes
    ) {
        private TurnstileSiteVerifyResponse {
            errorCodes = errorCodes == null ? List.of() : errorCodes;
        }
    }
}