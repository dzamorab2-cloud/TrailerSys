package com.trailersys.backend.facturacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnularFacturaRequest(
        @NotBlank(message = "Indica el motivo de la anulación")
        @Size(max = 500, message = "El motivo no puede superar 500 caracteres") String motivo) {
}
