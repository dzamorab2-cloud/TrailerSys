package com.trailersys.backend.mantenimiento;

import java.util.List;

public record MantenimientoReporteResponse(
        long total, double costoTotal, double costoPromedio, long preventivos, long correctivos,
        long vencidos, long vehiculosDisponibles, long vehiculosMantenimiento, long vehiculosFueraServicio,
        List<VehiculoCosto> costosPorVehiculo, List<TipoFrecuencia> mantenimientosFrecuentes) {
    public record VehiculoCosto(String placa, long cantidad, double costo, long diasFueraServicio) {}
    public record TipoFrecuencia(String tipo, long cantidad) {}
}
