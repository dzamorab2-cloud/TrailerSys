package com.trailersys.backend.mantenimiento;

import java.time.LocalDate;

import com.trailersys.backend.vehiculo.Vehiculo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "mantenimientos")
public class Mantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehiculo_id", nullable = false)
    private Vehiculo vehiculo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMantenimiento tipo;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private Integer kilometraje;

    @Column(nullable = false)
    private Double costo;

    private LocalDate proximoServicio;

    @Column(nullable = false, length = 255)
    private String descripcion;

    protected Mantenimiento() {
    }

    public Mantenimiento(Vehiculo vehiculo, TipoMantenimiento tipo, LocalDate fecha, Integer kilometraje,
                          Double costo, LocalDate proximoServicio, String descripcion) {
        this.vehiculo = vehiculo;
        this.tipo = tipo;
        this.fecha = fecha;
        this.kilometraje = kilometraje;
        this.costo = costo;
        this.proximoServicio = proximoServicio;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }

    public TipoMantenimiento getTipo() {
        return tipo;
    }

    public void setTipo(TipoMantenimiento tipo) {
        this.tipo = tipo;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public Integer getKilometraje() {
        return kilometraje;
    }

    public void setKilometraje(Integer kilometraje) {
        this.kilometraje = kilometraje;
    }

    public Double getCosto() {
        return costo;
    }

    public void setCosto(Double costo) {
        this.costo = costo;
    }

    public LocalDate getProximoServicio() {
        return proximoServicio;
    }

    public void setProximoServicio(LocalDate proximoServicio) {
        this.proximoServicio = proximoServicio;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
