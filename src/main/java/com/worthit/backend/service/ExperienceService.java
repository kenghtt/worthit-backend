package com.worthit.backend.service;

import com.worthit.backend.dto.CreateExperienceRequest;
import com.worthit.backend.dto.ExperienceSummary;
import com.worthit.backend.entity.Company;
import com.worthit.backend.entity.CompanyRole;
import com.worthit.backend.entity.EmploymentStatus;
import com.worthit.backend.entity.Experience;
import com.worthit.backend.entity.ExperienceStatus;
import com.worthit.backend.entity.Level;
import com.worthit.backend.entity.Location;
import com.worthit.backend.entity.Role;
import com.worthit.backend.repository.CompanyRepository;
import com.worthit.backend.repository.CompanyRoleRepository;
import com.worthit.backend.repository.ExperienceRepository;
import com.worthit.backend.repository.LevelRepository;
import com.worthit.backend.repository.LocationRepository;
import com.worthit.backend.repository.RoleRepository;
import com.worthit.backend.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Write-side logic for the submit-experience endpoint (see {@code api-endpoints.md} §4.1).
 *
 * <p>Resolves the submitted company / role / location, creating any that don't yet exist
 * (find-or-create, mirroring the seeder's upsert-by-slug approach in {@code DataSeeder}). Since new
 * experiences are persisted as {@code pending} (see {@code database-spec.md} §9), they — and any
 * companies/roles created alongside them — stay out of the public read endpoints until a published
 * experience exists. Records the experience and returns it in the §2.4 shape.</p>
 */
@Service
@RequiredArgsConstructor
public class ExperienceService {

    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final LocationRepository locationRepository;
    private final LevelRepository levelRepository;
    private final CompanyRoleRepository companyRoleRepository;
    private final ExperienceRepository experienceRepository;

    /**
     * Creates a new {@code pending} experience from the submit form (see {@code api-endpoints.md}
     * §4.1). Bean Validation on {@link CreateExperienceRequest} has already run; this method handles
     * the remaining domain mapping and entity resolution.
     *
     * @throws IllegalArgumentException if {@code employmentStatus} is not {@code current}/{@code former}
     *                                  (surfaced as {@code 400} via {@code GlobalExceptionHandler})
     */
    @Transactional
    public ExperienceSummary createExperience(CreateExperienceRequest req) {
        Company company = resolveCompany(req);
        Role role = resolveRole(req);
        linkCompanyRoleIfMissing(company, role);
        Location location = resolveLocation(req);

        Level level = (req.level() == null || req.level().isBlank()) ? null
                : levelRepository.findByCompany_IdAndName(company.getId(), req.level().trim()).orElse(null);

        Experience experience = Experience.builder()
                .company(company)
                .role(role)
                .location(location)
                .level(level)
                .levelName(blankToNull(req.level()))
                .employmentStatus(mapEmploymentStatus(req.employmentStatus()))
                .yearsExperience(req.yearsExperience())
                .yearsAtCompany(req.yearsAtCompany())
                .baseSalary(req.baseSalary())
                .bonus(zeroIfNull(req.bonus()))
                .stock(zeroIfNull(req.stock()))
                .signingBonus(zeroIfNull(req.signingBonus()))
                .compensationYear(req.compensationYear())
                .stressLevel(req.stressLevel())
                .hoursPerWeek(req.hoursPerWeek())
                .worthItScore(req.worthItScore())
                .whyStay(blankToNull(req.whyStay()))
                .whyLeave(blankToNull(req.whyLeave()))
                .wishKnew(blankToNull(req.wishKnew()))
                .status(ExperienceStatus.pending)
                .build();

        return ExperienceSummary.from(experienceRepository.save(experience));
    }

    /**
     * Resolves the company by {@code companySlug} (or the slugified display name), creating an
     * active company if none exists yet.
     */
    private Company resolveCompany(CreateExperienceRequest req) {
        String slug = isPresent(req.companySlug()) ? req.companySlug().trim()
                : SlugUtil.slugify(req.company());
        String displayName = isPresent(req.company()) ? req.company().trim() : slug;
        return companyRepository.findBySlug(slug)
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .slug(slug)
                        .name(displayName)
                        .active(true)
                        .build()));
    }

    /**
     * Resolves the role by {@code roleSlug} (or the slugified display name / custom role), creating
     * an active global role if none exists yet. {@code customRole} takes precedence as the display
     * name when the user typed a role not in the list.
     */
    private Role resolveRole(CreateExperienceRequest req) {
        String displayName = isPresent(req.customRole()) ? req.customRole().trim()
                : (isPresent(req.role()) ? req.role().trim() : null);
        String slug = isPresent(req.roleSlug()) ? req.roleSlug().trim()
                : SlugUtil.slugify(displayName);
        String name = displayName != null ? displayName : slug;
        return roleRepository.findBySlug(slug)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .slug(slug)
                        .name(name)
                        .active(true)
                        .build()));
    }

    /** Ensures the role is offered at the company (see {@code database-spec.md} §5). */
    private void linkCompanyRoleIfMissing(Company company, Role role) {
        if (!companyRoleRepository.existsByCompany_IdAndRole_Id(company.getId(), role.getId())) {
            companyRoleRepository.save(CompanyRole.builder()
                    .company(company)
                    .role(role)
                    .active(true)
                    .build());
        }
    }

    /**
     * Resolves the location by {@code city}/{@code state}, creating an active location if none
     * exists yet. The slug matches the seeder's {@code slugify(city + " " + state)} convention.
     */
    private Location resolveLocation(CreateExperienceRequest req) {
        String city = req.city().trim();
        String state = isPresent(req.state()) ? req.state().trim() : "";
        return locationRepository.findByCityAndState(city, state)
                .orElseGet(() -> locationRepository.save(Location.builder()
                        .slug(SlugUtil.slugify(city + " " + state))
                        .city(city)
                        .state(state)
                        .active(true)
                        .build()));
    }

    /**
     * Maps the API contract's {@code employmentStatus} ({@code current}/{@code former}) to the DB
     * enum ({@code current}/{@code past}); note {@code former} maps to {@link EmploymentStatus#past}.
     */
    private static EmploymentStatus mapEmploymentStatus(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "current" -> EmploymentStatus.current;
            case "former", "past" -> EmploymentStatus.past;
            default -> throw new IllegalArgumentException(
                    "employmentStatus must be one of: current, former");
        };
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return isPresent(value) ? value.trim() : null;
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
