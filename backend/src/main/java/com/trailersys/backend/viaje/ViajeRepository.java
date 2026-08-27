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
            """)
    Page<Viaje> buscar(@Param("search") String search,
                       @Param("estado") EstadoViaje estado,
                       Pageable pageable);

    List<Viaje> findByCarga_Id(Long cargaId);

    Optional<Viaje> findFirstByCarga_IdOrderByIdDesc(Long cargaId);

    List<Viaje> findByVehiculo_Id(Long vehiculoId);

    List<Viaje> findByConductor_Id(Long conductorId);

    List<Viaje> findTop500ByEstadoOrderByFechaSalidaAsc(EstadoViaje estado);

    List<Viaje> findTop500ByEstadoAndFechaSalidaLessThanEqualOrderByFechaSalidaAsc(
            EstadoViaje estado, LocalDateTime fechaSalida);

    List<Viaje> findTop100ByEstadoInOrderByFechaSalidaAsc(Collection<EstadoViaje> estados);

    List<Viaje> findTop100ByEntregaConfirmadaTrueAndEntregaValidadaFalseOrderByFechaEntregaConfirmadaDesc();
}
