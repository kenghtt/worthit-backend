package com.worthit.backend.repository;

import com.worthit.backend.entity.FeedbackSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackSubmissionRepository extends JpaRepository<FeedbackSubmission, Long> {

    boolean existsByReferenceNumber(String referenceNumber);
}
