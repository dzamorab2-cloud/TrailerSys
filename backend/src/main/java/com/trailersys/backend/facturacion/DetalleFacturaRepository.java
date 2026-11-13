package com.trailersys.backend.facturacion;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.trailersys.backend.viaje.Viaje;

public interface DetalleFacturaRepository extends JpaRepository<DetalleFactura, Long> {

    /** Un viaje ya esta facturado si aparece en una factura que no fue anulada. */
    boolean existsByViaje_IdAndFactura_EstadoNot(Long viajeId, EstadoFactura estado);

    /** Viajes finalizados del cliente que todavia no estan en ninguna factura vigente. */
    @Query("""
            select v from Viaje v
            where v.cliente.id = :clienteId
              and v.estado = com.trailersys.backend.viaje.EstadoViaje.FINALIZADO
              and not exists (
                  select d.id from DetalleFactura d
                  where d.viaje = v
                    and d.factura.estado <> com.trailersys.backend.facturacion.EstadoFactura.ANULADA)
            order by v.fechaSalida desc
            """)
    List<Viaje> viajesFacturables(@Param("clienteId") Long clienteId, Pageable pageable);
}
