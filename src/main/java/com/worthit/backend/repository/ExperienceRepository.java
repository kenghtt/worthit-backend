package com.worthit.backend.repository;

import com.worthit.backend.entity.Experience;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    long countByCompany_Id(Long companyId);

    /**
     * All active experiences for a company (see {@code api-endpoints.md} §2.3).
     * Loaded so per-role aggregates — including the salary average — can be computed in memory.
     */
    List<Experience> findByCompany_IdAndActive(Long companyId, boolean active);

    /**
     * Active experiences for a company + role (see {@code api-endpoints.md} §2.4),
     * with the company / role / location / level relations eagerly fetched to avoid N+1 when
     * the result is mapped to the response shape.
     */
    @Query("""
            select e from Experience e
            join fetch e.company c
            join fetch e.role r
            join fetch e.location l
            left join fetch e.level lv
            where c.id = :companyId and r.id = :roleId and e.active = :active
            """)
    List<Experience> findForCompanyRole(@Param("companyId") Long companyId,
                                        @Param("roleId") Long roleId,
                                        @Param("active") boolean active);

    /**
     * Aggregate stats for active experiences matching a company + role + submitted level label.
     */
    @Query("""
            select count(e) as experienceCount,
                   avg(e.worthItScore) as avgWorthScore,
                   avg(e.stressLevel) as avgStress,
                   avg(e.baseSalary + e.bonus + e.stock + e.signingBonus) as avgTotalComp
            from Experience e
            left join e.level lv
            where e.company.id = :companyId
              and e.role.id = :roleId
              and e.active = true
              and lower(coalesce(e.levelName, lv.name)) = :levelName
            """)
    ExperienceStatsProjection aggregateForCompanyRoleAndLevel(@Param("companyId") Long companyId,
                                                              @Param("roleId") Long roleId,
                                                              @Param("levelName") String levelName);

    /**
     * Aggregate stats for active experiences matching a company + role + submitted level label +
     * location city name.
     */
    @Query("""
            select count(e) as experienceCount,
                   avg(e.worthItScore) as avgWorthScore,
                   avg(e.stressLevel) as avgStress,
                   avg(e.baseSalary + e.bonus + e.stock + e.signingBonus) as avgTotalComp
            from Experience e
            join e.location l
            left join e.level lv
            where e.company.id = :companyId
              and e.role.id = :roleId
              and e.active = true
              and lower(coalesce(e.levelName, lv.name)) = :levelName
              and lower(l.city) = :locationCity
            """)
    ExperienceStatsProjection aggregateForCompanyRoleAndLevelAndLocation(@Param("companyId") Long companyId,
                                                                         @Param("roleId") Long roleId,
                                                                         @Param("levelName") String levelName,
                                                                         @Param("locationCity") String locationCity);

    /**
     * Active experiences in a location (see {@code api-endpoints.md} §3.3), with the
     * company eagerly fetched so per-company stats scoped to the city can be computed in memory
     * without an N+1.
     */
    @Query("""
            select e from Experience e
            join fetch e.company c
            where e.location.id = :locationId and e.active = :active
            """)
    List<Experience> findForLocation(@Param("locationId") Long locationId,
                                     @Param("active") boolean active);

    /**
     * Per-company aggregate stats over active experiences (see
     * {@code database-spec.md} §10). Companies with no matching experiences are not returned.
     */
    @Query("""
            select e.company.id as companyId,
                   count(e) as experienceCount,
                   count(distinct e.role.id) as roleCount,
                   avg(e.worthItScore) as avgWorthScore,
                   avg(e.stressLevel) as avgStress,
                   avg(e.hoursPerWeek) as avgHoursPerWeek,
                   avg(e.baseSalary + e.bonus + e.stock + e.signingBonus) as avgTotalComp
            from Experience e
            where e.active = :active
            group by e.company.id
            """)
    List<CompanyStatsProjection> aggregateByCompany(@Param("active") boolean active);

    /**
     * Per-location aggregate stats over active experiences (see
     * {@code api-endpoints.md} §3.1). Locations with no matching experiences are not returned.
     */
    @Query("""
            select e.location.id as locationId,
                   count(e) as experienceCount,
                   count(distinct e.company.id) as companyCount,
                   avg(e.worthItScore) as avgWorthScore,
                   avg(e.stressLevel) as avgStress,
                   avg(e.baseSalary + e.bonus + e.stock + e.signingBonus) as avgTotalComp
            from Experience e
            where e.active = :active
            group by e.location.id
            """)
    List<LocationStatsProjection> aggregateByLocation(@Param("active") boolean active);
}
