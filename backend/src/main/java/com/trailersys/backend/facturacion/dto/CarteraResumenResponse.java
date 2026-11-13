package com.trailersys.backend.facturacion.dto;

import java.math.BigDecimal;

/** Indicadores de la cartera para la cabecera del modulo. */
public record CarteraResumenResponse(
        BigDecimal porCobrar,
        BigDecimal vencido,
        long facturasAbiertas,
        long facturasVencidas,
        BigDecimal facturadoMes,
        BigDecimal cobradoMes) {
}
