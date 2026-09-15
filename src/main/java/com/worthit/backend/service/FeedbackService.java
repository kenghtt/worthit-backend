package com.worthit.backend.service;

import com.worthit.backend.dto.CreateFeedbackRequest;
import com.worthit.backend.dto.FeedbackSubmissionResponse;
import com.worthit.backend.entity.FeedbackSubmission;
import com.worthit.backend.repository.FeedbackSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final String REFERENCE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int REFERENCE_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FeedbackSubmissionRepository feedbackSubmissionRepository;

    @Transactional
    public FeedbackSubmissionResponse createFeedback(CreateFeedbackRequest request) {
        String referenceNumber = generateUniqueReferenceNumber();
        FeedbackSubmission submission = FeedbackSubmission.builder()
                .referenceNumber(referenceNumber)
                .category(request.category())
                .email(blankToNull(request.email()))
                .message(request.message().trim())
                .build();

        feedbackSubmissionRepository.save(submission);
        return new FeedbackSubmissionResponse(referenceNumber);
    }

    private String generateUniqueReferenceNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder reference = new StringBuilder("FB-");
            for (int i = 0; i < REFERENCE_LENGTH; i++) {
                reference.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
            }

            String referenceNumber = reference.toString();
            if (!feedbackSubmissionRepository.existsByReferenceNumber(referenceNumber)) {
                return referenceNumber;
            }
        }
        throw new IllegalStateException("Unable to generate a unique feedback reference number");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
