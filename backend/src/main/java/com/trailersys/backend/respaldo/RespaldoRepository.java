package com.trailersys.backend.respaldo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RespaldoRepository extends JpaRepository<Respaldo, Long> {

    List<Respaldo> findAllByOrderByFechaHoraDesc();

    /**
     * El ultimo respaldo COMPLETADO (nunca uno FALLIDO/EN_PROGRESO): es el
     * punto de partida real para el proximo incremental - encadenar desde un
     * respaldo que fallo dejaria un "eslabon roto" en la cadena.
     */
    Optional<Respaldo> findFirstByEstadoOrderByFechaHoraDesc(EstadoRespaldo estado);
}
