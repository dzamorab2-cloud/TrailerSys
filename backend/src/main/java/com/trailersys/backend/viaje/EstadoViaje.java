package com.trailersys.backend.viaje;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Las etiquetas coinciden exactamente con ESTADOS en js/viajes.js del
 * frontend, para que la API pueda consumirse sin remapear valores.
 */
public enum EstadoViaje {
    PROGRAMADO("Programado"),
    EN_CURSO("En Curso"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String etiqueta;

    EstadoViaje(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @JsonValue
    public String getEtiqueta() {
        return etiqueta;
    }

    @JsonCreator
    public static EstadoViaje desdeEtiqueta(String valor) {
        for (EstadoViaje estado : values()) {
            if (estado.etiqueta.equalsIgnoreCase(valor) || estado.name().equalsIgnoreCase(valor)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de viaje invalido: " + valor);
    }
}
