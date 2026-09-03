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

    private static final String ESTADO_VIAJE_CASE =
            "CASE v.estado WHEN 'EN_CURSO' THEN 'En Curso' WHEN 'FINALIZADO' THEN 'Finalizado' "
            + "WHEN 'CANCELADO' THEN 'Cancelado' ELSE 'Programado' END";
    private static final String ESTADO_CARGA_CASE =
            "CASE c.estado WHEN 'EN_TRANSITO' THEN 'En Tránsito' WHEN 'ENTREGADA' THEN 'Entregada' "
            + "WHEN 'ASIGNADA' THEN 'Asignada' WHEN 'CANCELADA' THEN 'Cancelada' ELSE 'Pendiente' END";

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
        String textoBusqueda = search == null ? "" : search.trim();

        return textoBusqueda.isEmpty()
                ? listarSinBusqueda(pagina, limite, tipoNormalizado, estadoNormalizado)
                : listarConBusqueda(pagina, limite, tipoNormalizado, estadoNormalizado, textoBusqueda);
    }

    /**
     * Camino RAPIDO (sin texto de busqueda - el caso mas comun, con o sin
     * filtro de tipo/estado): antes, CUALQUIER consulta a /api/guias unia
     * viajes+clientes+conductores+vehiculos+cargas (250 mil filas) con
     * cargas+clientes+viajes+conductores+vehiculos (150 mil filas) COMPLETO,
     * ordenaba las ~400 mil filas resultantes y recien ahi tomaba 24 - medido
     * en vivo, ~2.97s para la vista por defecto (la que se ve al abrir el
     * modulo). "tipo" y "estado" no necesitan ningun JOIN para filtrarse (son
     * columnas propias de viajes/cargas), asi que se evaluan ANTES de unir
     * nada: si "tipo" descarta una de las dos ramas, esa rama ni se ejecuta;
     * si no, cada rama hace su propio ORDER BY id DESC LIMIT (offset+limite)
     * usando el indice de la llave primaria - convierte un JOIN+sort de todo
     * el catalogo en un JOIN de a lo sumo (offset+limite) filas por rama.
     * Tomar (offset+limite) de CADA rama (no solo "limite" repartido entre
     * las dos) es lo que garantiza que el top real tras unir y ordenar de
     * nuevo sea correcto, aunque las dos ramas tengan secuencias de id
     * completamente independientes entre si (es el mismo principio de un
     * merge de listas ya ordenadas: en el peor caso, todo el resultado sale
     * de una sola rama).
     */
    private GuiaPageResponse listarSinBusqueda(int pagina, int limite, String tipo, String estado) {
        boolean incluyeViajes = tipo.isEmpty() || tipo.equals("VIAJE");
        boolean incluyeCargas = tipo.isEmpty() || tipo.equals("CARGA");
        int ventana = (pagina * limite) + limite;

        long totalViajes = !incluyeViajes ? 0 : jdbc.queryForObject(
                "SELECT count(*) FROM viajes v WHERE (? = '' OR " + ESTADO_VIAJE_CASE + " = ?)",
                Long.class, estado, estado);
        long totalCargas = !incluyeCargas ? 0 : jdbc.queryForObject(
                "SELECT count(*) FROM cargas c WHERE (? = '' OR " + ESTADO_CARGA_CASE + " = ?)",
                Long.class, estado, estado);
        long total = totalViajes + totalCargas;

        List<String> ramas = new ArrayList<>();
        List<Object> parametros = new ArrayList<>();
        if (incluyeViajes) {
            ramas.add("""
                SELECT 'GUIA-VIA-' || lpad(vv.id::text, 6, '0') numero, 'VIAJE' tipo,
                       vv.id referencia_id, vv.fecha_salida fecha, COALESCE(ca.descripcion, 'Viaje sin carga') descripcion,
                       cl.nombre cliente, co.nombres conductor, ve.placa placa, vv.origen origen, vv.destino destino,
                       %s estado
                FROM (SELECT * FROM viajes v WHERE (? = '' OR %s = ?) ORDER BY v.id DESC LIMIT ?) vv
                     JOIN clientes cl ON cl.id=vv.cliente_id JOIN conductores co ON co.id=vv.conductor_id
                     JOIN vehiculos ve ON ve.id=vv.vehiculo_id LEFT JOIN cargas ca ON ca.id=vv.carga_id
                """.formatted(ESTADO_VIAJE_CASE.replace("v.estado", "vv.estado"), ESTADO_VIAJE_CASE));
            parametros.add(estado); parametros.add(estado); parametros.add(ventana);
        }
        if (incluyeCargas) {
            // Alias explicito en CADA columna (numero, tipo, referencia_id...):
            // si el filtro de tipo deja esta rama sola (sin la de VIAJE antes),
            // ya no hay un UNION que le preste sus nombres de columna a partir
            // de la primera rama - sin esto, la consulta de afuera fallaba con
            // "no existe la columna referencia_id" apenas se filtraba por
            // tipo=CARGA.
            ramas.add("""
                SELECT 'GUIA-CAR-' || lpad(cc.id::text, 6, '0') numero, 'CARGA' tipo, cc.id referencia_id, NULL::timestamp fecha,
                       cc.descripcion descripcion, cl.nombre cliente, co.nombres conductor, ve.placa placa, cc.origen origen, cc.destino destino,
                       %s estado
                FROM (SELECT * FROM cargas c WHERE (? = '' OR %s = ?) ORDER BY c.id DESC LIMIT ?) cc
                     JOIN clientes cl ON cl.id=cc.cliente_id
                     LEFT JOIN viajes uv ON uv.id = (SELECT MAX(v2.id) FROM viajes v2 WHERE v2.carga_id = cc.id)
                     LEFT JOIN conductores co ON co.id = uv.conductor_id
                     LEFT JOIN vehiculos ve ON ve.id = uv.vehiculo_id
                """.formatted(ESTADO_CARGA_CASE.replace("c.estado", "cc.estado"), ESTADO_CARGA_CASE));
            parametros.add(estado); parametros.add(estado); parametros.add(ventana);
        }

        String sql = "SELECT * FROM (" + String.join(" UNION ALL ", ramas) + ") g "
                + "ORDER BY referencia_id DESC, tipo DESC LIMIT ? OFFSET ?";
        parametros.add(limite); parametros.add(pagina * limite);

        List<GuiaListadoResponse> contenido = jdbc.query(sql, this::mapear, parametros.toArray());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / limite);
        return new GuiaPageResponse(contenido, total, totalPages, pagina, pagina == 0, pagina + 1 >= totalPages);
    }

    /**
     * Camino ORIGINAL, sin optimizar: cuando hay texto de busqueda, hace
     * falta el JOIN completo si o si (el texto se busca en cliente/
     * conductor/placa, que no existen en viajes/cargas por si solos), asi
     * que no hay forma de aplicar el mismo atajo sin arriesgar resultados
     * incorrectos (un JOIN+LIMIT antes de filtrar por texto podria dejar
     * afuera coincidencias reales que no estaban entre las mas recientes).
     * Sigue siendo mas lento a proposito - es un uso deliberado y menos
     * frecuente que simplemente abrir el listado.
     */
    private GuiaPageResponse listarConBusqueda(int pagina, int limite, String tipo, String estado, String search) {
        String patron = "%" + search.toLowerCase() + "%";
        String union = """
            SELECT 'GUIA-VIA-' || lpad(v.id::text, 6, '0') numero, 'VIAJE' tipo,
                   v.id referencia_id, v.fecha_salida fecha, COALESCE(ca.descripcion, 'Viaje sin carga') descripcion,
                   cl.nombre cliente, co.nombres conductor, ve.placa, v.origen, v.destino,
                   %s estado
            FROM viajes v JOIN clientes cl ON cl.id=v.cliente_id JOIN conductores co ON co.id=v.conductor_id
                 JOIN vehiculos ve ON ve.id=v.vehiculo_id LEFT JOIN cargas ca ON ca.id=v.carga_id
            UNION ALL
            SELECT 'GUIA-CAR-' || lpad(c.id::text, 6, '0'), 'CARGA', c.id, NULL::timestamp,
                   c.descripcion, cl.nombre, co.nombres, ve.placa, c.origen, c.destino,
                   %s
            FROM cargas c JOIN clientes cl ON cl.id=c.cliente_id
                 LEFT JOIN viajes uv ON uv.id = (SELECT MAX(v2.id) FROM viajes v2 WHERE v2.carga_id = c.id)
                 LEFT JOIN conductores co ON co.id = uv.conductor_id
                 LEFT JOIN vehiculos ve ON ve.id = uv.vehiculo_id
            """.formatted(ESTADO_VIAJE_CASE, ESTADO_CARGA_CASE);
        String where = """
            WHERE (? = '' OR tipo = ?) AND (? = '' OR estado = ?)
              AND lower(numero || ' ' || descripcion || ' ' || cliente || ' ' ||
                   coalesce(conductor,'') || ' ' || coalesce(placa,'') || ' ' || origen || ' ' || destino) LIKE ?
            """;
        List<Object> parametros = List.of(tipo, tipo, estado, estado, patron);
        long total = jdbc.queryForObject("SELECT count(*) FROM (" + union + ") g " + where, Long.class, parametros.toArray());
        String sql = "SELECT * FROM (" + union + ") g " + where
                + " ORDER BY referencia_id DESC, tipo DESC LIMIT ? OFFSET ?";
        List<Object> paginaParametros = new ArrayList<>(parametros);
        paginaParametros.add(limite);
        paginaParametros.add(pagina * limite);
        List<GuiaListadoResponse> contenido = jdbc.query(sql, this::mapear, paginaParametros.toArray());
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / limite);
        return new GuiaPageResponse(contenido, total, totalPages, pagina, pagina == 0, pagina + 1 >= totalPages);
    }

    private GuiaListadoResponse mapear(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp fecha = rs.getTimestamp("fecha");
        return new GuiaListadoResponse(rs.getString("numero"), rs.getString("tipo"), rs.getLong("referencia_id"),
                fecha == null ? null : fecha.toLocalDateTime(), rs.getString("descripcion"), rs.getString("cliente"),
                rs.getString("conductor"), rs.getString("placa"), rs.getString("origen"),
                rs.getString("destino"), rs.getString("estado"));
    }
}
