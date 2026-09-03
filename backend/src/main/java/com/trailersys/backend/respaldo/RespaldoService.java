package com.trailersys.backend.respaldo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.respaldo.dto.ConfiguracionRespaldoRequest;

/**
 * COMPLETO: dump real de pg_dump (formato -Fc, restaurable con pg_restore) -
 * no es una simulacion. INCREMENTAL: no vuelve a copiar la base entera, solo
 * exporta las filas de "auditoria" mas recientes que el ultimo respaldo
 * COMPLETADO (cualquier tipo) - es decir, literalmente lo que cambio desde
 * entonces. Encadenar el completo con su cadena de incrementales (ver
 * respaldoAnteriorId en Respaldo) es lo que permite reconstruir la base
 * completa sin haber vuelto a copiarla entera en cada respaldo (ver
 * database/RestaurarRespaldo.java).
 *
 * Limitacion honesta: la tabla auditoria solo registra INSERT/UPDATE/DELETE
 * de las 8 tablas operativas (ver database/02_auditoria_indices.sql) - un
 * cambio de esquema (ALTER TABLE) no queda ahi y no lo captura ningun
 * incremental. Para eso hace falta un respaldo COMPLETO nuevo.
 */
@Service
public class RespaldoService {

    private static final DateTimeFormatter FORMATO_ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final RespaldoRepository respaldoRepository;
    private final ConfiguracionRespaldoRepository configuracionRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private final String directorio;
    private final String pgDumpPath;
    private final String dbHost;
    private final String dbPort;
    private final String dbNombre;
    private final String dbUsuario;
    private final String dbPassword;

    public RespaldoService(RespaldoRepository respaldoRepository,
            ConfiguracionRespaldoRepository configuracionRepository,
            JdbcTemplate jdbc, ObjectMapper objectMapper,
            @Value("${trailersys.respaldos.directorio}") String directorio,
            @Value("${trailersys.respaldos.pg-dump}") String pgDumpPath,
            @Value("${trailersys.respaldos.db-host}") String dbHost,
            @Value("${trailersys.respaldos.db-port}") String dbPort,
            @Value("${trailersys.respaldos.db-nombre}") String dbNombre,
            @Value("${spring.datasource.username}") String dbUsuario,
            @Value("${spring.datasource.password}") String dbPassword) {
        this.respaldoRepository = respaldoRepository;
        this.configuracionRepository = configuracionRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.directorio = directorio;
        this.pgDumpPath = pgDumpPath;
        this.dbHost = dbHost;
        this.dbPort = dbPort;
        this.dbNombre = dbNombre;
        this.dbUsuario = dbUsuario;
        this.dbPassword = dbPassword;
    }

    public List<Respaldo> listar() {
        return respaldoRepository.findAllByOrderByFechaHoraDesc();
    }

    public Respaldo obtener(Long id) {
        return respaldoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Respaldo no encontrado: " + id));
    }

    /** Fila unica de configuracion - se crea con valores por defecto (inactivo) la primera vez que se pide. */
    @Transactional
    public ConfiguracionRespaldo obtenerOCrearConfiguracion() {
        return configuracionRepository.findAll().stream().findFirst()
                .orElseGet(() -> configuracionRepository.save(new ConfiguracionRespaldo()));
    }

    @Transactional
    public ConfiguracionRespaldo actualizarConfiguracion(ConfiguracionRespaldoRequest request) {
        if (request.frecuencia() == FrecuenciaRespaldo.SEMANAL && request.diaSemana() == null) {
            throw new IllegalArgumentException("Selecciona el día de la semana para la frecuencia semanal.");
        }
        if (request.frecuencia() == FrecuenciaRespaldo.MENSUAL && request.diaMes() == null) {
            throw new IllegalArgumentException("Selecciona el día del mes para la frecuencia mensual.");
        }
        ConfiguracionRespaldo configuracion = obtenerOCrearConfiguracion();
        configuracion.setActivo(request.activo());
        configuracion.setFrecuencia(request.frecuencia());
        configuracion.setHoraProgramada(request.horaProgramada());
        // Solo se guarda el campo relevante a la frecuencia elegida - el otro se
        // deja como venía, así no se pierde si el usuario alterna de ida y vuelta
        // entre SEMANAL y MENSUAL mientras decide.
        if (request.frecuencia() == FrecuenciaRespaldo.SEMANAL) {
            configuracion.setDiaSemana(request.diaSemana());
        }
        if (request.frecuencia() == FrecuenciaRespaldo.MENSUAL) {
            configuracion.setDiaMes(request.diaMes());
        }
        return configuracionRepository.save(configuracion);
    }

