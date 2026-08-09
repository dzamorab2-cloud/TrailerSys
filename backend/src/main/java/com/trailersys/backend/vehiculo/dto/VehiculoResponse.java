package com.trailersys.backend.vehiculo.dto;

import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;

public record VehiculoResponse(
        Long id,
        String placa,
        String marca,
        String modelo,
        String tipo,
        Integer anio,
        String color,
        EstadoVehiculo estado,
        Integer kilometraje,
        Integer capacidad,
        String observaciones,
        String foto
) {
    public static VehiculoResponse from(Vehiculo v) {
        return new VehiculoResponse(v.getId(), v.getPlaca(), v.getMarca(), v.getModelo(), v.getTipo(), v.getAnio(),
                v.getColor(), v.getEstado(), v.getKilometraje(), v.getCapacidad(), v.getObservaciones(), v.getFoto());
    }
}
