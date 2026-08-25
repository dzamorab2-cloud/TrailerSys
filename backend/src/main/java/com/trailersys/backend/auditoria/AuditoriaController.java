package com.trailersys.backend.auditoria;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AuditoriaController {
    private final JdbcTemplate jdbc;

    public AuditoriaController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public AuditoriaPageResponse listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String tabla,
            @RequestParam(required = false) String operacion,
            @RequestParam(required = false) String search) {
        int pagina = Math.max(0, page);
        int tamano = Math.min(100, Math.max(10, size));
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (tabla != null && !tabla.isBlank()) { where.append(" AND tabla = ?"); args.add(tabla); }
        if (operacion != null && !operacion.isBlank()) { where.append(" AND operacion = ?"); args.add(operacion); }
        if (search != null && !search.isBlank()) {
            where.append(" AND (coalesce(usuario_app, '') ILIKE ? OR usuario_bd ILIKE ? OR coalesce(registro_id, '') ILIKE ?)");
            String patron = "%" + search.trim() + "%";
            args.add(patron); args.add(patron); args.add(patron);
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM auditoria" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(tamano); pageArgs.add(pagina * tamano);
        List<AuditoriaResponse> content = jdbc.query("SELECT id, fecha_hora, usuario_bd, usuario_app, operacion, tabla, registro_id, datos_anteriores::text, datos_nuevos::text, cliente_ip::text FROM auditoria" + where + " ORDER BY fecha_hora DESC LIMIT ? OFFSET ?",
                this::mapear, pageArgs.toArray());
        int pages = total == null ? 0 : (int) Math.ceil(total / (double) tamano);
        return new AuditoriaPageResponse(content, total == null ? 0 : total, pagina, tamano, pages);
    }

    private AuditoriaResponse mapear(ResultSet rs, int row) throws SQLException {
        return new AuditoriaResponse(rs.getLong("id"), rs.getObject("fecha_hora", java.time.OffsetDateTime.class),
                rs.getString("usuario_bd"), rs.getString("usuario_app"), rs.getString("operacion"),
                rs.getString("tabla"), rs.getString("registro_id"), rs.getString("datos_anteriores"),
                rs.getString("datos_nuevos"), rs.getString("cliente_ip"));
    }
}
