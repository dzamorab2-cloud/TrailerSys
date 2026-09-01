package com.trailersys.backend.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DisponibilidadController {
    private final JdbcTemplate jdbc;
    public DisponibilidadController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private long contar(String sql, Object... params) {
        Long valor = jdbc.queryForObject(sql, Long.class, params);
        return valor == null ? 0 : valor;
    }

    @GetMapping("/disponibilidad")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR','SUPERVISOR')")
    public DisponibilidadResponse obtener() {
        return new DisponibilidadResponse(
                contar("SELECT count(*) FROM vehiculos WHERE estado='DISPONIBLE'"),
                contar("SELECT count(*) FROM vehiculos WHERE estado='EN_RUTA'"),
                contar("SELECT count(*) FROM vehiculos WHERE estado='MANTENIMIENTO'"),
                contar("SELECT count(*) FROM vehiculos WHERE estado='FUERA_DE_SERVICIO'"),
                contar("SELECT count(*) FROM conductores WHERE estado='DISPONIBLE'"),
                contar("SELECT count(*) FROM conductores WHERE estado='EN_RUTA'"),
                contar("SELECT count(*) FROM conductores WHERE estado='DESCANSO'"),
                contar("SELECT count(*) FROM conductores WHERE estado='INACTIVO'"),
                contar("SELECT count(*) FROM conductores WHERE licencia_vencimiento < CURRENT_DATE"));
    }
}
