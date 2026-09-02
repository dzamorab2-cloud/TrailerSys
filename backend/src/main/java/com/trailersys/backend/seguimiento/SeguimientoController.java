package com.trailersys.backend.seguimiento;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.seguimiento.dto.AlertaDto;
import com.trailersys.backend.seguimiento.dto.SeguimientoEventoRequest;
import com.trailersys.backend.seguimiento.dto.SeguimientoEventoResponse;

import jakarta.validation.Valid;

/**
 * Segun TRAILERSYS_ROLES (js/roles.js), "seguimiento" ya no esta entre los
 * modulos de Conductor - tiene su propio "mis-viajes" en su lugar,
 * justamente porque el generico "exponia el resto de la flota y una
 * bitacora manual que no le corresponden" (comentario textual en
 * roles.js). Aun asi, Conductor sigue en PUEDE_GESTIONAR/PUEDE_CONSULTAR
 * porque puede registrar/consultar eventos de sus PROPIOS viajes via API
 * directa (SeguimientoService acota eso por Conductor, no por el modulo) -
 * pero nunca fleet-wide: alertas() usa PUEDE_VER_ALERTAS, sin Conductor,
 * porque esas si son siempre de toda la flota y no hay forma de acotarlas
 * por conductor. Supervisor solo consulta eventos/alertas (necesita leer
 * para poder validar una entrega desde Viajes), por eso las lecturas y las
 * escrituras tienen @PreAuthorize separados.
 */
@RestController
@RequestMapping("/api/seguimiento")
public class SeguimientoController {

    private static final String PUEDE_CONSULTAR = "hasAnyRole('ADMINISTRADOR','COORDINADOR','CONDUCTOR','SUPERVISOR')";
    private static final String PUEDE_GESTIONAR = "hasAnyRole('ADMINISTRADOR','COORDINADOR','CONDUCTOR')";
    private static final String PUEDE_VER_ALERTAS = "hasAnyRole('ADMINISTRADOR','COORDINADOR','SUPERVISOR')";

    private final SeguimientoService service;

    public SeguimientoController(SeguimientoService service) {
        this.service = service;
    }

    @GetMapping("/eventos")
    @PreAuthorize(PUEDE_CONSULTAR)
    public List<SeguimientoEventoResponse> listarEventos(@RequestParam(required = false) Long viajeId, Principal principal) {
        return service.listarEventos(viajeId, principal.getName()).stream().map(SeguimientoEventoResponse::from).toList();
    }

    @PostMapping("/eventos")
    @PreAuthorize(PUEDE_GESTIONAR)
    public ResponseEntity<SeguimientoEventoResponse> crearEvento(
            @Valid @RequestBody SeguimientoEventoRequest request, Principal principal) {
        SeguimientoEvento creado = service.crearEvento(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(SeguimientoEventoResponse.from(creado));
    }

    @DeleteMapping("/eventos/{id}")
    @PreAuthorize(PUEDE_GESTIONAR)
    public ResponseEntity<Void> eliminarEvento(@PathVariable Long id, Principal principal) {
        service.eliminarEvento(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/alertas")
    @PreAuthorize(PUEDE_VER_ALERTAS)
    public List<AlertaDto> alertas() {
        return service.obtenerAlertas();
    }
}
