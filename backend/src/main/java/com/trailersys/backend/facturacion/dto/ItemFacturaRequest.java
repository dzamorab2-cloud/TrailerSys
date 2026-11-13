package com.trailersys.backend.facturacion.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ItemFacturaRequest(
        @NotNull(message = "Cada línea debe tener un viaje") Long viajeId,
        @Size(max = 255, message = "La descripción no puede superar 255 caracteres") String descripcion,
        @NotNull(message = "Ingresa el valor de cada viaje")
        @DecimalMin(value = "0.01", message = "El valor de cada viaje debe ser mayor a 0")
        @Digits(integer = 10, fraction = 2, message = "Usa como máximo 2 decimales") BigDecimal monto) {
}
