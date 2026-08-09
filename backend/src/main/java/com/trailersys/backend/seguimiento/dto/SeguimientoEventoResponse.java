package com.trailersys.backend.seguimiento.dto;

import java.time.LocalDateTime;

import com.trailersys.backend.seguimiento.SeguimientoEvento;
import com.trailersys.backend.seguimiento.TipoEvento;

public record SeguimientoEventoResponse(
        Long id,
        Long viajeId,
        String viajeOrigen,
        String viajeDestino,
        Long vehiculoId,
        String vehiculoPlaca,
        LocalDateTime fechaHora,
        TipoEvento evento,
        String ubicacion,
        String observacion
) {
    public static SeguimientoEventoResponse from(SeguimientoEvento e) {
        return new SeguimientoEventoResponse(
                e.getId(),
                e.getViaje().getId(), e.getViaje().getOrigen(), e.getViaje().getDestino(),
                e.getVehiculo().getId(), e.getVehiculo().getPlaca(),
                e.getFechaHora(), e.getEvento(), e.getUbicacion(), e.getObservacion());
    }
}
