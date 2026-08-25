package com.trailersys.backend.auditoria;

import java.time.OffsetDateTime;

public record AuditoriaResponse(Long id, OffsetDateTime fechaHora, String usuarioBd,
        String usuarioApp, String operacion, String tabla, String registroId,
        String datosAnteriores, String datosNuevos, String clienteIp) {
}
