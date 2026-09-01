package com.trailersys.backend.conductor.dto;

import java.time.LocalDate;

import com.trailersys.backend.conductor.EstadoConductor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConductorRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombres,
        @NotBlank(message = "La identificación es obligatoria") String identificacion,
        @NotBlank(message = "El teléfono es obligatorio") String telefono,
        @Email(message = "Ingresa un correo válido") String correo,
        @NotBlank(message = "El número de licencia es obligatorio") String licenciaNumero,
        @NotBlank(message = "La categoría es obligatoria") String licenciaCategoria,
        @NotNull(message = "La fecha de vencimiento es obligatoria") LocalDate licenciaVencimiento,
        @NotNull(message = "El estado es obligatorio") EstadoConductor estado,
        Long vehiculoId,
        String observaciones,
        String foto,
        /** Opcional: solo se usa para calcular la edad mostrada en la guia/perfil. */
        LocalDate fechaNacimiento
) {
}
