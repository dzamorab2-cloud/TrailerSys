package com.trailersys.backend.guia;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guias")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
public class GuiaController {
    private final JdbcTemplate jdbc;

    public GuiaController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public GuiaPageResponse listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String tipo,
            @RequestParam(defaultValue = "") String estado) {
        int pagina = Math.max(0, page);
        int limite = Math.min(100, Math.max(12, size));
        String tipoNormalizado = tipo == null ? "" : tipo.trim().toUpperCase();
        String estadoNormalizado = estado == null ? "" : estado.trim();
        String patron = "%" + (search == null ? "" : search.trim().toLowerCase()) + "%";

        String union = """
            SELECT 'GUIA-VIA-' || lpad(v.id::text, 6, '0') numero, 'VIAJE' tipo,
                   v.id referencia_id, v.fecha_salida fecha, COALESCE(ca.descripcion, 'Viaje sin carga') descripcion,
                   cl.nombre cliente, co.nombres conductor, ve.placa, v.origen, v.destino,
                   CASE v.estado WHEN 'EN_CURSO' THEN 'En Curso' WHEN 'FINALIZADO' THEN 'Finalizado'
                     WHEN 'CANCELADO' THEN 'Cancelado' ELSE 'Programado' END estado
            FROM viajes v JOIN clientes cl ON cl.id=v.cliente_id JOIN conductores co ON co.id=v.conductor_id
                 JOIN vehiculos ve ON ve.id=v.vehiculo_id LEFT JOIN cargas ca ON ca.id=v.carga_id
            UNION ALL
            SELECT 'GUIA-CAR-' || lpad(c.id::text, 6, '0'), 'CARGA', c.id, NULL::timestamp,
                   c.descripcion, cl.nombre, co.nombres, ve.placa, c.origen, c.destino,
                   CASE c.estado WHEN 'EN_TRANSITO' THEN 'En Tránsito' WHEN 'ENTREGADA' THEN 'Entregada'
                     WHEN 'ASIGNADA' THEN 'Asignada' WHEN 'CANCELADA' THEN 'Cancelada' ELSE 'Pendiente' END
            FROM cargas c JOIN clientes cl ON cl.id=c.cliente_id
                 -- El conductor/placa de una carga no son un dato propio: se toman
                 -- del ultimo viaje que la transporto (mismo criterio que
                 -- ViajeService.buscarUltimoPorCarga: el viaje con mayor id para
                 -- esa carga). Antes esta rama del UNION traia NULL a proposito
                 -- para "conductor" y "placa" - eso dejaba el listado de Guias
                 -- mostrando siempre "Pendiente de asignar / Sin vehiculo" para
                 -- TODAS las cargas, incluso las que ya iban en camino con un
                 -- conductor y vehiculo asignados (la propia guia, al abrirla,
                 -- si mostraba el dato bien porque hace su propia consulta -
                 -- pero la fila de la tabla no).
                 --
                 -- Se llega al mismo "ultimo viaje de esta carga" sin LATERAL
                 -- (Postgres lo soporta, pero H2 no lo reconoce ni en modo
                 -- PostgreSQL - con LATERAL este endpoint quedaba imposible de
                 -- probar contra la base de pruebas): una subconsulta escalar
                 -- correlacionada (MAX(id), siempre un solo valor) que se une
                 -- de vuelta a viajes, en vez de una subconsulta lateral por
                 -- cada fila.
                 LEFT JOIN viajes uv ON uv.id = (SELECT MAX(v2.id) FROM viajes v2 WHERE v2.carga_id = c.id)
                 LEFT JOIN conductores co ON co.id = uv.conductor_id
                 LEFT JOIN vehiculos ve ON ve.id = uv.vehiculo_id
            """;
        String where = """
            WHERE (? = '' OR tipo = ?) AND (? = '' OR estado = ?)
              AND (? = '%%' OR lower(numero || ' ' || descripcion || ' ' || cliente || ' ' ||
                   coalesce(conductor,'') || ' ' || coalesce(placa,'') || ' ' || origen || ' ' || destino) LIKE ?)
            """;
        List<Object> parametros = List.of(tipoNormalizado, tipoNormalizado, estadoNormalizado, estadoNormalizado, patron, patron);
        long total = jdbc.queryForObject("SELECT count(*) FROM (" + union + ") g " + where, Long.class, parametros.toArray());
        String sql = "SELECT * FROM (" + union + ") g " + where
                + " ORDER BY referencia_id DESC, tipo DESC LIMIT ? OFFSET ?";
        List<Object> paginaParametros = new ArrayList<>(parametros);
        paginaParametros.add(limite);
        paginaParametros.add(pagina * limite);
        List<GuiaListadoResponse> contenido = jdbc.query(sql, (rs, row) -> {
            Timestamp fecha = rs.getTimestamp("fecha");
            return new GuiaListadoResponse(rs.getString("numero"), rs.getString("tipo"), rs.getLong("referencia_id"),
                    fecha == null ? null : fecha.toLocalDateTime(), rs.getString("descripcion"), rs.getString("cliente"),
                    rs.getString("conductor"), rs.getString("placa"), rs.getString("origen"),
                    rs.getString("destino"), rs.getString("estado"));
        }, paginaParametros.toArray());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / limite);
        return new GuiaPageResponse(contenido, total, totalPages, pagina, pagina == 0, pagina + 1 >= totalPages);
    }
}
