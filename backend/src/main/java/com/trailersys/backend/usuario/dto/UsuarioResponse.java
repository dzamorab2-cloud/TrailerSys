package com.trailersys.backend.usuario.dto;

import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;

public record UsuarioResponse(Long id, String username, String nombre, String correo, Rol rol, boolean activo,
                               Long clienteId, String clienteNombre,
                               Long conductorId, String conductorNombres) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getNombre(),
                usuario.getCorreo(), usuario.getRol(), usuario.isActivo(),
                usuario.getCliente() != null ? usuario.getCliente().getId() : null,
                usuario.getCliente() != null ? usuario.getCliente().getNombre() : null,
                usuario.getConductor() != null ? usuario.getConductor().getId() : null,
                usuario.getConductor() != null ? usuario.getConductor().getNombres() : null);
    }
}
