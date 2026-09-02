package com.trailersys.backend.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.mantenimiento.MantenimientoRepository;
import com.trailersys.backend.mantenimiento.TipoMantenimiento;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.ViajeRepository;

/**
 * El modulo Reportes (js/reportes.js) mostraba, junto a la tarjeta "Total X"
 * (que si viene de pagina.totalElements, el conteo real), un desglose por
 * estado calculado contando solo los 100 registros que trae la pagina que se
 * ve en pantalla - con catalogos de decenas o cientos de miles de filas, ese
 * desglose no tenia relacion real con el total (ej. "Disponibles: 88" en vez
 * de los ~44.900 reales). Estos endpoints devuelven el conteo real por
 * estado directamente de la base, independiente de la paginacion.
 *
 * El desglose siempre es del catalogo completo (no respeta el filtro de
 * estado de la tabla, que ya de por si deja el resto de las categorias en
 * cero): es un resumen aparte, igual que ya hace /api/dashboard/disponibilidad
 * para Vehiculos/Conductores. Viajes es la excepcion: respeta el rango de
 * fecha (no el estado) porque ese filtro es justamente lo que evita traer
 * medio millon de filas para un solo numero.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteResumenController {

    private final JdbcTemplate jdbc;
    private final ViajeRepository viajeRepository;
    private final MantenimientoRepository mantenimientoRepository;

    public ReporteResumenController(JdbcTemplate jdbc, ViajeRepository viajeRepository,
            MantenimientoRepository mantenimientoRepository) {
        this.jdbc = jdbc;
        this.viajeRepository = viajeRepository;
        this.mantenimientoRepository = mantenimientoRepository;
    }

    private long contar(String sql, Object... params) {
        Long valor = jdbc.queryForObject(sql, Long.class, params);
        return valor == null ? 0 : valor;
    }

    @GetMapping("/cargas")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
    public CargaResumenResponse cargas() {
        return new CargaResumenResponse(
                contar("SELECT count(*) FROM cargas WHERE estado='PENDIENTE'"),
                contar("SELECT count(*) FROM cargas WHERE estado='ASIGNADA'"),
                contar("SELECT count(*) FROM cargas WHERE estado='EN_TRANSITO'"),
                contar("SELECT count(*) FROM cargas WHERE estado='ENTREGADA'"),
                contar("SELECT count(*) FROM cargas WHERE estado='CANCELADA'"));
    }

    @GetMapping("/clientes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
    public ClienteResumenResponse clientes() {
        return new ClienteResumenResponse(
                contar("SELECT count(*) FROM clientes WHERE estado='ACTIVO'"),
                contar("SELECT count(*) FROM clientes WHERE estado='INACTIVO'"),
                contar("SELECT count(*) FROM clientes WHERE correo IS NOT NULL AND correo <> ''"));
    }

    @GetMapping("/viajes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR','CONDUCTOR','SUPERVISOR')")
    public ViajeResumenResponse viajes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        LocalDateTime desdeFiltro = desde == null ? null : desde.atStartOfDay();
        LocalDateTime hastaFiltro = hasta == null ? null : hasta.atTime(LocalTime.MAX);
        Object[] fila = viajeRepository.resumenPorFecha(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO,
                EstadoViaje.FINALIZADO, EstadoViaje.CANCELADO, desdeFiltro, hastaFiltro).get(0);
        return new ViajeResumenResponse(num(fila[0]), num(fila[1]), num(fila[2]), num(fila[3]), dbl(fila[4]));
    }

    @GetMapping("/mantenimientos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','MANTENIMIENTO')")
    public MantenimientoResumenResponse mantenimientos(
            @RequestParam(required = false) Long vehiculoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Object[] fila = mantenimientoRepository.resumen(TipoMantenimiento.PREVENTIVO, TipoMantenimiento.CORRECTIVO,
                vehiculoId, desde, hasta).get(0);
        return new MantenimientoResumenResponse(num(fila[0]), num(fila[1]), num(fila[2]), dbl(fila[3]));
    }

    private long num(Object valor) {
        return valor == null ? 0 : ((Number) valor).longValue();
    }

    private double dbl(Object valor) {
        return valor == null ? 0 : ((Number) valor).doubleValue();
    }
}
