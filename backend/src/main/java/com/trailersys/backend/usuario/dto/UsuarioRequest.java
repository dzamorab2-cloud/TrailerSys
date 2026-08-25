package com.trailersys.backend.usuario.dto;

import com.trailersys.backend.usuario.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
        @NotBlank @Size(max = 60) String username,
        @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres") String password,
        @NotBlank @Size(max = 120) String nombre,
        @Email @Size(max = 120) String correo,
        @NotNull Rol rol,
        @NotNull Boolean activo
) {
}
