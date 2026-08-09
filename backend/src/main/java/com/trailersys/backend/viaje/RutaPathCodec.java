package com.trailersys.backend.viaje;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailersys.backend.viaje.dto.PuntoRuta;

/**
 * Convierte el trazado de la ruta (lista de puntos lat/lng) hacia y desde
 * el TEXT que se guarda en Viaje.rutaPath. Aislado en una clase propia
 * para que ViajeService (al guardar) y ViajeResponse (al leer, desde el
 * subpaquete dto) usen exactamente la misma logica de serializacion.
 */
public final class RutaPathCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RutaPathCodec() {
    }

    public static String serializar(List<PuntoRuta> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(path);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el trazado de la ruta.", e);
        }
    }

    public static List<PuntoRuta> deserializar(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<PuntoRuta>>() {
            });
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
