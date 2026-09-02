package com.trailersys.backend.common;

/** Conteo real (no acotado a una pagina) de Clientes por estado, para el modulo Reportes. */
public record ClienteResumenResponse(long activos, long inactivos, long conCorreo) {
}
