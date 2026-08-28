package com.trailersys.backend.dashboard;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "isAuthenticated()" bastaba mientras todos los roles autenticados eran
 * personal interno. Ahora que CLIENTE tambien inicia sesion (autoservicio
 * de pedidos, paquete "pedido") hay que excluirlo explicitamente: este
 * resumen expone origen/destino/placa/conductor de los proximos viajes de
 * TODOS los clientes, no solo del propio.
 */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR','MANTENIMIENTO','CONDUCTOR','SUPERVISOR')")
public class DashboardController {
    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/resumen")
    public DashboardResponse resumen() {
        List<DashboardResponse.ProximoViaje> proximos = jdbc.query("""
                SELECT v.id, v.origen, v.destino, ve.placa, c.nombres, v.fecha_salida
                FROM viajes v JOIN vehiculos ve ON ve.id=v.vehiculo_id
                JOIN conductores c ON c.id=v.conductor_id
                WHERE v.estado='PROGRAMADO' ORDER BY v.fecha_salida ASC LIMIT 5
                """, (rs, row) -> new DashboardResponse.ProximoViaje(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toLocalDateTime()));
        return new DashboardResponse(count("vehiculos", null), count("vehiculos", "estado='DISPONIBLE'"),
                count("conductores", null), count("conductores", "estado IN ('DISPONIBLE','EN_RUTA')"),
                count("viajes", "estado='EN_CURSO'"), count("viajes", "estado='PROGRAMADO'"),
                count("mantenimientos", "proximo_servicio < CURRENT_DATE"),
                count("viajes", "entrega_confirmada=true AND entrega_validada=false"), proximos);
    }

    private long count(String table, String condition) {
        String sql = "SELECT count(*) FROM " + table + (condition == null ? "" : " WHERE " + condition);
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
