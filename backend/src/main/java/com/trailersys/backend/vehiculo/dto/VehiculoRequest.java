package com.trailersys.backend.vehiculo.dto;

import com.trailersys.backend.vehiculo.EstadoVehiculo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record VehiculoRequest(
        @NotBlank(message = "La placa es obligatoria") String placa,
        @NotBlank(message = "La marca es obligatoria") String marca,
        @NotBlank(message = "El modelo es obligatorio") String modelo,
        @NotBlank(message = "El tipo es obligatorio") String tipo,
        @NotNull(message = "El año es obligatorio") @Min(value = 1980, message = "El año debe ser 1980 o posterior") Integer anio,
        @NotBlank(message = "El color es obligatorio") String color,
        @NotNull(message = "El estado es obligatorio") EstadoVehiculo estado,
        @NotNull(message = "El kilometraje es obligatorio") @Min(value = 0, message = "El kilometraje no puede ser negativo") Integer kilometraje,
        @NotNull(message = "La capacidad es obligatoria") @Min(value = 0, message = "La capacidad no puede ser negativa") Integer capacidad,
        String observaciones,
        String foto
) {
}
