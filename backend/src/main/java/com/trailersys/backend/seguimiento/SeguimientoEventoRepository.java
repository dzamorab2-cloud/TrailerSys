package com.trailersys.backend.seguimiento;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeguimientoEventoRepository extends JpaRepository<SeguimientoEvento, Long> {

    List<SeguimientoEvento> findByViajeIdOrderByFechaHoraDesc(Long viajeId);

    void deleteByViajeId(Long viajeId);
}
