package com.trailersys.backend.conductor;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConductorRepository extends JpaRepository<Conductor, Long> {

    Optional<Conductor> findByIdentificacionIgnoreCase(String identificacion);

    boolean existsByIdentificacionIgnoreCase(String identificacion);

    @Query("""
            select c from Conductor c
            where (:search = '' or lower(c.nombres) like lower(concat('%', :search, '%'))
                   or lower(c.identificacion) like lower(concat('%', :search, '%'))
                   or lower(c.telefono) like lower(concat('%', :search, '%')))
              and (:estado is null or c.estado = :estado)
            """)
    Page<Conductor> buscar(@Param("search") String search,
                           @Param("estado") EstadoConductor estado,
                           Pageable pageable);
}
