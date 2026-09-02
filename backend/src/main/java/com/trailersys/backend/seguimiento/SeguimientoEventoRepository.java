package com.trailersys.backend.seguimiento;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoEventoRepository extends JpaRepository<SeguimientoEvento, Long> {

    List<SeguimientoEvento> findByViajeIdOrderByFechaHoraDesc(Long viajeId);

    /** Para acotar el listado sin viajeId a las propias rutas de un Conductor (ver SeguimientoService.listarEventos). */
    List<SeguimientoEvento> findByViaje_Conductor_IdOrderByFechaHoraDesc(Long conductorId);

    void deleteByViajeId(Long viajeId);
}
