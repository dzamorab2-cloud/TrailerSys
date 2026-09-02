package com.trailersys.backend.carga;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Las etiquetas coinciden exactamente con ESTADOS en js/cargas.js del
 * frontend, para que la API pueda consumirse sin remapear valores.
 */
public enum EstadoCarga {
    PENDIENTE("Pendiente"),
    ASIGNADA("Asignada"),
    EN_TRANSITO("En Tránsito"),
    ENTREGADA("Entregada"),
    /** El cliente cancelo su pedido mientras seguia Pendiente (ver PedidoClienteService.eliminarPedido()). */
    CANCELADA("Cancelada");

    private final String etiqueta;

    EstadoCarga(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    @JsonValue
    public String getEtiqueta() {
        return etiqueta;
    }

    @JsonCreator
    public static EstadoCarga desdeEtiqueta(String valor) {
        for (EstadoCarga estado : values()) {
            if (estado.etiqueta.equalsIgnoreCase(valor) || estado.name().equalsIgnoreCase(valor)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de carga invalido: " + valor);
    }
}
