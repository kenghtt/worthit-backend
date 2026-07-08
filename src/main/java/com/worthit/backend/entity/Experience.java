package com.worthit.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A single submitted compensation + culture review (see {@code database-spec.md} §8).
 */
@Entity
@Table(
        name = "experience",
        indexes = {
                @Index(name = "idx_experience_company", columnList = "company_id"),
                @Index(name = "idx_experience_company_role", columnList = "company_id, role_id"),
                @Index(name = "idx_experience_company_role_location", columnList = "company_id, role_id, location_id"),
                @Index(name = "idx_experience_location", columnList = "location_id"),
                @Index(name = "idx_experience_status", columnList = "status"),
                @Index(name = "idx_experience_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Experience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private Level level;

    @Column(name = "level_name", length = 80)
    private String levelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false, length = 20)
    private EmploymentStatus employmentStatus;

    @Column(name = "years_experience", nullable = false)
    private short yearsExperience;

    @Column(name = "years_at_company")
    private Short yearsAtCompany;

    @Column(name = "base_salary", nullable = false)
    private int baseSalary;

    @Column(nullable = false)
    @Builder.Default
    private int bonus = 0;

    @Column(nullable = false)
    @Builder.Default
    private int stock = 0;

    @Column(name = "signing_bonus", nullable = false)
    @Builder.Default
    private int signingBonus = 0;

    @Column(name = "compensation_year", nullable = false)
    private short compensationYear;

    @Column(name = "stress_level", nullable = false, precision = 3, scale = 1)
    private BigDecimal stressLevel;

    @Column(name = "hours_per_week")
    private Short hoursPerWeek;

    @Column(name = "worth_it_score", nullable = false, precision = 3, scale = 1)
    private BigDecimal worthItScore;

    @Column(name = "why_stay", columnDefinition = "TEXT")
    private String whyStay;

    @Column(name = "why_leave", columnDefinition = "TEXT")
    private String whyLeave;

    @Column(name = "wish_knew", columnDefinition = "TEXT")
    private String wishKnew;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExperienceStatus status = ExperienceStatus.pending;

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean active = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
