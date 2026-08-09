package com.trailersys.backend.seguimiento.dto;

/**
 * Espejo de los objetos que arma computeAlerts() en js/seguimiento.js:
 * "danger" o "warning", un icono de Bootstrap Icons y el texto ya armado.
 */
public record AlertaDto(String nivel, String icono, String texto) {
}
