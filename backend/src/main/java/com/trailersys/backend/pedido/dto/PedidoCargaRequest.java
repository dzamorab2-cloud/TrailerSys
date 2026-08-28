package com.trailersys.backend.pedido.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A diferencia de CargaRequest (uso interno de Administrador/Coordinador),
 * este DTO no tiene clienteId ni estado: el cliente que hace el pedido
 * nunca elige a nombre de que cliente lo crea (siempre el suyo propio,
 * resuelto en el backend a partir de la sesion) ni en que estado queda
 * (siempre Pendiente, a la espera de que un Coordinador le arme un viaje).
 */
public record PedidoCargaRequest(
        @NotBlank(message = "La descripción es obligatoria") String descripcion,
        @NotBlank(message = "El tipo de mercancía es obligatorio") String tipo,
        @NotNull(message = "El peso es obligatorio") @Min(value = 0, message = "El peso no puede ser negativo") Integer peso,
        @NotBlank(message = "El origen es obligatorio") String origen,
        @NotBlank(message = "El destino es obligatorio") String destino,
        String observaciones
) {
}
