package com.trailersys.backend.pedido.dto;

import java.util.List;
import com.trailersys.backend.carga.dto.CargaResponse;
import com.trailersys.backend.seguimiento.dto.SeguimientoEventoResponse;
import com.trailersys.backend.viaje.dto.ViajeResponse;

public record DetallePedidoResponse(
        CargaResponse carga,
        ViajeResponse viaje,
        List<SeguimientoEventoResponse> eventos
) {}
