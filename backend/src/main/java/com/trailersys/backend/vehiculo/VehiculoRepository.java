package com.trailersys.backend.vehiculo;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    Optional<Vehiculo> findByPlacaIgnoreCase(String placa);

    boolean existsByPlacaIgnoreCase(String placa);

    Page<Vehiculo> findByEstado(EstadoVehiculo estado, Pageable pageable);

    @Query("""
            select v from Vehiculo v
            where (:search = '' or lower(v.placa) like lower(concat('%', :search, '%'))
                   or lower(v.marca) like lower(concat('%', :search, '%'))
                   or lower(v.modelo) like lower(concat('%', :search, '%')))
              and (:estado is null or v.estado = :estado)
            """)
    Page<Vehiculo> buscar(@Param("search") String search,
                          @Param("estado") EstadoVehiculo estado,
                          Pageable pageable);
}
