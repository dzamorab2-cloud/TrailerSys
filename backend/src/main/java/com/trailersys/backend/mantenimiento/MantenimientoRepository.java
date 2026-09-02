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

    /**
     * Conteo real (preventivos/correctivos/vencidos) y costo total dentro de
     * un vehiculo y/o rango de fecha opcional - a diferencia de buscar()
     * arriba, esto no pagina: es para las tarjetas de resumen del modulo
     * Reportes, que antes se calculaban en el frontend contando solo la
     * pagina de 100 filas que se mostraba en pantalla (ver
     * ReporteResumenController). No filtra por tipo a proposito: ese es
     * justamente el desglose que muestran preventivos/correctivos.
     */
    @Query("""
            select
              sum(case when m.tipo = :preventivo then 1L else 0L end),
              sum(case when m.tipo = :correctivo then 1L else 0L end),
              sum(case when m.proximoServicio < current_date then 1L else 0L end),
              coalesce(sum(m.costo), 0.0)
            from Mantenimiento m
            where (:vehiculoId is null or m.vehiculo.id = :vehiculoId)
              and (cast(:desde as date) is null or m.fecha >= :desde)
              and (cast(:hasta as date) is null or m.fecha <= :hasta)
            """)
    List<Object[]> resumen(@Param("preventivo") TipoMantenimiento preventivo,
                            @Param("correctivo") TipoMantenimiento correctivo,
                            @Param("vehiculoId") Long vehiculoId,
                            @Param("desde") LocalDate desde,
                            @Param("hasta") LocalDate hasta);
}