    /**
     * pg_dump corre de forma sincrona (bloquea la peticion hasta terminar) -
     * razonable para el tamaño de esta base de demostracion. Para una base de
     * produccion real y pesada, esto se ejecutaria en background (@Async o un
     * job aparte) para no dejar la peticion HTTP esperando.
     */
    public Respaldo crearCompleto(String generadoPor) {
        Respaldo respaldo = new Respaldo(TipoRespaldo.COMPLETO, generadoPor, null);
        respaldo = respaldoRepository.save(respaldo);

        try {
            Path carpeta = Path.of(directorio);
            Files.createDirectories(carpeta);
            String nombreArchivo = "completo-" + LocalDateTime.now().format(FORMATO_ARCHIVO) + ".dump";
            Path archivo = carpeta.resolve(nombreArchivo);

            ProcessBuilder pb = new ProcessBuilder(pgDumpPath,
                    "-h", dbHost, "-p", dbPort, "-U", dbUsuario,
                    "-Fc", "-f", archivo.toAbsolutePath().toString(), dbNombre);
            pb.environment().put("PGPASSWORD", dbPassword);
            pb.redirectErrorStream(true);

            Process proceso = pb.start();
            String salida = new String(proceso.getInputStream().readAllBytes());
            boolean termino = proceso.waitFor(30, java.util.concurrent.TimeUnit.MINUTES);

            if (!termino) {
                proceso.destroyForcibly();
                throw new IllegalStateException("pg_dump no terminó dentro del tiempo esperado.");
            }
            if (proceso.exitValue() != 0) {
                throw new IllegalStateException("pg_dump terminó con error: " + salida);
            }

            respaldo.setArchivoRuta(archivo.toAbsolutePath().toString());
            respaldo.setTamanoBytes(Files.size(archivo));
            respaldo.setEstado(EstadoRespaldo.COMPLETADO);
        } catch (Exception ex) {
            respaldo.setEstado(EstadoRespaldo.FALLIDO);
            respaldo.setMensajeError(ex.getMessage());
        }
        return respaldoRepository.save(respaldo);
    }

    public Respaldo crearIncremental(String generadoPor) {
        Optional<Respaldo> ultimo = respaldoRepository.findFirstByEstadoOrderByFechaHoraDesc(EstadoRespaldo.COMPLETADO);
        if (ultimo.isEmpty()) {
            // No hay nada de que partir todavia: el primer respaldo siempre es completo.
            return crearCompleto(generadoPor);
        }

        Respaldo respaldo = new Respaldo(TipoRespaldo.INCREMENTAL, generadoPor, ultimo.get().getId());
        respaldo = respaldoRepository.save(respaldo);

        try {
            Path carpeta = Path.of(directorio);
            Files.createDirectories(carpeta);
            String nombreArchivo = "incremental-" + LocalDateTime.now().format(FORMATO_ARCHIVO) + ".json";
            Path archivo = carpeta.resolve(nombreArchivo);

            List<Map<String, Object>> cambios = jdbc.query(
                    """
                    SELECT id, fecha_hora, usuario_app, operacion, tabla, registro_id,
                           datos_anteriores::text AS datos_anteriores, datos_nuevos::text AS datos_nuevos
                    FROM auditoria WHERE fecha_hora > ? ORDER BY fecha_hora ASC
                    """,
                    this::mapearFila, Timestamp.valueOf(ultimo.get().getFechaHora()));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(archivo.toFile(), cambios);

            respaldo.setArchivoRuta(archivo.toAbsolutePath().toString());
            respaldo.setTamanoBytes(Files.size(archivo));
            respaldo.setRegistrosCapturados(cambios.size());
            respaldo.setEstado(EstadoRespaldo.COMPLETADO);
        } catch (IOException | RuntimeException ex) {
            respaldo.setEstado(EstadoRespaldo.FALLIDO);
            respaldo.setMensajeError(ex.getMessage());
        }
        return respaldoRepository.save(respaldo);
    }

