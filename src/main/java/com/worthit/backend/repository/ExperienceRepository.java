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
