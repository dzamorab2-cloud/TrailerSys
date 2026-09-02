package com.trailersys.backend.common;

/**
 * Conteo real (no acotado a una pagina) de Mantenimientos por tipo y
 * vencidos, dentro del vehiculo/rango de fecha pedido, para el modulo
 * Reportes.
 */
public record MantenimientoResumenResponse(long preventivos, long correctivos, long vencidos, double costoTotal) {
}
