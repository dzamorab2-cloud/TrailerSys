package com.trailersys.backend.seguimiento.dto;

import java.time.LocalDateTime;

import com.trailersys.backend.seguimiento.TipoEvento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

/**
 * No incluye vehiculoId a proposito: igual que en el frontend, el
 * vehiculo del evento se deriva automaticamente del viaje seleccionado
 * (ver ViajeService/SeguimientoService), no lo elige el usuario.
 *
 * latitudReportada/longitudReportada son opcionales: solo llegan cuando
 * el Conductor marco un punto en el mapa de js/mis-viajes.js al crear el
 * evento (no hay GPS real conectado). Si no se marco nada, ambos quedan
 * null y el evento se guarda igual que antes de esta funcionalidad.
 */
public record SeguimientoEventoRequest(
        @NotNull(message = "Selecciona un viaje") Long viajeId,
        @NotNull(message = "La fecha y hora son obligatorias")
        @PastOrPresent(message = "La fecha y hora no pueden ser futuras") LocalDateTime fechaHora,
        @NotNull(message = "El tipo de evento es obligatorio") TipoEvento evento,
        @NotBlank(message = "La ubicación es obligatoria") String ubicacion,
        String observacion,
        Double latitudReportada,
        Double longitudReportada
) {
}
