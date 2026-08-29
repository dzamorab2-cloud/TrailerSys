package com.trailersys.backend.pedido.dto;

import com.trailersys.backend.cliente.Cliente;

public record PerfilClienteResponse(Long id, String nombre, String identificacion, String telefono,
        String correo, String direccion, String servicios, String observaciones) {
    public static PerfilClienteResponse from(Cliente c) {
        return new PerfilClienteResponse(c.getId(), c.getNombre(), c.getIdentificacion(), c.getTelefono(),
                c.getCorreo(), c.getDireccion(), c.getServicios(), c.getObservaciones());
    }
}
