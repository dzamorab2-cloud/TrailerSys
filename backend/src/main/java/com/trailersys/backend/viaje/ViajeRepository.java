package com.trailersys.backend.viaje;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ViajeRepository extends JpaRepository<Viaje, Long> {

    List<Viaje> findByCarga_Id(Long cargaId);

    List<Viaje> findByVehiculo_Id(Long vehiculoId);

    List<Viaje> findByConductor_Id(Long conductorId);
}
