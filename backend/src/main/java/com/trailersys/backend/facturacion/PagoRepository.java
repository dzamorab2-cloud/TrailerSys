package com.trailersys.backend.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Query("""
            select coalesce(sum(p.monto), 0) from Pago p
            where p.fecha >= :desde
              and p.factura.estado <> com.trailersys.backend.facturacion.EstadoFactura.ANULADA
            """)
    BigDecimal sumarCobradoDesde(@Param("desde") LocalDate desde);
}
