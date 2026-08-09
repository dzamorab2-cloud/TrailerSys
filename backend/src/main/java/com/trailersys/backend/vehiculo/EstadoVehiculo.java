package com.trailersys.backend.vehiculo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Las etiquetas coinciden exactamente con ESTADOS en js/vehiculos.js del
 * frontend, para que la API pueda consumirse sin remapear valores.
 */
public enum EstadoVehiculo {
    DISPONIBLE("Disponible"),
    EN_RUTA("En Ruta"),
    MANTENIMIENTO("Mantenimiento"),
    FUERA_DE_SERVICIO("Fuera de Servicio");

    private final String etiqueta;

    EstadoVehiculo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @JsonValue
    public String getEtiqueta() {
        return etiqueta;
    }

    @JsonCreator
    public static EstadoVehiculo desdeEtiqueta(String valor) {
        for (EstadoVehiculo estado : values()) {
            if (estado.etiqueta.equalsIgnoreCase(valor) || estado.name().equalsIgnoreCase(valor)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de vehiculo invalido: " + valor);
    }
}
