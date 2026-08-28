package com.trailersys.backend.viaje;

import java.time.LocalDateTime;

import com.trailersys.backend.carga.Carga;
import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.conductor.Conductor;
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

/**
 * El calculo de la ruta (geocodificar origen/destino y trazarla) sigue
 * ocurriendo en el frontend via Nominatim/OSRM (js/map-service.js); este
 * backend solo almacena el resultado ya calculado. Los campos rutaXxx son
 * planos (no un @Embeddable) para evitar los problemas de Hibernate con
 * componentes embebidos nulos cuando todavia no se ha calculado la ruta.
 */
@Entity
@Table(name = "viajes")
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehiculo_id", nullable = false)
    private Vehiculo vehiculo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conductor_id", nullable = false)
    private Conductor conductor;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "carga_id")
    private Carga carga;

    @Column(nullable = false, length = 150)
    private String origen;

    @Column(nullable = false, length = 150)
    private String destino;

    @Column(nullable = false)
    private LocalDateTime fechaSalida;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoViaje estado;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    private Double rutaOrigenLat;
    private Double rutaOrigenLng;
    private Double rutaDestinoLat;
    private Double rutaDestinoLng;
    private Double rutaDistanciaKm;
    private Double rutaDuracionMin;

    /** Lista de [lat, lng] serializada como JSON; ver RutaPathCodec. */
    @Column(columnDefinition = "TEXT")
    private String rutaPath;

    @Column(nullable = false)
    private boolean entregaConfirmada = false;

    private LocalDateTime fechaEntregaConfirmada;

    @Column(columnDefinition = "TEXT")
    private String observacionEntrega;

    @Column(length = 60)
    private String confirmadoPor;

    @Column(nullable = false)
    private boolean entregaValidada = false;

    private LocalDateTime fechaValidacionEntrega;

    @Column(columnDefinition = "TEXT")
    private String observacionValidacion;

    @Column(length = 60)
    private String validadoPor;

    /**
     * Cuantos puntos de control de ViajeSimulacionService ya generaron una
     * parada automatica para este viaje. Integer (no int) y sin
     * nullable=false a proposito: agregar una columna NOT NULL sobre una
     * tabla con filas existentes falla en Postgres si no se define un
     * DEFAULT explicito (ya nos paso con entregaConfirmada/entregaValidada
     * en esta misma sesion).
     */
    private Integer paradasSimuladasRegistradas;

    /**
     * Tercer paso de confirmacion, paralelo a entregaConfirmada (Conductor)
     * y entregaValidada (Supervisor): el Cliente confirma que recibio su
     * carga. No cambia el estado del viaje ni depende de los otros dos
     * pasos. Boolean (no boolean) y sin nullable=false por la misma razon
     * que paradasSimuladasRegistradas: esta columna se agrega sobre una
     * tabla que ya tiene filas.
     */
    private Boolean entregaConfirmadaCliente;

    private LocalDateTime fechaConfirmacionCliente;

    @Column(columnDefinition = "TEXT")
    private String observacionConfirmacionCliente;

    @Column(length = 60)
    private String confirmadoPorCliente;

    protected Viaje() {
    }

    public Viaje(Vehiculo vehiculo, Conductor conductor, Cliente cliente, Carga carga, String origen, String destino,
                 LocalDateTime fechaSalida, EstadoViaje estado, String observaciones) {
        this.vehiculo = vehiculo;
        this.conductor = conductor;
        this.cliente = cliente;
        this.carga = carga;
        this.origen = origen;
        this.destino = destino;
        this.fechaSalida = fechaSalida;
        this.estado = estado;
        this.observaciones = observaciones;
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

    public Conductor getConductor() {
        return conductor;
    }

    public void setConductor(Conductor conductor) {
        this.conductor = conductor;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public Carga getCarga() {
        return carga;
    }

    public void setCarga(Carga carga) {
        this.carga = carga;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public LocalDateTime getFechaSalida() {
        return fechaSalida;
    }

    public void setFechaSalida(LocalDateTime fechaSalida) {
        this.fechaSalida = fechaSalida;
    }

    public EstadoViaje getEstado() {
        return estado;
    }

    public void setEstado(EstadoViaje estado) {
        this.estado = estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public Double getRutaOrigenLat() {
        return rutaOrigenLat;
    }

    public void setRutaOrigenLat(Double rutaOrigenLat) {
        this.rutaOrigenLat = rutaOrigenLat;
    }

    public Double getRutaOrigenLng() {
        return rutaOrigenLng;
    }

    public void setRutaOrigenLng(Double rutaOrigenLng) {
        this.rutaOrigenLng = rutaOrigenLng;
    }

    public Double getRutaDestinoLat() {
        return rutaDestinoLat;
    }

    public void setRutaDestinoLat(Double rutaDestinoLat) {
        this.rutaDestinoLat = rutaDestinoLat;
    }

    public Double getRutaDestinoLng() {
        return rutaDestinoLng;
    }

    public void setRutaDestinoLng(Double rutaDestinoLng) {
        this.rutaDestinoLng = rutaDestinoLng;
    }

    public Double getRutaDistanciaKm() {
        return rutaDistanciaKm;
    }

    public void setRutaDistanciaKm(Double rutaDistanciaKm) {
        this.rutaDistanciaKm = rutaDistanciaKm;
    }

    public Double getRutaDuracionMin() {
        return rutaDuracionMin;
    }

    public void setRutaDuracionMin(Double rutaDuracionMin) {
        this.rutaDuracionMin = rutaDuracionMin;
    }

    public String getRutaPath() {
        return rutaPath;
    }

    public void setRutaPath(String rutaPath) {
        this.rutaPath = rutaPath;
    }

    public boolean isEntregaConfirmada() {
        return entregaConfirmada;
    }

    public void setEntregaConfirmada(boolean entregaConfirmada) {
        this.entregaConfirmada = entregaConfirmada;
    }

    public LocalDateTime getFechaEntregaConfirmada() {
        return fechaEntregaConfirmada;
    }

    public void setFechaEntregaConfirmada(LocalDateTime fechaEntregaConfirmada) {
        this.fechaEntregaConfirmada = fechaEntregaConfirmada;
    }

    public String getObservacionEntrega() {
        return observacionEntrega;
    }

    public void setObservacionEntrega(String observacionEntrega) {
        this.observacionEntrega = observacionEntrega;
    }

    public String getConfirmadoPor() {
        return confirmadoPor;
    }

    public void setConfirmadoPor(String confirmadoPor) {
        this.confirmadoPor = confirmadoPor;
    }

    public boolean isEntregaValidada() {
        return entregaValidada;
    }

    public void setEntregaValidada(boolean entregaValidada) {
        this.entregaValidada = entregaValidada;
    }

    public LocalDateTime getFechaValidacionEntrega() {
        return fechaValidacionEntrega;
    }

    public void setFechaValidacionEntrega(LocalDateTime fechaValidacionEntrega) {
        this.fechaValidacionEntrega = fechaValidacionEntrega;
    }

    public String getObservacionValidacion() {
        return observacionValidacion;
    }

    public void setObservacionValidacion(String observacionValidacion) {
        this.observacionValidacion = observacionValidacion;
    }

    public String getValidadoPor() {
        return validadoPor;
    }

    public void setValidadoPor(String validadoPor) {
        this.validadoPor = validadoPor;
    }

    public int getParadasSimuladasRegistradas() {
        return paradasSimuladasRegistradas == null ? 0 : paradasSimuladasRegistradas;
    }

    public void setParadasSimuladasRegistradas(int paradasSimuladasRegistradas) {
        this.paradasSimuladasRegistradas = paradasSimuladasRegistradas;
    }

    public boolean isEntregaConfirmadaCliente() {
        return entregaConfirmadaCliente != null && entregaConfirmadaCliente;
    }

    public void setEntregaConfirmadaCliente(boolean entregaConfirmadaCliente) {
        this.entregaConfirmadaCliente = entregaConfirmadaCliente;
    }

    public LocalDateTime getFechaConfirmacionCliente() {
        return fechaConfirmacionCliente;
    }

    public void setFechaConfirmacionCliente(LocalDateTime fechaConfirmacionCliente) {
        this.fechaConfirmacionCliente = fechaConfirmacionCliente;
    }

    public String getObservacionConfirmacionCliente() {
        return observacionConfirmacionCliente;
    }

    public void setObservacionConfirmacionCliente(String observacionConfirmacionCliente) {
        this.observacionConfirmacionCliente = observacionConfirmacionCliente;
    }

    public String getConfirmadoPorCliente() {
        return confirmadoPorCliente;
    }

    public void setConfirmadoPorCliente(String confirmadoPorCliente) {
        this.confirmadoPorCliente = confirmadoPorCliente;
    }
}
