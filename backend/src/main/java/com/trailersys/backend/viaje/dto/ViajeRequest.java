package com.trailersys.backend.viaje.dto;

import java.time.LocalDateTime;

import com.trailersys.backend.viaje.EstadoViaje;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ViajeRequest(
        @NotNull(message = "Selecciona un vehículo") Long vehiculoId,
        @NotNull(message = "Selecciona un conductor") Long conductorId,
        @NotNull(message = "Selecciona un cliente") Long clienteId,
        Long cargaId,
        @NotBlank(message = "El origen es obligatorio") String origen,
        @NotBlank(message = "El destino es obligatorio") String destino,
        @NotNull(message = "La fecha de salida es obligatoria") LocalDateTime fechaSalida,
        @NotNull(message = "El estado es obligatorio") EstadoViaje estado,
        String observaciones,
        @Valid RutaDto ruta
) {
}
