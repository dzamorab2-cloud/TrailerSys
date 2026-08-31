package com.trailersys.backend.mantenimiento;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Long> {

    @Query("""
            select m from Mantenimiento m
            where (:search = '' or lower(m.descripcion) like lower(concat('%', :search, '%'))
                   or lower(m.vehiculo.placa) like lower(concat('%', :search, '%')))
              and (:vehiculoId is null or m.vehiculo.id = :vehiculoId)
              and (:tipo is null or m.tipo = :tipo)
              and (cast(:desde as date) is null or m.fecha >= :desde)
              and (cast(:hasta as date) is null or m.fecha <= :hasta)
            """)
    Page<Mantenimiento> buscar(@Param("search") String search,
                               @Param("vehiculoId") Long vehiculoId,
                               @Param("tipo") TipoMantenimiento tipo,
                               @Param("desde") LocalDate desde,
                               @Param("hasta") LocalDate hasta,
                               Pageable pageable);

    List<Mantenimiento> findByVehiculoIdOrderByFechaDesc(Long vehiculoId);

    List<Mantenimiento> findTop100ByProximoServicioLessThanEqualOrderByProximoServicioAsc(LocalDate fecha);

    /**
     * Usado por SeguimientoService.obtenerAlertas() para poder diversificar
     * antes de quedarse con las 100 alertas finales: se trae un lote mas
     * grande (2.000) porque en la practica muchos vehiculos comparten la
     * misma fecha de proximo servicio (mas de 100 por dia en el peor caso),
     * asi que tomar directo "los primeros 100" ordenados por fecha deja una
     * sola fecha repetida 100 veces en vez de una mezcla de vehiculos/dias.
     */
    List<Mantenimiento> findTop2000ByProximoServicioLessThanEqualOrderByProximoServicioAsc(LocalDate fecha);

    List<Mantenimiento> findByProximoServicioBetweenOrderByProximoServicioAsc(LocalDate desde, LocalDate hasta);
}
