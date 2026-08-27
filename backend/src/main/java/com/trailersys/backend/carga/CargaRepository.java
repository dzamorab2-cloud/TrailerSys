package com.trailersys.backend.carga;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CargaRepository extends JpaRepository<Carga, Long> {

    @Query("""
            select c from Carga c
            where (:search = '' or lower(c.descripcion) like lower(concat('%', :search, '%'))
                   or lower(c.origen) like lower(concat('%', :search, '%'))
                   or lower(c.destino) like lower(concat('%', :search, '%'))
                   or lower(c.cliente.nombre) like lower(concat('%', :search, '%')))
              and (:estado is null or c.estado = :estado)
            """)
    Page<Carga> buscar(@Param("search") String search,
                       @Param("estado") EstadoCarga estado,
                       Pageable pageable);
}
