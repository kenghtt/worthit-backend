package com.worthit.backend.repository;

import com.worthit.backend.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findBySlug(String slug);

    Optional<Location> findByCityAndState(String city, String state);
}
