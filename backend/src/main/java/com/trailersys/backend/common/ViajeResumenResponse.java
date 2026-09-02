package com.trailersys.backend.common;

/**
 * Conteo real (no acotado a una pagina) de Viajes por estado, dentro del
 * rango de fechas pedido, para el modulo Reportes.
 */
public record ViajeResumenResponse(long programados, long enCurso, long finalizados, long cancelados, double kmTotales) {
}
