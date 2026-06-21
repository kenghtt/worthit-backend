package com.worthit.backend.repository;

import com.worthit.backend.entity.Experience;
import com.worthit.backend.entity.ExperienceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {

    long countByCompany_Id(Long companyId);

    /**
     * All experiences for a company in the given status (see {@code api-endpoints.md} §2.3).
     * Loaded so per-role aggregates — including the salary average — can be computed in memory.
     */
    List<Experience> findByCompany_IdAndStatus(Long companyId, ExperienceStatus status);

    /**
     * Experiences for a company + role in the given status (see {@code api-endpoints.md} §2.4),
     * with the company / role / location / level relations eagerly fetched to avoid N+1 when
     * the result is mapped to the response shape.
     */
    @Query("""
            select e from Experience e
            join fetch e.company c
            join fetch e.role r
            join fetch e.location l
            left join fetch e.level lv
            where c.id = :companyId and r.id = :roleId and e.status = :status
            """)
    List<Experience> findForCompanyRole(@Param("companyId") Long companyId,
                                        @Param("roleId") Long roleId,
                                        @Param("status") ExperienceStatus status);

    /**
     * Per-company aggregate stats over experiences in the given status (see
     * {@code database-spec.md} §10). Companies with no matching experiences are not returned.
     */
    @Query("""
            select e.company.id as companyId,
                   count(e) as experienceCount,
                   count(distinct e.role.id) as roleCount,
                   avg(e.worthItScore) as avgWorthScore,
                   avg(e.stressLevel) as avgStress
            from Experience e
            where e.status = :status
            group by e.company.id
            """)
    List<CompanyStatsProjection> aggregateByCompany(@Param("status") ExperienceStatus status);
}
