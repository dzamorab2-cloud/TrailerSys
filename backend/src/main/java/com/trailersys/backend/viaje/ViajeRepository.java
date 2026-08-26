package com.trailersys.backend.viaje;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ViajeRepository extends JpaRepository<Viaje, Long> {

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
