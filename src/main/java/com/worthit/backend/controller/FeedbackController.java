package com.worthit.backend.controller;

import com.worthit.backend.dto.CreateFeedbackRequest;
import com.worthit.backend.dto.FeedbackSubmissionResponse;
import com.worthit.backend.service.FeedbackRateLimiter;
import com.worthit.backend.service.FeedbackService;
import com.worthit.backend.service.ClientIpResolver;
import com.worthit.backend.service.SubmissionAnalyticsService;
import com.worthit.backend.service.TurnstileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
@Slf4j
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final FeedbackRateLimiter feedbackRateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final SubmissionAnalyticsService submissionAnalyticsService;
    private final TurnstileService turnstileService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackSubmissionResponse createFeedback(@Valid @RequestBody CreateFeedbackRequest request,
                                                     HttpServletRequest httpRequest) {
        String remoteIp = clientIpResolver.resolve(httpRequest);
        turnstileService.verifySubmissionToken(
                request.turnstileToken(), remoteIp, TurnstileService.FEEDBACK_ACTION);
        Instant rateLimitReservation = feedbackRateLimiter.checkAndRecordSubmission(remoteIp);
        FeedbackSubmissionResponse response;
        try {
            response = feedbackService.createFeedback(request);
        } catch (RuntimeException ex) {
            feedbackRateLimiter.rollBackSubmission(remoteIp, rateLimitReservation);
            throw ex;
        }
        submissionAnalyticsService.captureFeedbackSuccess(
                httpRequest,
                request.category(),
                request.email() != null && !request.email().isBlank(),
                request.message().trim().length()
        );
        log.info("Feedback submitted. referenceNumber={} category={}", response.referenceNumber(), request.category());
        return response;
    }
}
