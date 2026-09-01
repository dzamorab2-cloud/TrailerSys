package com.trailersys.backend.dashboard;

import java.util.List;

/**
 * Viajes por dia de los ultimos 7 dias (fecha_salida), para la grafica de
 * tendencia del Dashboard - la misma idea que ya usaba el Conductor con
 * "viajes por mes" (ver ResumenConductorResponse) pero a nivel de toda la
 * operacion, para Administrador/Coordinador/Mantenimiento/Supervisor.
 */
public record TendenciaResponse(List<Punto> viajesPorDia) {
    public record Punto(String etiqueta, long cantidad) {
    }
}
