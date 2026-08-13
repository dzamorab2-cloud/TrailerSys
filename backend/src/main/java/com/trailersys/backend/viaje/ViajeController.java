package com.trailersys.backend.viaje;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.viaje.dto.ConfirmarEntregaRequest;
import com.trailersys.backend.viaje.dto.ValidarEntregaRequest;
import com.trailersys.backend.viaje.dto.ViajeRequest;
import com.trailersys.backend.viaje.dto.ViajeResponse;

import jakarta.validation.Valid;

/**
 * Segun TRAILERSYS_ROLES (js/roles.js): Administrador, Coordinador,
 * Conductor y Supervisor tienen "viajes" en su lista de modulos, pero
 * solo Administrador y Coordinador lo tienen en "manage". Conductor y
 * Supervisor consultan en modo solo lectura (por ejemplo, el conductor ve
 * sus viajes pero no los crea ni edita).
 */
@RestController
@RequestMapping("/api/viajes")
public class ViajeController {

    private static final String PUEDE_CONSULTAR = "hasAnyRole('ADMINISTRADOR','COORDINADOR','CONDUCTOR','SUPERVISOR')";
    private static final String PUEDE_GESTIONAR = "hasAnyRole('ADMINISTRADOR','COORDINADOR')";

    private final ViajeService service;

    public ViajeController(ViajeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(PUEDE_CONSULTAR)
    public List<ViajeResponse> listar(
            @RequestParam(required = false) EstadoViaje estado,
            @RequestParam(required = false) String search) {
        return service.listar(estado, search).stream().map(ViajeResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(PUEDE_CONSULTAR)
    public ViajeResponse obtener(@PathVariable Long id) {
        return ViajeResponse.from(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(PUEDE_GESTIONAR)
    public ResponseEntity<ViajeResponse> crear(@Valid @RequestBody ViajeRequest request) {
        Viaje creado = service.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ViajeResponse.from(creado));
    }

    @PutMapping("/{id}")
    @PreAuthorize(PUEDE_GESTIONAR)
    public ViajeResponse actualizar(@PathVariable Long id, @Valid @RequestBody ViajeRequest request) {
        return ViajeResponse.from(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PUEDE_GESTIONAR)
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * El conductor confirma que la carga llego a destino. Cierra el viaje
     * (pasa a Finalizado) y queda registrado quien confirmo y cuando.
     */
    @PostMapping("/{id}/confirmar-entrega")
    @PreAuthorize("hasAnyRole('CONDUCTOR','ADMINISTRADOR')")
    public ViajeResponse confirmarEntrega(@PathVariable Long id, @RequestBody(required = false) ConfirmarEntregaRequest request,
                                           Principal principal) {
        String observacion = request != null ? request.observacion() : null;
        return ViajeResponse.from(service.confirmarEntrega(id, observacion, principal.getName()));
    }

    /**
     * El supervisor valida una entrega ya confirmada por el conductor. Es
     * un registro de auditoria aparte, no vuelve a cambiar el estado.
     */
    @PostMapping("/{id}/validar-entrega")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMINISTRADOR')")
    public ViajeResponse validarEntrega(@PathVariable Long id, @RequestBody(required = false) ValidarEntregaRequest request,
                                         Principal principal) {
        String observacion = request != null ? request.observacion() : null;
        return ViajeResponse.from(service.validarEntrega(id, observacion, principal.getName()));
    }
}
