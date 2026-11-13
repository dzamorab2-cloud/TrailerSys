package com.trailersys.backend.facturacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.trailersys.backend.facturacion.DetalleFactura;
import com.trailersys.backend.facturacion.EstadoFactura;
import com.trailersys.backend.facturacion.Factura;
import com.trailersys.backend.facturacion.MetodoPago;
import com.trailersys.backend.facturacion.Pago;

public record FacturaResponse(
        Long id,
        String numero,
        Long clienteId,
        String clienteNombre,
        String clienteIdentificacion,
        String clienteDireccion,
        String clienteCorreo,
        LocalDate fechaEmision,
        LocalDate fechaVencimiento,
        long diasVencida,
        BigDecimal subtotal,
        BigDecimal ivaPorcentaje,
        BigDecimal iva,
        BigDecimal total,
        BigDecimal pagado,
        BigDecimal saldo,
        EstadoFactura estado,
        String observaciones,
        String creadoPor,
        LocalDateTime fechaCreacion,
        String motivoAnulacion,
        LocalDateTime fechaAnulacion,
        List<Detalle> detalles,
        List<PagoItem> pagos) {

    public record Detalle(Long id, Long viajeId, String descripcion, BigDecimal monto) {
        static Detalle from(DetalleFactura d) {
            return new Detalle(d.getId(), d.getViaje() == null ? null : d.getViaje().getId(),
                    d.getDescripcion(), d.getMonto());
        }
    }

    public record PagoItem(Long id, LocalDate fecha, BigDecimal monto, MetodoPago metodo,
                           String referencia, String registradoPor) {
        static PagoItem from(Pago p) {
            return new PagoItem(p.getId(), p.getFecha(), p.getMonto(), p.getMetodo(),
                    p.getReferencia(), p.getRegistradoPor());
        }
    }

    public static FacturaResponse from(Factura f, LocalDate hoy) {
        EstadoFactura estado = f.estadoActual(hoy);
        long diasVencida = estado == EstadoFactura.VENCIDA
                ? ChronoUnit.DAYS.between(f.getFechaVencimiento(), hoy) : 0;
        return new FacturaResponse(
                f.getId(), f.getNumero(),
                f.getCliente().getId(), f.getCliente().getNombre(), f.getCliente().getIdentificacion(),
                f.getCliente().getDireccion(), f.getCliente().getCorreo(),
                f.getFechaEmision(), f.getFechaVencimiento(), diasVencida,
                f.getSubtotal(), f.getIvaPorcentaje(), f.getIva(), f.getTotal(),
                f.getTotal().subtract(f.getSaldo()), f.getSaldo(),
                estado, f.getObservaciones(), f.getCreadoPor(), f.getFechaCreacion(),
                f.getMotivoAnulacion(), f.getFechaAnulacion(),
                f.getDetalles().stream().map(Detalle::from).toList(),
                f.getPagos().stream().map(PagoItem::from).toList());
    }
}
