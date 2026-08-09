package com.trailersys.backend.mantenimiento;

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

import com.trailersys.backend.mantenimiento.dto.MantenimientoRequest;
import com.trailersys.backend.mantenimiento.dto.MantenimientoResponse;

import jakarta.validation.Valid;

/**
 * Segun TRAILERSYS_ROLES (js/roles.js), Administrador y Responsable de
 * Mantenimiento tienen "mantenimientos" tanto en modulos como en manage
 * (Coordinador no lo incluye), asi que un unico @PreAuthorize cubre todo.
 */
@RestController
@RequestMapping("/api/mantenimientos")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','MANTENIMIENTO')")
public class MantenimientoController {

    private final MantenimientoService service;

    public MantenimientoController(MantenimientoService service) {
        this.service = service;
    }

    @GetMapping
    public List<MantenimientoResponse> listar(
            @RequestParam(required = false) Long vehiculoId,
            @RequestParam(required = false) TipoMantenimiento tipo,
            @RequestParam(required = false) String search) {
        return service.listar(vehiculoId, tipo, search).stream().map(MantenimientoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public MantenimientoResponse obtener(@PathVariable Long id) {
        return MantenimientoResponse.from(service.obtener(id));
    }

    @PostMapping
    public ResponseEntity<MantenimientoResponse> crear(@Valid @RequestBody MantenimientoRequest request) {
        Mantenimiento creado = service.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(MantenimientoResponse.from(creado));
    }

    @PutMapping("/{id}")
    public MantenimientoResponse actualizar(@PathVariable Long id, @Valid @RequestBody MantenimientoRequest request) {
        return MantenimientoResponse.from(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
