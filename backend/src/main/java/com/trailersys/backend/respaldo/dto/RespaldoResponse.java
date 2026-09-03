package com.trailersys.backend.respaldo.dto;

import java.time.LocalDateTime;

import com.trailersys.backend.respaldo.EstadoRespaldo;
import com.trailersys.backend.respaldo.Respaldo;
import com.trailersys.backend.respaldo.TipoRespaldo;

public record RespaldoResponse(
        Long id,
        TipoRespaldo tipo,
        LocalDateTime fechaHora,
        Long tamanoBytes,
        EstadoRespaldo estado,
        String mensajeError,
        Long respaldoAnteriorId,
        Integer registrosCapturados,
        String generadoPor
) {
    public static RespaldoResponse from(Respaldo r) {
        return new RespaldoResponse(r.getId(), r.getTipo(), r.getFechaHora(), r.getTamanoBytes(),
                r.getEstado(), r.getMensajeError(), r.getRespaldoAnteriorId(), r.getRegistrosCapturados(),
                r.getGeneradoPor());
    }
}
