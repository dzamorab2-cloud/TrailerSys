package com.trailersys.backend.respaldo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Configuracion global (una sola fila, ver RespaldoService.obtenerOCrearConfiguracion)
 * de cuándo corren los respaldos automáticos. No hay una fila por usuario ni
 * por rol: es una única política para toda la operación, que cualquier
 * Administrador puede ver/editar - la frecuencia (diario/semanal/mensual) y
 * la hora quedan a criterio de quien la configura, no hay un valor "correcto"
 * fijo en el código.
 */
@Entity
@Table(name = "configuracion_respaldo")
public class ConfiguracionRespaldo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean activo = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FrecuenciaRespaldo frecuencia = FrecuenciaRespaldo.DIARIO;

    @Column(nullable = false)
    private LocalTime horaProgramada = LocalTime.of(2, 0);

    /** Solo aplica cuando frecuencia=SEMANAL: en qué día de la semana correr. */
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private DayOfWeek diaSemana = DayOfWeek.MONDAY;

    /**
     * Solo aplica cuando frecuencia=MENSUAL: día del mes (1-31) en que correr.
     * Si el mes no tiene ese día (ej. 31 en febrero), corre el último día del
     * mes - ver RespaldoService.correspondeHoy().
     */
    private Integer diaMes = 1;

    /** Evita disparar el respaldo programado más de una vez el mismo día. */
    private LocalDate ultimaEjecucionProgramada;

    public Long getId() {
        return id;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public FrecuenciaRespaldo getFrecuencia() {
        return frecuencia;
    }

    public void setFrecuencia(FrecuenciaRespaldo frecuencia) {
        this.frecuencia = frecuencia;
    }

    public LocalTime getHoraProgramada() {
        return horaProgramada;
    }

    public void setHoraProgramada(LocalTime horaProgramada) {
        this.horaProgramada = horaProgramada;
    }

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(DayOfWeek diaSemana) {
        this.diaSemana = diaSemana;
    }

    public Integer getDiaMes() {
        return diaMes;
    }

    public void setDiaMes(Integer diaMes) {
        this.diaMes = diaMes;
    }

    public LocalDate getUltimaEjecucionProgramada() {
        return ultimaEjecucionProgramada;
    }

    public void setUltimaEjecucionProgramada(LocalDate ultimaEjecucionProgramada) {
        this.ultimaEjecucionProgramada = ultimaEjecucionProgramada;
    }
}
