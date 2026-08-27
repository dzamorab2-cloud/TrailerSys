package com.trailersys.backend.cliente;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByIdentificacionIgnoreCase(String identificacion);

    boolean existsByIdentificacionIgnoreCase(String identificacion);

    @Query("""
            select c from Cliente c
            where (:search = '' or lower(c.nombre) like lower(concat('%', :search, '%'))
                   or lower(c.identificacion) like lower(concat('%', :search, '%'))
                   or lower(c.telefono) like lower(concat('%', :search, '%')))
              and (:estado is null or c.estado = :estado)
            """)
    Page<Cliente> buscar(@Param("search") String search,
                         @Param("estado") EstadoCliente estado,
                         Pageable pageable);
}
