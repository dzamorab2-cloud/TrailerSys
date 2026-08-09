package com.trailersys.backend.cliente;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByIdentificacionIgnoreCase(String identificacion);

    boolean existsByIdentificacionIgnoreCase(String identificacion);
}
