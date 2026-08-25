package com.trailersys.backend.mantenimiento;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {

    List<Mantenimiento> findByVehiculoIdOrderByFechaDesc(Long vehiculoId);

    List<Mantenimiento> findTop100ByProximoServicioLessThanEqualOrderByProximoServicioAsc(LocalDate fecha);
}
