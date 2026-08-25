package com.trailersys.backend.vehiculo;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    Optional<Vehiculo> findByPlacaIgnoreCase(String placa);

    boolean existsByPlacaIgnoreCase(String placa);

    Page<Vehiculo> findByEstado(EstadoVehiculo estado, Pageable pageable);
}
