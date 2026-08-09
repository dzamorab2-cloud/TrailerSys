package com.trailersys.backend.carga.dto;

import com.trailersys.backend.carga.EstadoCarga;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CargaRequest(
        @NotBlank(message = "La descripción es obligatoria") String descripcion,
        @NotNull(message = "Selecciona un cliente") Long clienteId,
        @NotBlank(message = "El tipo de mercancía es obligatorio") String tipo,
        @NotNull(message = "El peso es obligatorio") @Min(value = 0, message = "El peso no puede ser negativo") Integer peso,
        @NotBlank(message = "El origen es obligatorio") String origen,
        @NotBlank(message = "El destino es obligatorio") String destino,
        @NotNull(message = "El estado es obligatorio") EstadoCarga estado,
        String observaciones
) {
}
