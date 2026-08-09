package com.trailersys.backend.conductor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Las etiquetas coinciden exactamente con ESTADOS en js/conductores.js
 * del frontend, para que la API pueda consumirse sin remapear valores.
 */
public enum EstadoConductor {
    DISPONIBLE("Disponible"),
    EN_RUTA("En Ruta"),
    DESCANSO("Descanso"),
    INACTIVO("Inactivo");

    private final String etiqueta;

    EstadoConductor(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @JsonValue
    public String getEtiqueta() {
        return etiqueta;
    }

    @JsonCreator
    public static EstadoConductor desdeEtiqueta(String valor) {
        for (EstadoConductor estado : values()) {
            if (estado.etiqueta.equalsIgnoreCase(valor) || estado.name().equalsIgnoreCase(valor)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de conductor invalido: " + valor);
    }
}
