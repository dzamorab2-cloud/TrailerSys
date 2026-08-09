package com.trailersys.backend.conductor;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConductorRepository extends JpaRepository<Conductor, Long> {

    Optional<Conductor> findByIdentificacionIgnoreCase(String identificacion);

    boolean existsByIdentificacionIgnoreCase(String identificacion);
}
