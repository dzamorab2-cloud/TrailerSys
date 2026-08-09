package com.trailersys.backend.viaje.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/**
 * Espejo de la "ruta" que calcula js/map-service.js en el frontend
 * (Nominatim + OSRM): coordenadas de origen/destino, distancia,
 * duracion estimada y el trazado para dibujar la polilinea en Leaflet.
 * El backend no calcula nada de esto, solo lo persiste.
 */
public record RutaDto(
        @NotNull Double origenLat,
        @NotNull Double origenLng,
        @NotNull Double destinoLat,
        @NotNull Double destinoLng,
        @NotNull Double distanciaKm,
        @NotNull Double duracionMin,
        List<PuntoRuta> path
) {
}
