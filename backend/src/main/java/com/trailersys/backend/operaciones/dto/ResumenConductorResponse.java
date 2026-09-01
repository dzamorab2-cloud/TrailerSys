package com.trailersys.backend.operaciones.dto;

import java.util.List;

/**
 * Resumen propio del conductor para las tarjetas y graficas del Dashboard:
 * totales por estado, km recorridos (viajes Finalizados con ruta ya
 * calculada) y viajes por mes (ultimos 6 meses, mas antiguo primero).
 */
public record ResumenConductorResponse(
        long totalViajes,
        long viajesProgramados,
        long viajesEnCurso,
        long viajesFinalizados,
        long viajesCancelados,
        double kmRecorridos,
        List<ViajesPorMes> viajesPorMes
) {
    public record ViajesPorMes(String mes, long cantidad) {
    }
}
