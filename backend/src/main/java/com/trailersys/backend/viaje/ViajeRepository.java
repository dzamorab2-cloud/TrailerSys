package com.trailersys.backend.viaje;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViajeRepository extends JpaRepository<Viaje, Long> {

    @Query("""
            select v from Viaje v
            where (:search = '' or lower(v.origen) like lower(concat('%', :search, '%'))
                   or lower(v.destino) like lower(concat('%', :search, '%'))
                   or lower(v.vehiculo.placa) like lower(concat('%', :search, '%'))
                   or lower(v.conductor.nombres) like lower(concat('%', :search, '%')))
              and (:estado is null or v.estado = :estado)
              and (cast(:desde as timestamp) is null or v.fechaSalida >= :desde)
              and (cast(:hasta as timestamp) is null or v.fechaSalida <= :hasta)
            """)
    Page<Viaje> buscar(@Param("search") String search,
                       @Param("estado") EstadoViaje estado,
                       @Param("desde") LocalDateTime desde,
                       @Param("hasta") LocalDateTime hasta,
                       Pageable pageable);

    List<Viaje> findByCarga_Id(Long cargaId);

    Optional<Viaje> findFirstByCarga_IdOrderByIdDesc(Long cargaId);

    List<Viaje> findByVehiculo_Id(Long vehiculoId);

    List<Viaje> findByConductor_Id(Long conductorId);

    // Para "Mis viajes" (autoservicio del conductor): historial paginado con
    // el mismo buscador+filtro de estado que ya usa /api/paginas/viajes
    // (buscar() arriba), pero acotado siempre a un conductor especifico -
    // nunca se expone el listado completo de la operacion a este rol.
    @Query("""
            select v from Viaje v
            where v.conductor.id = :conductorId
              and (:search = '' or lower(v.origen) like lower(concat('%', :search, '%'))
                   or lower(v.destino) like lower(concat('%', :search, '%')))
              and (:estado is null or v.estado = :estado)
            """)
    Page<Viaje> buscarMisViajes(@Param("conductorId") Long conductorId,
                                 @Param("search") String search,
                                 @Param("estado") EstadoViaje estado,
                                 Pageable pageable);

    // Detalle de un viaje puntual acotado a un conductor especifico. Un id
    // que no es de este conductor simplemente no aparece (Optional vacio),
    // el mismo criterio que ya usa PedidoClienteService para Carga.
    Optional<Viaje> findByIdAndConductor_Id(Long id, Long conductorId);

    List<Viaje> findTop500ByEstadoOrderByFechaSalidaAsc(EstadoViaje estado);

    List<Viaje> findTop500ByEstadoAndFechaSalidaLessThanEqualOrderByFechaSalidaAsc(
            EstadoViaje estado, LocalDateTime fechaSalida);

    List<Viaje> findTop100ByEstadoInOrderByFechaSalidaAsc(Collection<EstadoViaje> estados);

    List<Viaje> findTop100ByEntregaConfirmadaTrueAndEntregaValidadaFalseOrderByFechaEntregaConfirmadaDesc();

    List<Viaje> findByEstadoReclamoClienteIsNotNullOrderByFechaConfirmacionClienteDesc();

    /**
     * Conteo real por estado (mas la distancia total de ruta), dentro de un
     * rango de fecha opcional - a diferencia de buscar() arriba, esto no
     * pagina: es para las tarjetas de resumen del modulo Reportes, que antes
     * se calculaban en el frontend contando solo la pagina de 100 filas que
     * se mostraba en pantalla (ver ReporteResumenController).
     */
    @Query("""
            select
              sum(case when v.estado = :programado then 1L else 0L end),
              sum(case when v.estado = :enCurso then 1L else 0L end),
              sum(case when v.estado = :finalizado then 1L else 0L end),
              sum(case when v.estado = :cancelado then 1L else 0L end),
              coalesce(sum(v.rutaDistanciaKm), 0.0)
            from Viaje v
            where (cast(:desde as timestamp) is null or v.fechaSalida >= :desde)
              and (cast(:hasta as timestamp) is null or v.fechaSalida <= :hasta)
            """)
    List<Object[]> resumenPorFecha(@Param("programado") EstadoViaje programado,
                                    @Param("enCurso") EstadoViaje enCurso,
                                    @Param("finalizado") EstadoViaje finalizado,
                                    @Param("cancelado") EstadoViaje cancelado,
                                    @Param("desde") LocalDateTime desde,
                                    @Param("hasta") LocalDateTime hasta);
}
