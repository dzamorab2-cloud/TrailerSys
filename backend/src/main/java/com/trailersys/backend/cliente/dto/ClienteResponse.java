package com.trailersys.backend.cliente.dto;

import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.EstadoCliente;

public record ClienteResponse(
        Long id,
        String nombre,
        String identificacion,
        EstadoCliente estado,
        String telefono,
        String correo,
        String direccion,
        String servicios,
        String observaciones
) {
    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNombre(), c.getIdentificacion(), c.getEstado(), c.getTelefono(),
                c.getCorreo(), c.getDireccion(), c.getServicios(), c.getObservaciones());
    }
}
