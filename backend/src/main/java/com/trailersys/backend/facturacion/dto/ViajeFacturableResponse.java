package com.trailersys.backend.facturacion.dto;

import java.time.LocalDateTime;

import com.trailersys.backend.viaje.Viaje;

public record ViajeFacturableResponse(
        Long id, String origen, String destino, LocalDateTime fechaSalida,
        Double distanciaKm, String vehiculoPlaca, String cargaDescripcion) {

    public static ViajeFacturableResponse from(Viaje v) {
        return new ViajeFacturableResponse(v.getId(), v.getOrigen(), v.getDestino(), v.getFechaSalida(),
                v.getRutaDistanciaKm(), v.getVehiculo().getPlaca(),
                v.getCarga() == null ? null : v.getCarga().getDescripcion());
    }
}
