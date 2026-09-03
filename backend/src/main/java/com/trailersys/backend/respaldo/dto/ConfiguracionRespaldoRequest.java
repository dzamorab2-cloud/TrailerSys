package com.trailersys.backend.respaldo.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.trailersys.backend.respaldo.FrecuenciaRespaldo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ConfiguracionRespaldoRequest(
        boolean activo,
        @NotNull(message = "La frecuencia es obligatoria") FrecuenciaRespaldo frecuencia,
        @NotNull(message = "La hora programada es obligatoria") LocalTime horaProgramada,
        DayOfWeek diaSemana,
        @Min(value = 1, message = "El día del mes debe estar entre 1 y 31")
        @Max(value = 31, message = "El día del mes debe estar entre 1 y 31") Integer diaMes) {
}
