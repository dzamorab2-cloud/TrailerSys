package com.trailersys.backend.seguimiento;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Las etiquetas coinciden exactamente con TIPO_ICON en js/seguimiento.js
 * del frontend, para que la API pueda consumirse sin remapear valores.
 */
public enum TipoEvento {
    SALIDA("Salida"),
    PARADA("Parada"),
    RETRASO("Retraso"),
    INCIDENTE("Incidente"),
    LLEGADA("Llegada"),
    OTRO("Otro");

    private final String etiqueta;

    TipoEvento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @JsonValue
    public String getEtiqueta() {
        return etiqueta;
    }

    @JsonCreator
    public static TipoEvento desdeEtiqueta(String valor) {
        for (TipoEvento tipo : values()) {
            if (tipo.etiqueta.equalsIgnoreCase(valor) || tipo.name().equalsIgnoreCase(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de evento invalido: " + valor);
    }
}
