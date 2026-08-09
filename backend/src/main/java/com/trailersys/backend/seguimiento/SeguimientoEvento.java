package com.trailersys.backend.seguimiento;

import java.time.LocalDateTime;

import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.viaje.Viaje;

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

/**
 * Coincide con el modelo de datos de la seccion 10 del documento:
 * "Seguimiento: ID, viaje, vehiculo, fecha/hora, ubicacion, evento y
 * observacion". El vehiculo se guarda como una foto del momento (se
 * copia del viaje al crear el evento) en vez de derivarse siempre de
 * viaje.getVehiculo(), igual que hace js/seguimiento.js en el frontend.
 */
@Entity
@Table(name = "seguimiento_eventos")
public class SeguimientoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "viaje_id", nullable = false)
    private Viaje viaje;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehiculo_id", nullable = false)
    private Vehiculo vehiculo;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEvento evento;

    @Column(nullable = false, length = 255)
    private String ubicacion;

    @Column(columnDefinition = "TEXT")
    private String observacion;

    protected SeguimientoEvento() {
    }

    public SeguimientoEvento(Viaje viaje, Vehiculo vehiculo, LocalDateTime fechaHora, TipoEvento evento,
                              String ubicacion, String observacion) {
        this.viaje = viaje;
        this.vehiculo = vehiculo;
        this.fechaHora = fechaHora;
        this.evento = evento;
        this.ubicacion = ubicacion;
        this.observacion = observacion;
    }

    public Long getId() {
        return id;
    }

    public Viaje getViaje() {
        return viaje;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public TipoEvento getEvento() {
        return evento;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public String getObservacion() {
        return observacion;
    }
}
