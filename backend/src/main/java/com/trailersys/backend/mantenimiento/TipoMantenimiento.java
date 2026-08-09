package com.trailersys.backend.mantenimiento;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Las etiquetas coinciden exactamente con TIPOS en js/mantenimientos.js
 * del frontend, para que la API pueda consumirse sin remapear valores.
 */
public enum TipoMantenimiento {
    PREVENTIVO("Preventivo"),
    CORRECTIVO("Correctivo");

    private final String etiqueta;

    TipoMantenimiento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @JsonValue
    public String getEtiqueta() {
        return etiqueta;
    }

    @JsonCreator
    public static TipoMantenimiento desdeEtiqueta(String valor) {
        for (TipoMantenimiento tipo : values()) {
            if (tipo.etiqueta.equalsIgnoreCase(valor) || tipo.name().equalsIgnoreCase(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de mantenimiento invalido: " + valor);
    }
}
