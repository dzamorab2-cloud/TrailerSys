package com.trailersys.backend.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Abono (parcial o total) registrado contra una factura. */
@Entity
@Table(name = "pagos", indexes = {
        @Index(name = "idx_pagos_factura", columnList = "factura_id"),
        @Index(name = "idx_pagos_fecha", columnList = "fecha")
})
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPago metodo;

    @Column(length = 120)
    private String referencia;

    @Column(name = "registrado_por", length = 60)
    private String registradoPor;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    protected Pago() {
    }

    public Pago(Factura factura, LocalDate fecha, BigDecimal monto, MetodoPago metodo,
                String referencia, String registradoPor) {
        this.factura = factura;
        this.fecha = fecha;
        this.monto = monto;
        this.metodo = metodo;
        this.referencia = referencia;
        this.registradoPor = registradoPor;
        this.fechaRegistro = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Factura getFactura() { return factura; }
    public LocalDate getFecha() { return fecha; }
    public BigDecimal getMonto() { return monto; }
    public MetodoPago getMetodo() { return metodo; }
    public String getReferencia() { return referencia; }
    public String getRegistradoPor() { return registradoPor; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}
