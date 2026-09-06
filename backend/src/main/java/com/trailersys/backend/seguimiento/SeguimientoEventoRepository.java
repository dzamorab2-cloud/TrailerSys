package com.trailersys.backend.seguimiento;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoEventoRepository extends JpaRepository<SeguimientoEvento, Long> {

    List<SeguimientoEvento> findByViajeIdOrderByFechaHoraDesc(Long viajeId);

    /** Para acotar el listado sin viajeId a las propias rutas de un Conductor (ver SeguimientoService.listarEventos). */
    List<SeguimientoEvento> findByViaje_Conductor_IdOrderByFechaHoraDesc(Long conductorId);

    /**
     * El evento mas reciente de este viaje que tenga una ubicacion marcada a
     * mano por el Conductor (ver SeguimientoEvento) - lo usa el mapa en vivo
     * (js/seguimiento.js) para corregir la posicion simulada del vehiculo.
     */
    Optional<SeguimientoEvento> findFirstByViajeIdAndLatitudReportadaIsNotNullAndLongitudReportadaIsNotNullOrderByFechaHoraDesc(Long viajeId);

    void deleteByViajeId(Long viajeId);
}
