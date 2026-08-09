package com.trailersys.backend.mantenimiento.dto;

import java.time.LocalDate;

import com.trailersys.backend.mantenimiento.Mantenimiento;
import com.trailersys.backend.mantenimiento.TipoMantenimiento;

public record MantenimientoResponse(
        Long id,
        Long vehiculoId,
        String vehiculoPlaca,
        TipoMantenimiento tipo,
        LocalDate fecha,
        Integer kilometraje,
        Double costo,
        LocalDate proximoServicio,
        boolean proximoServicioVencido,
        String descripcion
) {
    public static MantenimientoResponse from(Mantenimiento m) {
        boolean vencido = m.getProximoServicio() != null && m.getProximoServicio().isBefore(LocalDate.now());
        return new MantenimientoResponse(m.getId(), m.getVehiculo().getId(), m.getVehiculo().getPlaca(),
                m.getTipo(), m.getFecha(), m.getKilometraje(), m.getCosto(), m.getProximoServicio(), vencido,
                m.getDescripcion());
    }
}
