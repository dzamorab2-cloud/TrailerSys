package com.trailersys.backend.facturacion;

import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Las tablas de facturacion las crea Hibernate (ddl-auto), despues de que
 * corrio 00_esquema_trailersys.sql, asi que ese script no les pudo poner el
 * disparador de auditoria. Este paso lo agrega al arrancar (solo en
 * PostgreSQL y solo si la funcion de auditoria existe), para que facturas,
 * lineas y pagos queden en la Auditoria igual que el resto de las tablas.
 * Tambien crea el indice de viajes por cliente que usa "viajes por
 * facturar". Equivalente manual: database/14_facturacion_auditoria.sql.
 */
@Component
public class FacturacionAuditoriaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FacturacionAuditoriaInitializer.class);
    private static final List<String> TABLAS = List.of("facturas", "factura_detalles", "pagos");

    private final JdbcTemplate jdbcTemplate;

    public FacturacionAuditoriaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * "Viajes por facturar" filtra por cliente y estado y ordena por fecha;
     * sin este indice, con cientos de miles de viajes PostgreSQL recorre
     * todos los finalizados para encontrar los de un cliente.
     */
    private void crearIndiceViajesPorCliente() {
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_viajes_cliente_estado_fecha "
                    + "ON public.viajes (cliente_id, estado, fecha_salida DESC)");
        } catch (DataAccessException error) {
            log.warn("No se pudo crear el indice de viajes por cliente: {}", error.getMostSpecificCause().getMessage());
        }
    }

    @Override
    public void run(String... args) {
        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) return;
        } catch (SQLException error) {
            log.warn("No se pudo verificar el motor de base de datos: {}", error.getMessage());
            return;
        }
        crearIndiceViajesPorCliente();
        try {
            Integer funciones = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_proc WHERE proname = 'trailersys_registrar_auditoria'", Integer.class);
            if (funciones == null || funciones == 0) {
                log.info("Funcion de auditoria no encontrada: las tablas de facturacion no se auditaran.");
                return;
            }
            for (String tabla : TABLAS) {
                jdbcTemplate.execute("CREATE OR REPLACE TRIGGER trg_auditar AFTER INSERT OR DELETE OR UPDATE ON public."
                        + tabla + " FOR EACH ROW EXECUTE FUNCTION public.trailersys_registrar_auditoria()");
            }
        } catch (DataAccessException error) {
            log.warn("No se pudo activar la auditoria de facturacion: {}", error.getMostSpecificCause().getMessage());
        }
    }
}
