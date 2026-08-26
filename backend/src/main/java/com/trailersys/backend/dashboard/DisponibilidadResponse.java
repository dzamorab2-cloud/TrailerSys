package com.trailersys.backend.dashboard;

public record DisponibilidadResponse(
        long vehiculosDisponibles, long vehiculosEnRuta, long vehiculosMantenimiento, long vehiculosFueraServicio,
        long conductoresDisponibles, long conductoresEnRuta, long conductoresDescanso, long conductoresInactivos,
        long licenciasVencidas) {
}
