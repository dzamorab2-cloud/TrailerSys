package com.trailersys.backend.mantenimiento;

import java.util.List;

/**
 * Mantenimientos por mes de los ultimos 6 meses (fecha), para la grafica de
 * tendencia del Dashboard del rol Mantenimiento - mismo espiritu que
 * TendenciaResponse (viajes de toda la operacion) y
 * ResumenConductorResponse.viajesPorMes.
 */
public record MantenimientoTendenciaResponse(List<Punto> mantenimientosPorMes) {
    public record Punto(String etiqueta, long cantidad) {
    }
}
