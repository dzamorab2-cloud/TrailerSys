package com.trailersys.backend.dashboard;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "isAuthenticated()" bastaba mientras todos los roles autenticados eran
 * personal interno. Ahora que CLIENTE y CONDUCTOR tambien inician sesion
 * (autoservicio, paquetes "pedido" y "operaciones") hay que excluirlos
 * explicitamente: este resumen expone origen/destino/placa/conductor de los
 * proximos viajes de TODA la operacion, no solo los propios. El conductor
 * tiene su propio resumen personalizado en GET /api/mis-viajes/resumen.
 */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR','MANTENIMIENTO','SUPERVISOR')")
public class DashboardController {
    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/resumen")
    public DashboardResponse resumen() {
        // "estado='PROGRAMADO'" solo no basta: un viaje puede seguir en ese
        // estado aunque su fecha_salida ya haya pasado (el propio panel de
        // Alertas operativas de Seguimiento marca justamente ese caso como
        // alerta). Sin el filtro de fecha, ese viaje atrasado ordenaba
        // primero (fecha_salida ASC) y aparecia como el "proximo viaje" en
        // vez de uno realmente futuro.
        List<DashboardResponse.ProximoViaje> proximos = jdbc.query("""
                SELECT v.id, v.origen, v.destino, ve.placa, c.nombres, v.fecha_salida
                FROM viajes v JOIN vehiculos ve ON ve.id=v.vehiculo_id
                JOIN conductores c ON c.id=v.conductor_id
                WHERE v.estado='PROGRAMADO' AND v.fecha_salida >= NOW() ORDER BY v.fecha_salida ASC LIMIT 5
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

    /**
     * Viajes por dia de los ultimos 7 dias (hoy incluido), para la grafica de
     * tendencia del Dashboard. Una consulta por dia (parametrizada, no un
     * "date_trunc" especifico de Postgres) para que funcione igual en la
     * suite de pruebas (H2) y en produccion.
     */
    @GetMapping("/tendencia")
    public TendenciaResponse tendencia() {
        DateTimeFormatter etiquetaFmt = DateTimeFormatter.ofPattern("EEE d", new Locale("es", "EC"));
        List<TendenciaResponse.Punto> puntos = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate dia = LocalDate.now().minusDays(i);
            long cantidad = contarViajesEntre(dia.atStartOfDay(), dia.plusDays(1).atStartOfDay());
            String etiqueta = dia.format(etiquetaFmt);
            puntos.add(new TendenciaResponse.Punto(etiqueta.substring(0, 1).toUpperCase() + etiqueta.substring(1), cantidad));
        }
        return new TendenciaResponse(puntos);
    }

    private long contarViajesEntre(LocalDateTime desde, LocalDateTime hasta) {
        Long valor = jdbc.queryForObject(
                "SELECT count(*) FROM viajes WHERE fecha_salida >= ? AND fecha_salida < ?",
                Long.class, Timestamp.valueOf(desde), Timestamp.valueOf(hasta));
        return valor == null ? 0 : valor;
    }
}
