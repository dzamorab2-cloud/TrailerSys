import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconstruye una base de datos completa a partir de la cadena de respaldos
 * (un COMPLETO + su cadena de INCREMENTALes) que crea RespaldoService desde
 * la aplicacion web (modulo Configuracion, solo Administrador).
 *
 * ESTO NUNCA SE EJECUTA DESDE LA APLICACION WEB. Es una utilidad de linea de
 * comandos aparte, a proposito: restaurar es una operacion destructiva sobre
 * la base destino y no debe poder dispararse con un clic.
 *
 * Como funciona:
 *   1. Lee (solo lectura) la tabla "respaldos" de la base "trailersys" real
 *      para reconstruir la cadena: sube por respaldoAnteriorId hasta llegar
 *      a un COMPLETO.
 *   2. Crea la base DESTINO (falla si ya existe - nunca sobrescribe algo que
 *      ya tenga datos sin que el operador la borre a mano primero).
 *   3. Restaura el COMPLETO con pg_restore (el dump real de pg_dump).
 *   4. Reproduce cada INCREMENTAL en orden: son las filas de la tabla
 *      "auditoria" capturadas despues del respaldo anterior. Para
 *      INSERT/UPDATE hace un upsert con jsonb_populate_record (reconstruye
 *      la fila completa a partir del JSON que genero el trigger de
 *      auditoria - mismas columnas, mismos tipos). Para DELETE, borra por id.
 *
 * SEGURIDAD: por defecto rechaza "trailersys" como base destino. Si de
 * verdad se necesita sobrescribir la base real (no recomendado - restaurar
 * directamente sobre produccion sin pasar antes por una base de prueba es
 * arriesgado), hay que editar este archivo y cambiar
 * PERMITIR_SOBRE_TRAILERSYS a true - una decision que debe tomarse leyendo
 * el codigo, nunca por accidente desde la linea de comandos.
 *
 * LIMITACION HONESTA: los incrementales solo capturan cambios de DATOS en
 * las 8 tablas auditadas (ver database/02_auditoria_indices.sql). Un cambio
 * de ESQUEMA (un ALTER TABLE hecho a mano después del último respaldo
 * completo) no queda en "auditoria" y este script no lo reproduce - para eso
 * hace falta generar un respaldo COMPLETO nuevo después de ese cambio.
 *
 * Compilar y ejecutar (ejemplo con el driver ya presente en el repositorio
 * Maven local de este proyecto - ajusta la version si difiere):
 *
 *   cd database
 *   javac RestaurarRespaldo.java
 *   java -cp ".;%USERPROFILE%\.m2\repository\org\postgresql\postgresql\42.7.11\postgresql-42.7.11.jar" ^
 *        RestaurarRespaldo trailersys_prueba 17
 *
 * Variables de entorno opcionales: DB_HOST (localhost), DB_PORT (5432),
 * DB_USER (postgres), DB_PASSWORD (obligatoria en la practica).
 */
public class RestaurarRespaldo {

    private static final boolean PERMITIR_SOBRE_TRAILERSYS = false;

