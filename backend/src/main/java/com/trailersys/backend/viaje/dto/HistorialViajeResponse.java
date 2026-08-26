package com.trailersys.backend.viaje.dto;

import java.time.OffsetDateTime;

public record HistorialViajeResponse(OffsetDateTime fecha, String tipo, String titulo, String detalle) {
}
