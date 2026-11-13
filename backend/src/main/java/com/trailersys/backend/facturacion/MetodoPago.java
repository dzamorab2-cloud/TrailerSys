package com.trailersys.backend.facturacion;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MetodoPago {
    TRANSFERENCIA("Transferencia"),
    DEPOSITO("Depósito"),
    CHEQUE("Cheque"),
    EFECTIVO("Efectivo"),
    TARJETA("Tarjeta");

    private final String etiqueta;

    MetodoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @JsonValue
    public String getEtiqueta() {
        return etiqueta;
    }

    @JsonCreator
    public static MetodoPago desdeEtiqueta(String valor) {
        for (MetodoPago metodo : values()) {
            if (metodo.etiqueta.equalsIgnoreCase(valor) || metodo.name().equalsIgnoreCase(valor)) {
                return metodo;
            }
        }
        throw new IllegalArgumentException("Metodo de pago invalido: " + valor);
    }
}
