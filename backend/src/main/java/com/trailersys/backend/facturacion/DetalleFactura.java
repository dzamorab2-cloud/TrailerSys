package com.trailersys.backend.facturacion;

import java.math.BigDecimal;

import com.trailersys.backend.viaje.Viaje;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Una linea de la factura: el servicio de transporte de un viaje. */
@Entity
@Table(name = "factura_detalles", indexes = {
        @Index(name = "idx_factura_detalles_factura", columnList = "factura_id"),
        @Index(name = "idx_factura_detalles_viaje", columnList = "viaje_id")
})
public class DetalleFactura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    @ManyToOne
    @JoinColumn(name = "viaje_id")
    private Viaje viaje;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    protected DetalleFactura() {
    }

    public DetalleFactura(Factura factura, Viaje viaje, String descripcion, BigDecimal monto) {
        this.factura = factura;
        this.viaje = viaje;
        this.descripcion = descripcion;
        this.monto = monto;
    }

    public Long getId() { return id; }
    public Factura getFactura() { return factura; }
    public Viaje getViaje() { return viaje; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getMonto() { return monto; }
}
