package com.worthit.backend.repository;

import com.worthit.backend.entity.CompanyRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRoleRepository extends JpaRepository<CompanyRole, Long> {

    boolean existsByCompany_IdAndRole_Id(Long companyId, Long roleId);
}
