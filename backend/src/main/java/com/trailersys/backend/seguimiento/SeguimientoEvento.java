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

    /**
     * No hay telemetria GPS real conectada al vehiculo (ver la misma
     * aclaracion en ViajeSimulacionService y calcularPosicionSimulada en
     * js/seguimiento.js): estas dos columnas son opcionales y solo se
     * llenan cuando el propio Conductor marca en el mapa, a mano, donde
     * esta en realidad al registrar un evento (ver js/mis-viajes.js). Los
     * eventos automaticos de la simulacion (SALIDA/PARADA/LLEGADA creados
     * desde ViajeService/ViajeSimulacionService) las dejan en null.
     */
    private Double latitudReportada;

    private Double longitudReportada;

    protected SeguimientoEvento() {
    }

    public SeguimientoEvento(Viaje viaje, Vehiculo vehiculo, LocalDateTime fechaHora, TipoEvento evento,
                              String ubicacion, String observacion) {
        this(viaje, vehiculo, fechaHora, evento, ubicacion, observacion, null, null);
    }

    public SeguimientoEvento(Viaje viaje, Vehiculo vehiculo, LocalDateTime fechaHora, TipoEvento evento,
                              String ubicacion, String observacion, Double latitudReportada, Double longitudReportada) {
        this.viaje = viaje;
        this.vehiculo = vehiculo;
        this.fechaHora = fechaHora;
        this.evento = evento;
        this.ubicacion = ubicacion;
        this.observacion = observacion;
        this.latitudReportada = latitudReportada;
        this.longitudReportada = longitudReportada;
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

    public Double getLatitudReportada() {
        return latitudReportada;
    }

    public Double getLongitudReportada() {
        return longitudReportada;
    }
}
