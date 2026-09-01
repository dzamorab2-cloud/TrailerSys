package com.trailersys.backend.conductor;

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
@Table(name = "conductores")
public class Conductor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombres;

    @Column(nullable = false, unique = true, length = 30)
    private String identificacion;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Column(length = 120)
    private String correo;

    @Column(nullable = false, length = 40)
    private String licenciaNumero;

    @Column(nullable = false, length = 20)
    private String licenciaCategoria;

    @Column(nullable = false)
    private LocalDate licenciaVencimiento;

    /**
     * Opcional: nadie la exigia hasta que el Dashboard propio del conductor
     * (autoservicio) empezo a mostrar su edad. Se guarda la fecha (no un
     * numero de edad) para que no quede desactualizada - la edad se calcula
     * al leer, en ConductorResponse.
     */
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoConductor estado;

    /**
     * Vehiculo asignado, opcional (igual que "Sin asignar" en el frontend).
     * EAGER a proposito: ConductorResponse siempre necesita vehiculoPlaca, y
     * el mapeo a DTO ocurre en el controller, fuera de la transaccion de
     * lectura, donde un proxy LAZY ya lanzaria LazyInitializationException.
     */
    @ManyToOne
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(columnDefinition = "TEXT")
    private String foto;

    protected Conductor() {
    }

    public Conductor(String nombres, String identificacion, String telefono, String correo,
                      String licenciaNumero, String licenciaCategoria, LocalDate licenciaVencimiento,
                      EstadoConductor estado, Vehiculo vehiculo, String observaciones, String foto) {
        this.nombres = nombres;
        this.identificacion = identificacion;
        this.telefono = telefono;
        this.correo = correo;
        this.licenciaNumero = licenciaNumero;
        this.licenciaCategoria = licenciaCategoria;
        this.licenciaVencimiento = licenciaVencimiento;
        this.estado = estado;
        this.vehiculo = vehiculo;
        this.observaciones = observaciones;
        this.foto = foto;
    }

    public Long getId() {
        return id;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getIdentificacion() {
        return identificacion;
    }

    public void setIdentificacion(String identificacion) {
        this.identificacion = identificacion;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getLicenciaNumero() {
        return licenciaNumero;
    }

    public void setLicenciaNumero(String licenciaNumero) {
        this.licenciaNumero = licenciaNumero;
    }

    public String getLicenciaCategoria() {
        return licenciaCategoria;
    }

    public void setLicenciaCategoria(String licenciaCategoria) {
        this.licenciaCategoria = licenciaCategoria;
    }

    public LocalDate getLicenciaVencimiento() {
        return licenciaVencimiento;
    }

    public void setLicenciaVencimiento(LocalDate licenciaVencimiento) {
        this.licenciaVencimiento = licenciaVencimiento;
    }

    public EstadoConductor getEstado() {
        return estado;
    }

    public void setEstado(EstadoConductor estado) {
        this.estado = estado;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }
}
