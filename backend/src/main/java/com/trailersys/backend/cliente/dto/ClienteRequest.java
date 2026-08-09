package com.trailersys.backend.cliente.dto;

import com.trailersys.backend.cliente.EstadoCliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClienteRequest(
        @NotBlank(message = "El nombre o razón social es obligatorio") String nombre,
        @NotBlank(message = "La identificación es obligatoria") String identificacion,
        @NotNull(message = "El estado es obligatorio") EstadoCliente estado,
        @NotBlank(message = "El teléfono es obligatorio") String telefono,
        @Email(message = "Ingresa un correo válido") String correo,
        @NotBlank(message = "La dirección es obligatoria") String direccion,
        String servicios,
        String observaciones
) {
}
