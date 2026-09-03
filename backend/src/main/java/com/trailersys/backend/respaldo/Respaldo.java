package com.trailersys.backend.respaldo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un respaldo COMPLETO es un dump real de pg_dump (archivoRuta apunta a ese
 * .dump). Un INCREMENTAL es un archivo JSON con las filas de "auditoria"
 * posteriores al respaldo anterior (respaldoAnteriorId) - no vuelve a copiar
 * toda la base, solo lo que cambio desde entonces. La cadena
 * respaldoAnteriorId -> ... -> un COMPLETO (sin respaldoAnteriorId) es lo que
 * hace falta recorrer para reconstruir la base completa (ver
 * database/RestaurarRespaldo.java).
 */
@Entity
@Table(name = "respaldos")
public class Respaldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoRespaldo tipo;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(length = 500)
    private String archivoRuta;

    private Long tamanoBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoRespaldo estado;

    @Column(length = 1000)
    private String mensajeError;

    /** Null para un COMPLETO. Para un INCREMENTAL, el id del respaldo del que parte. */
    private Long respaldoAnteriorId;

    /** Solo para INCREMENTAL: cuantas filas de auditoria quedaron capturadas. */
    private Integer registrosCapturados;

    @Column(nullable = false, length = 60)
    private String generadoPor;

    protected Respaldo() {
    }

    public Respaldo(TipoRespaldo tipo, String generadoPor, Long respaldoAnteriorId) {
        this.tipo = tipo;
        this.generadoPor = generadoPor;
        this.respaldoAnteriorId = respaldoAnteriorId;
        this.fechaHora = LocalDateTime.now();
        this.estado = EstadoRespaldo.EN_PROGRESO;
    }

    public Long getId() {
        return id;
    }

    public TipoRespaldo getTipo() {
        return tipo;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public String getArchivoRuta() {
        return archivoRuta;
    }

    public void setArchivoRuta(String archivoRuta) {
        this.archivoRuta = archivoRuta;
    }

    public Long getTamanoBytes() {
        return tamanoBytes;
    }

    public void setTamanoBytes(Long tamanoBytes) {
        this.tamanoBytes = tamanoBytes;
    }

    public EstadoRespaldo getEstado() {
        return estado;
    }

    public void setEstado(EstadoRespaldo estado) {
        this.estado = estado;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public void setMensajeError(String mensajeError) {
        this.mensajeError = mensajeError;
    }

    public Long getRespaldoAnteriorId() {
        return respaldoAnteriorId;
    }

    public Integer getRegistrosCapturados() {
        return registrosCapturados;
    }

    public void setRegistrosCapturados(Integer registrosCapturados) {
        this.registrosCapturados = registrosCapturados;
    }

    public String getGeneradoPor() {
        return generadoPor;
    }
}
