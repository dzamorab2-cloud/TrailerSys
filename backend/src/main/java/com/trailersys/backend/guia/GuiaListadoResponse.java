package com.trailersys.backend.guia;

import java.time.LocalDateTime;

public record GuiaListadoResponse(
        String numero, String tipo, Long referenciaId, LocalDateTime fecha,
        String descripcion, String cliente, String conductor, String placa,
        String origen, String destino, String estado) {
}
