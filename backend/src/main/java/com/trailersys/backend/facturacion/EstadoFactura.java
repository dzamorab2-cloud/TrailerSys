package com.trailersys.backend.facturacion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Estado de una factura interna (cuenta por cobrar). VENCIDA no se guarda:
 * se calcula al responder (PENDIENTE o PARCIAL con la fecha de vencimiento
 * ya pasada), para que no dependa de un proceso nocturno que la actualice.
 */
public enum EstadoFactura {
    PENDIENTE("Pendiente"),
    PARCIAL("Parcial"),
    PAGADA("Pagada"),
    VENCIDA("Vencida"),
    ANULADA("Anulada");

    private final String etiqueta;

    EstadoFactura(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @JsonValue
    public String getEtiqueta() {
        return etiqueta;
    }

    @JsonCreator
    public static EstadoFactura desdeEtiqueta(String valor) {
        for (EstadoFactura estado : values()) {
            if (estado.etiqueta.equalsIgnoreCase(valor) || estado.name().equalsIgnoreCase(valor)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de factura invalido: " + valor);
    }
}
