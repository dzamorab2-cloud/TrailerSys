package com.trailersys.backend.seguimiento.dto;

import java.time.LocalDateTime;

import com.trailersys.backend.seguimiento.TipoEvento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * No incluye vehiculoId a proposito: igual que en el frontend, el
 * vehiculo del evento se deriva automaticamente del viaje seleccionado
 * (ver ViajeService/SeguimientoService), no lo elige el usuario.
 */
public record SeguimientoEventoRequest(
        @NotNull(message = "Selecciona un viaje") Long viajeId,
        @NotNull(message = "La fecha y hora son obligatorias") LocalDateTime fechaHora,
        @NotNull(message = "El tipo de evento es obligatorio") TipoEvento evento,
        @NotBlank(message = "La ubicación es obligatoria") String ubicacion,
        String observacion
) {
}
