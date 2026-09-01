package com.trailersys.backend.usuario;

import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.conductor.Conductor;

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
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 120)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    /**
     * Solo aplica a usuarios con rol CLIENTE: identifica a que Cliente
     * pertenece, para que el autoservicio (paquete "pedido") acote todas
     * sus consultas/creaciones a este registro y nunca confie en un
     * clienteId que venga del request. Nullable a proposito: el resto de
     * roles (personal interno) no tiene un cliente asociado.
     */
    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    /**
     * Solo aplica a usuarios con rol CONDUCTOR: identifica a que Conductor
     * pertenece, para que su Dashboard y "Mis viajes" (autoservicio del
     * conductor) acoten todo a este registro y nunca confien en un
     * conductorId que venga del request. Nullable por el mismo motivo que
     * "cliente": el resto de roles no tiene un conductor asociado.
     */
    @ManyToOne
    @JoinColumn(name = "conductor_id")
    private Conductor conductor;

    protected Usuario() {
    }

    public Usuario(String username, String passwordHash, String nombre, String correo, Rol rol) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.correo = correo;
        this.rol = rol;
        this.activo = true;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public Conductor getConductor() {
        return conductor;
    }

    public void setConductor(Conductor conductor) {
        this.conductor = conductor;
    }
}
