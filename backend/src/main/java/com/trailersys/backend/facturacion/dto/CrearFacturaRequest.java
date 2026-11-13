package com.trailersys.backend.facturacion.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearFacturaRequest(
        @NotNull(message = "Selecciona el cliente") Long clienteId,
        @Min(value = 0, message = "Los días de crédito no pueden ser negativos")
        @Max(value = 365, message = "Los días de crédito no pueden superar 365") Integer diasCredito,
        @DecimalMin(value = "0", message = "El IVA no puede ser negativo")
        @DecimalMax(value = "100", message = "El IVA no puede superar el 100%") BigDecimal ivaPorcentaje,
        @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres") String observaciones,
        @NotEmpty(message = "Agrega al menos un viaje a la factura") List<@Valid ItemFacturaRequest> items) {
}
