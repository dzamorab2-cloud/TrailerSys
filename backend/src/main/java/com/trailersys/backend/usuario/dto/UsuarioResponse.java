package com.trailersys.backend.usuario.dto;

import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;

public record UsuarioResponse(Long id, String username, String nombre, String correo, Rol rol, boolean activo) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getNombre(),
                usuario.getCorreo(), usuario.getRol(), usuario.isActivo());
    }
}
