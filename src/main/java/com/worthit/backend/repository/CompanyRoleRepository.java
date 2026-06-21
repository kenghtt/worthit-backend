package com.worthit.backend.repository;

import com.worthit.backend.entity.CompanyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompanyRoleRepository extends JpaRepository<CompanyRole, Long> {

    boolean existsByCompany_IdAndRole_Id(Long companyId, Long roleId);

    /**
     * Active role links for a company with the {@link com.worthit.backend.entity.Role} eagerly
     * fetched (drives the role list in {@code api-endpoints.md} §2.3, avoiding an N+1 on role).
     */
    @Query("""
            select cr from CompanyRole cr
            join fetch cr.role r
            where cr.company.id = :companyId and cr.active = true
            """)
    List<CompanyRole> findActiveWithRoleByCompanyId(@Param("companyId") Long companyId);
}
