package com.trailersys.backend.mantenimiento.dto;

import java.time.LocalDate;

import com.trailersys.backend.mantenimiento.TipoMantenimiento;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MantenimientoRequest(
        @NotNull(message = "Selecciona un vehículo") Long vehiculoId,
        @NotNull(message = "El tipo es obligatorio") TipoMantenimiento tipo,
        @NotNull(message = "La fecha es obligatoria") LocalDate fecha,
        @NotNull(message = "El kilometraje es obligatorio") @Min(value = 0, message = "El kilometraje no puede ser negativo") Integer kilometraje,
        @NotNull(message = "El costo es obligatorio") @DecimalMin(value = "0", message = "El costo no puede ser negativo") Double costo,
        LocalDate proximoServicio,
        @NotBlank(message = "La descripción es obligatoria") String descripcion
) {
}