    private Map<String, Object> mapearFila(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("id", rs.getLong("id"));
        fila.put("fechaHora", rs.getTimestamp("fecha_hora").toLocalDateTime().toString());
        fila.put("usuarioApp", rs.getString("usuario_app"));
        fila.put("operacion", rs.getString("operacion"));
        fila.put("tabla", rs.getString("tabla"));
        fila.put("registroId", rs.getString("registro_id"));
        fila.put("datosAnteriores", rs.getString("datos_anteriores"));
        fila.put("datosNuevos", rs.getString("datos_nuevos"));
        return fila;
    }

    /**
     * Corre cada minuto (mismo patron que ViajeSimulacionService): si la
     * configuracion esta activa, hoy corresponde segun la frecuencia elegida
     * (diario/semanal/mensual) y ya pasó la hora programada sin que se haya
     * disparado todavía HOY, dispara UN incremental (nunca un completo - si
     * es el primer respaldo de todos, crearIncremental() lo promueve solo a
     * completo).
     *
     * IMPORTANTE: configuracionRepository.save(...) al final NO es opcional.
     * Este método no es @Transactional (y aunque lo fuera, se invoca desde el
     * propio scheduler de Spring, no via una llamada externa a este bean, así
     * que el "dirty checking" automático de JPA no aplica) - sin el save()
     * explícito, marcar "ya se ejecutó hoy" solo cambiaba el objeto en
     * memoria y nunca llegaba a la base, así que el respaldo se volvía a
     * disparar en cada tick del scheduler (cada 60s) en vez de una vez al día.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    public void ejecutarProgramado() {
        ConfiguracionRespaldo configuracion = obtenerOCrearConfiguracion();
        if (!configuracion.isActivo()) {
            return;
        }
        LocalDate hoy = LocalDate.now();
        if (hoy.equals(configuracion.getUltimaEjecucionProgramada())) {
            return;
        }
        if (!correspondeHoy(configuracion, hoy)) {
            return;
        }
        if (java.time.LocalTime.now().isBefore(configuracion.getHoraProgramada())) {
            return;
        }
        configuracion.setUltimaEjecucionProgramada(hoy);
        configuracionRepository.save(configuracion);
        crearIncremental("Sistema");
    }

    /** ¿Corresponde correr el respaldo programado hoy, según la frecuencia configurada? */
    private boolean correspondeHoy(ConfiguracionRespaldo configuracion, LocalDate hoy) {
        return switch (configuracion.getFrecuencia()) {
            case DIARIO -> true;
            case SEMANAL -> hoy.getDayOfWeek() == configuracion.getDiaSemana();
            // Si el día configurado no existe en este mes (ej. 31 en un mes de
            // 30 días, o 30 en febrero), corre el último día del mes - así
            // "día 31" sigue significando "fin de mes" en vez de saltarse ese
            // mes por completo.
            case MENSUAL -> {
                int diaMes = configuracion.getDiaMes() == null ? 1 : configuracion.getDiaMes();
                int ultimoDiaDelMes = hoy.lengthOfMonth();
                yield hoy.getDayOfMonth() == Math.min(diaMes, ultimoDiaDelMes);
            }
        };
    }

    /** Para el endpoint de descarga: ruta del archivo + nombre sugerido, o 404/409 si no esta listo. */
    public Path resolverArchivoParaDescarga(Long id) {
        Respaldo respaldo = obtener(id);
        if (respaldo.getEstado() != EstadoRespaldo.COMPLETADO || respaldo.getArchivoRuta() == null) {
            throw new ConflictException("Este respaldo no tiene un archivo disponible para descargar.");
        }
        Path archivo = Path.of(respaldo.getArchivoRuta());
        if (!Files.exists(archivo)) {
            throw new ResourceNotFoundException("El archivo de este respaldo ya no existe en disco.");
        }
        return archivo;
    }
}
