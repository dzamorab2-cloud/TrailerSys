package com.trailersys.backend.pedido.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolverReclamoRequest(@NotBlank String respuesta, String estado) {}
