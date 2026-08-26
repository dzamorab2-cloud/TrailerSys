package com.trailersys.backend.viaje;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.viaje.dto.HistorialViajeResponse;

@RestController
@RequestMapping("/api/viajes")
public class HistorialViajeController {
    private final JdbcTemplate jdbc;
    private final ViajeService service;
    public HistorialViajeController(JdbcTemplate jdbc, ViajeService service) { this.jdbc = jdbc; this.service = service; }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR','CONDUCTOR','SUPERVISOR')")
    public List<HistorialViajeResponse> historial(@PathVariable Long id) {
        Viaje viaje = service.obtener(id);
        List<HistorialViajeResponse> items = new ArrayList<>();
        jdbc.queryForList("SELECT fecha_hora FROM auditoria WHERE tabla='viajes' AND registro_id=? AND operacion='INSERT' ORDER BY fecha_hora LIMIT 1", id.toString())
                .forEach(row -> items.add(new HistorialViajeResponse(((java.sql.Timestamp) row.get("fecha_hora")).toInstant().atOffset(ZoneOffset.UTC), "CREACION", "Viaje creado y asignado",
                        viaje.getVehiculo().getPlaca() + " · " + viaje.getConductor().getNombres())));
        if (viaje.getFechaSalida() != null) items.add(new HistorialViajeResponse(viaje.getFechaSalida().atOffset(ZoneOffset.ofHours(-5)), "SALIDA", "Salida programada", viaje.getOrigen() + " → " + viaje.getDestino()));
        for (Map<String, Object> row : jdbc.queryForList("SELECT fecha_hora, evento, ubicacion, observacion FROM seguimiento_eventos WHERE viaje_id=? ORDER BY fecha_hora", id)) {
            var fecha = ((java.sql.Timestamp) row.get("fecha_hora")).toLocalDateTime().atOffset(ZoneOffset.ofHours(-5));
            String observacion = (String) row.get("observacion");
            items.add(new HistorialViajeResponse(fecha, "EVENTO", String.valueOf(row.get("evento")),
                    row.get("ubicacion") + (observacion == null ? "" : " · " + observacion)));
        }
        if (viaje.getFechaEntregaConfirmada() != null) items.add(new HistorialViajeResponse(viaje.getFechaEntregaConfirmada().atOffset(ZoneOffset.ofHours(-5)), "ENTREGA", "Entrega confirmada", viaje.getObservacionEntrega()));
        if (viaje.getFechaValidacionEntrega() != null) items.add(new HistorialViajeResponse(viaje.getFechaValidacionEntrega().atOffset(ZoneOffset.ofHours(-5)), "VALIDACION", "Entrega validada", viaje.getObservacionValidacion()));
        items.sort(Comparator.comparing(HistorialViajeResponse::fecha));
        return items;
    }
}
