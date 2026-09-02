package com.trailersys.backend.common;

/** Conteo real (no acotado a una pagina) de Cargas por estado, para el modulo Reportes. */
public record CargaResumenResponse(long pendientes, long asignadas, long enTransito, long entregadas, long canceladas) {
}
