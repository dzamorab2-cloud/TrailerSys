package com.trailersys.backend.auth;

import com.trailersys.backend.usuario.Usuario;

public record MeResponse(
        Long id,
        String username,
        String nombre,
        String correo,
        String rol
) {
    public static MeResponse from(Usuario usuario) {
        return new MeResponse(usuario.getId(), usuario.getUsername(), usuario.getNombre(),
                usuario.getCorreo(), usuario.getRol().name());
    }
}