    /** Mismo listado que el trigger trg_auditar en 02_auditoria_indices.sql. */
    private static final Set<String> TABLAS_AUDITADAS = Set.of(
            "usuarios", "vehiculos", "conductores", "clientes",
            "cargas", "viajes", "seguimiento_eventos", "mantenimientos");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Uso: java RestaurarRespaldo <baseDestino> <idRespaldo> [--pg-restore=ruta]");
            System.err.println("  baseDestino : nombre de la base de datos DESTINO (se crea nueva; nunca 'trailersys').");
            System.err.println("  idRespaldo  : id del respaldo (completo o incremental) hasta el que se quiere reconstruir.");
            System.exit(1);
            return;
        }

        String baseDestino = args[0];
        long idRespaldo = Long.parseLong(args[1]);
        String pgRestorePath = "C:/Program Files/PostgreSQL/18/bin/pg_restore.exe";
        for (String a : args) {
            if (a.startsWith("--pg-restore=")) {
                pgRestorePath = a.substring("--pg-restore=".length());
            }
        }

        if (baseDestino.equalsIgnoreCase("trailersys") && !PERMITIR_SOBRE_TRAILERSYS) {
            System.err.println("Por seguridad, este script rechaza 'trailersys' como base destino.");
            System.err.println("Si de verdad quieres sobrescribir la base real, edita RestaurarRespaldo.java");
            System.err.println("y cambia PERMITIR_SOBRE_TRAILERSYS a true. No es algo para hacer sin pensarlo.");
            System.exit(1);
            return;
        }

        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String usuario = System.getenv().getOrDefault("DB_USER", "postgres");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "postgres");

        List<Map<String, Object>> cadena = leerCadena(host, port, usuario, password, idRespaldo);

        System.out.println("Cadena a reconstruir (" + cadena.size() + " respaldo(s)):");
        for (Map<String, Object> f : cadena) {
            System.out.println("  #" + f.get("id") + " " + f.get("tipo") + " -> " + f.get("archivoRuta"));
        }
        if (cadena.isEmpty() || !"COMPLETO".equals(cadena.get(0).get("tipo"))) {
            throw new IllegalStateException("La cadena no empieza en un respaldo COMPLETO - no se puede reconstruir.");
        }

        crearBaseDestino(host, port, usuario, password, baseDestino);
        restaurarCompleto(pgRestorePath, host, port, usuario, password, baseDestino,
                (String) cadena.get(0).get("archivoRuta"));
        reproducirIncrementales(host, port, usuario, password, baseDestino, cadena);

        System.out.println("Reconstruccion completa en la base: " + baseDestino);
    }

    private static List<Map<String, Object>> leerCadena(String host, String port, String usuario,
            String password, long idInicial) throws SQLException {
        String urlOrigen = "jdbc:postgresql://" + host + ":" + port + "/trailersys";
        List<Map<String, Object>> cadena = new ArrayList<>();
        try (Connection origen = DriverManager.getConnection(urlOrigen, usuario, password)) {
            Long actual = idInicial;
            while (actual != null) {
                try (PreparedStatement ps = origen.prepareStatement(
                        "SELECT id, tipo, archivo_ruta, estado, respaldo_anterior_id FROM respaldos WHERE id = ?")) {
                    ps.setLong(1, actual);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalStateException("Respaldo no encontrado: " + actual);
                        }
                        String estado = rs.getString("estado");
                        if (!"COMPLETADO".equals(estado)) {
                            throw new IllegalStateException("El respaldo " + actual + " no esta COMPLETADO (estado=" + estado + ").");
                        }
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("id", rs.getLong("id"));
                        fila.put("tipo", rs.getString("tipo"));
                        fila.put("archivoRuta", rs.getString("archivo_ruta"));
                        cadena.add(0, fila);
                        long anterior = rs.getLong("respaldo_anterior_id");
                        actual = rs.wasNull() ? null : anterior;
                    }
                }
            }
        }
        return cadena;
    }

    private static void crearBaseDestino(String host, String port, String usuario, String password,
            String baseDestino) throws SQLException {
        if (!baseDestino.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Nombre de base destino invalido: " + baseDestino);
        }
        String urlPostgres = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        try (Connection admin = DriverManager.getConnection(urlPostgres, usuario, password);
                Statement st = admin.createStatement()) {
            // A proposito sin "IF NOT EXISTS": si ya existe, falla aqui - nunca se
            // restaura encima de una base que ya podria tener datos.
            st.execute("CREATE DATABASE \"" + baseDestino + "\"");
        }
        System.out.println("Base destino creada: " + baseDestino);
    }

    private static void restaurarCompleto(String pgRestorePath, String host, String port, String usuario,
            String password, String baseDestino, String archivoCompleto) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(pgRestorePath,
                "-h", host, "-p", port, "-U", usuario, "-d", baseDestino,
                "--no-owner", "--exit-on-error", archivoCompleto);
        pb.environment().put("PGPASSWORD", password);
        pb.inheritIO();
        Process proceso = pb.start();
        int codigo = proceso.waitFor();
        if (codigo != 0) {
            throw new IllegalStateException("pg_restore termino con codigo " + codigo);
        }
        System.out.println("Respaldo completo restaurado.");
    }

    private static void reproducirIncrementales(String host, String port, String usuario, String password,
            String baseDestino, List<Map<String, Object>> cadena) throws Exception {
        String urlDestino = "jdbc:postgresql://" + host + ":" + port + "/" + baseDestino;
        try (Connection destino = DriverManager.getConnection(urlDestino, usuario, password)) {
            destino.setAutoCommit(false);
            for (int i = 1; i < cadena.size(); i++) {
                Map<String, Object> f = cadena.get(i);
                if (!"INCREMENTAL".equals(f.get("tipo"))) {
                    continue;
                }
                String contenidoJson = Files.readString(Path.of((String) f.get("archivoRuta")));
                reproducirIncremental(destino, contenidoJson);
                System.out.println("Incremental #" + f.get("id") + " reproducido.");
            }
            destino.commit();
        }
    }

    private static void reproducirIncremental(Connection destino, String contenidoJson) throws SQLException {
        // "datosNuevos" en el JSON del incremental es un STRING que contiene JSON
        // (asi lo escribe RespaldoService: datos_nuevos::text del jsonb original),
        // no un objeto anidado - por eso hace falta ->> (extrae y desescapa el
        // texto) y no -> (que devolveria el string tal cual, como un jsonb
        // escalar de tipo string, y jsonb_populate_record fallaria con "cannot
        // invoke populate_composite on a scalar" al intentar tratarlo como fila).
        String sql = "SELECT elem->>'tabla' AS tabla, elem->>'operacion' AS operacion, "
                + "elem->>'registroId' AS registro_id, elem->>'datosNuevos' AS datos_nuevos "
                + "FROM jsonb_array_elements(?::jsonb) AS elem ORDER BY (elem->>'id')::bigint";
        try (PreparedStatement ps = destino.prepareStatement(sql)) {
            ps.setString(1, contenidoJson);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tabla = rs.getString("tabla");
                    if (!TABLAS_AUDITADAS.contains(tabla)) {
                        throw new IllegalStateException("Tabla no reconocida en el incremental: " + tabla);
                    }
                    aplicarFila(destino, tabla, rs.getString("operacion"), rs.getString("registro_id"),
                            rs.getString("datos_nuevos"));
                }
            }
        }
    }

    /** Nombres de columna (sin "id") de cada tabla ya consultados, para no repetir la consulta a information_schema por cada fila. */
    private static final Map<String, List<String>> COLUMNAS_CACHE = new java.util.HashMap<>();

    private static void aplicarFila(Connection destino, String tabla, String operacion, String registroId,
            String datosNuevos) throws SQLException {
        if ("DELETE".equals(operacion)) {
            try (PreparedStatement del = destino.prepareStatement("DELETE FROM " + tabla + " WHERE id = ?")) {
                del.setLong(1, Long.parseLong(registroId));
                del.executeUpdate();
            }
            return;
        }
        // NO se puede hacer "borrar y volver a insertar" aqui: si la fila es
        // referenciada por otra tabla (ej. clientes.id desde cargas.cliente_id,
        // que es el caso normal, no la excepcion), el DELETE revienta con una
        // violacion de llave foranea aunque la fila se fuera a reinsertar de
        // inmediato con el mismo id. Por eso es un UPSERT real con
        // ON CONFLICT ... DO UPDATE: jsonb_populate_record reconstruye la fila
        // completa usando los nombres y tipos reales de columna de la tabla
        // destino (mismo shape que to_jsonb(NEW), que genero el trigger de
        // auditoria), y si el id ya existe, actualiza esa misma fila en vez de
        // volver a crearla.
        List<String> columnas = columnasSinId(destino, tabla);
        String setClause = columnas.stream().map(c -> c + " = EXCLUDED." + c).collect(java.util.stream.Collectors.joining(", "));
        String sql = "INSERT INTO " + tabla + " SELECT * FROM jsonb_populate_record(NULL::" + tabla + ", ?::jsonb) "
                + "ON CONFLICT (id) DO UPDATE SET " + setClause;
        try (PreparedStatement ins = destino.prepareStatement(sql)) {
            ins.setString(1, datosNuevos);
            ins.executeUpdate();
        }
    }

    private static List<String> columnasSinId(Connection destino, String tabla) throws SQLException {
        List<String> cacheadas = COLUMNAS_CACHE.get(tabla);
        if (cacheadas != null) {
            return cacheadas;
        }
        List<String> columnas = new ArrayList<>();
        try (PreparedStatement ps = destino.prepareStatement(
                "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' "
                        + "AND table_name = ? AND column_name <> 'id' ORDER BY ordinal_position")) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columnas.add(rs.getString(1));
                }
            }
        }
        if (columnas.isEmpty()) {
            throw new IllegalStateException("No se encontraron columnas para la tabla '" + tabla + "' en la base destino.");
        }
        COLUMNAS_CACHE.put(tabla, columnas);
        return columnas;
    }
}
