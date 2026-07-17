package com.worthit.backend.repository;

import com.worthit.backend.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LevelRepository extends JpaRepository<Level, Long> {

    List<Level> findByCompany_IdOrderByNormalizedRankAsc(Long companyId);

    Optional<Level> findByCompany_IdAndName(Long companyId, String name);
}
