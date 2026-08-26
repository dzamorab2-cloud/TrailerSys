package com.trailersys.backend.mantenimiento;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mantenimientos/reportes")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','MANTENIMIENTO')")
public class MantenimientoReporteController {
    private final JdbcTemplate jdbc;
    public MantenimientoReporteController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public MantenimientoReporteResponse obtener() {
        Map<String,Object> r = jdbc.queryForMap("""
            select count(*) total, coalesce(sum(costo),0) costo_total, coalesce(avg(costo),0) costo_promedio,
              count(*) filter (where tipo='PREVENTIVO') preventivos,
              count(*) filter (where tipo='CORRECTIVO') correctivos,
              count(*) filter (where proximo_servicio < current_date) vencidos
            from mantenimientos""");
        Map<String,Object> v = jdbc.queryForMap("""
            select count(*) filter (where estado='DISPONIBLE') disponibles,
              count(*) filter (where estado='MANTENIMIENTO') mantenimiento,
              count(*) filter (where estado='FUERA_DE_SERVICIO') fuera
            from vehiculos""");
        List<MantenimientoReporteResponse.VehiculoCosto> costos = jdbc.query("""
            select v.placa, count(*) cantidad, coalesce(sum(m.costo),0) costo,
              coalesce(sum(case when v.estado in ('MANTENIMIENTO','FUERA_DE_SERVICIO')
                then greatest(current_date-m.fecha,0) else 0 end),0) dias
            from mantenimientos m join vehiculos v on v.id=m.vehiculo_id
            group by v.id,v.placa order by costo desc limit 10""",
            (rs,n) -> new MantenimientoReporteResponse.VehiculoCosto(rs.getString("placa"), rs.getLong("cantidad"), rs.getDouble("costo"), rs.getLong("dias")));
        List<MantenimientoReporteResponse.TipoFrecuencia> frecuentes = jdbc.query("""
            select tipo, count(*) cantidad from mantenimientos group by tipo order by cantidad desc""",
            (rs,n) -> new MantenimientoReporteResponse.TipoFrecuencia(rs.getString("tipo"), rs.getLong("cantidad")));
        return new MantenimientoReporteResponse(num(r,"total"), dbl(r,"costo_total"), dbl(r,"costo_promedio"),
            num(r,"preventivos"), num(r,"correctivos"), num(r,"vencidos"), num(v,"disponibles"),
            num(v,"mantenimiento"), num(v,"fuera"), costos, frecuentes);
    }
    private long num(Map<String,Object> m,String k){ return ((Number)m.get(k)).longValue(); }
    private double dbl(Map<String,Object> m,String k){ return ((Number)m.get(k)).doubleValue(); }
}
