package com.trailersys.backend.dashboard;

import java.util.List;

public record DashboardResponse(long vehiculos, long vehiculosDisponibles, long conductores,
        long conductoresActivos, long viajesEnCurso, long viajesProgramados,
        long mantenimientosVencidos, long entregasPendientes,
        List<ProximoViaje> proximosViajes) {
    public record ProximoViaje(Long id, String origen, String destino, String placa,
            String conductor, java.time.LocalDateTime fechaSalida) {}
}
