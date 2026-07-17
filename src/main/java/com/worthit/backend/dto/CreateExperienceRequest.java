package com.worthit.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for creating an experience (see {@code api-endpoints.md} §4.1). Maps the UI's
 * multi-step submit form to the {@code experience} DB columns.
 *
 * <p>Field names are forced to camelCase via {@link JsonProperty} to match the UI contract,
 * overriding the global {@code snake_case} Jackson strategy. {@code @JsonIgnoreProperties} tolerates
 * the form's culture sliders ({@code autonomy}, {@code coding}, ...) that the UI sends but the
 * backend does not yet persist (the {@code experience} table has no culture columns), since the
 * global config enables {@code FAIL_ON_UNKNOWN_PROPERTIES}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateExperienceRequest(
        @JsonProperty("companySlug") String companySlug,
        @JsonProperty("company") String company,
        @JsonProperty("roleSlug") String roleSlug,
        @JsonProperty("role") String role,
        @JsonProperty("customRole") String customRole,
        @JsonProperty("level") String level,
        @JsonProperty("employmentStatus") @NotBlank String employmentStatus,
        @JsonProperty("city") @NotBlank String city,
        @JsonProperty("state") String state,
        @JsonProperty("yearsExperience") @NotNull @Min(0) Short yearsExperience,
        @JsonProperty("yearsAtCompany") @Min(0) Short yearsAtCompany,
        @JsonProperty("baseSalary") @NotNull @Min(0) Integer baseSalary,
        @JsonProperty("bonus") @Min(0) Integer bonus,
        @JsonProperty("stock") @Min(0) Integer stock,
        @JsonProperty("signingBonus") @Min(0) Integer signingBonus,
        @JsonProperty("compensationYear") @NotNull Short compensationYear,
        @JsonProperty("stressLevel") @NotNull @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal stressLevel,
        @JsonProperty("hoursPerWeek") @Min(0) Short hoursPerWeek,
        @JsonProperty("worthItScore") @NotNull @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal worthItScore,
        @JsonProperty("whyStay") String whyStay,
        @JsonProperty("whyLeave") String whyLeave,
        @JsonProperty("wishKnew") String wishKnew
) {

    /**
     * A company must be identifiable by either its slug or display name (see §4 validation:
     * {@code companySlug}/{@code company} required).
     */
    @AssertTrue(message = "either companySlug or company is required")
    public boolean isCompanyProvided() {
        return isPresent(companySlug) || isPresent(company);
    }

    /**
     * A role must be identifiable by its slug, display name, or the free-text custom role
     * (see §4 validation: {@code role}/{@code customRole} required).
     */
    @AssertTrue(message = "either role, roleSlug, or customRole is required")
    public boolean isRoleProvided() {
        return isPresent(roleSlug) || isPresent(role) || isPresent(customRole);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
