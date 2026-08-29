package com.trailersys.backend.carga.dto;

import com.trailersys.backend.carga.Carga;
import com.trailersys.backend.carga.EstadoCarga;
import java.time.LocalDateTime;

public record CargaResponse(
        Long id,
        String descripcion,
        Long clienteId,
        String clienteNombre,
        String tipo,
        Integer peso,
        String origen,
        String destino,
        EstadoCarga estado,
        String observaciones,
        LocalDateTime fechaCreacion
) {
    public static CargaResponse from(Carga c) {
        return new CargaResponse(c.getId(), c.getDescripcion(), c.getCliente().getId(), c.getCliente().getNombre(),
                c.getTipo(), c.getPeso(), c.getOrigen(), c.getDestino(), c.getEstado(), c.getObservaciones(), c.getFechaCreacion());
    }
}
