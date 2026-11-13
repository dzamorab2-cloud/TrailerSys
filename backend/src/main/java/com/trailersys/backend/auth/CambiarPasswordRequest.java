package com.trailersys.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cambio de contrasena de la propia cuenta (PUT /api/auth/me/password). */
public record CambiarPasswordRequest(
        @NotBlank(message = "Ingresa tu contraseña actual") String passwordActual,
        @NotBlank(message = "Ingresa la nueva contraseña")
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres") String passwordNueva) {
}
