package com.trailersys.backend.respaldo.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.trailersys.backend.respaldo.ConfiguracionRespaldo;
import com.trailersys.backend.respaldo.FrecuenciaRespaldo;

public record ConfiguracionRespaldoResponse(
        boolean activo,
        FrecuenciaRespaldo frecuencia,
        LocalTime horaProgramada,
        DayOfWeek diaSemana,
        Integer diaMes) {
    public static ConfiguracionRespaldoResponse from(ConfiguracionRespaldo c) {
        return new ConfiguracionRespaldoResponse(c.isActivo(), c.getFrecuencia(), c.getHoraProgramada(),
                c.getDiaSemana(), c.getDiaMes());
    }
}
