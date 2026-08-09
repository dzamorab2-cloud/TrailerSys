package com.trailersys.backend.viaje.dto;

import java.time.LocalDateTime;

import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.RutaPathCodec;
import com.trailersys.backend.viaje.Viaje;

public record ViajeResponse(
        Long id,
        Long vehiculoId,
        String vehiculoPlaca,
        Long conductorId,
        String conductorNombres,
        Long clienteId,
        String clienteNombre,
        Long cargaId,
        String cargaDescripcion,
        String origen,
        String destino,
        LocalDateTime fechaSalida,
        EstadoViaje estado,
        String observaciones,
        RutaDto ruta
) {
    public static ViajeResponse from(Viaje v) {
        RutaDto ruta = null;
        if (v.getRutaDistanciaKm() != null) {
            ruta = new RutaDto(
                    v.getRutaOrigenLat(), v.getRutaOrigenLng(), v.getRutaDestinoLat(), v.getRutaDestinoLng(),
                    v.getRutaDistanciaKm(), v.getRutaDuracionMin(), RutaPathCodec.deserializar(v.getRutaPath()));
        }

        return new ViajeResponse(
                v.getId(),
                v.getVehiculo().getId(), v.getVehiculo().getPlaca(),
                v.getConductor().getId(), v.getConductor().getNombres(),
                v.getCliente().getId(), v.getCliente().getNombre(),
                v.getCarga() != null ? v.getCarga().getId() : null,
                v.getCarga() != null ? v.getCarga().getDescripcion() : null,
                v.getOrigen(), v.getDestino(), v.getFechaSalida(), v.getEstado(), v.getObservaciones(), ruta);
    }
}
