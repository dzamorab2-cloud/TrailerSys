package com.trailersys.backend.facturacion.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.trailersys.backend.facturacion.MetodoPago;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistrarPagoRequest(
        @NotNull(message = "Ingresa el monto del pago")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        @Digits(integer = 10, fraction = 2, message = "Usa como máximo 2 decimales") BigDecimal monto,
        LocalDate fecha,
        @NotNull(message = "Selecciona el método de pago") MetodoPago metodo,
        @Size(max = 120, message = "La referencia no puede superar 120 caracteres") String referencia) {
}
