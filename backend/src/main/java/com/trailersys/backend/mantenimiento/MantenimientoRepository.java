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

    List<Mantenimiento> findByProximoServicioBetweenOrderByProximoServicioAsc(LocalDate desde, LocalDate hasta);
}
