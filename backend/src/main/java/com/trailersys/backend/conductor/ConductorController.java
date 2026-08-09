package com.trailersys.backend.conductor;

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

import com.trailersys.backend.conductor.dto.ConductorRequest;
import com.trailersys.backend.conductor.dto.ConductorResponse;

import jakarta.validation.Valid;

/**
 * Segun TRAILERSYS_ROLES (js/roles.js), solo Administrador y Coordinador
 * tienen "conductores" en su lista de modulos, y ambos lo gestionan por
 * completo: no hay un caso de solo-lectura para este modulo.
 */
@RestController
@RequestMapping("/api/conductores")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
public class ConductorController {

    private final ConductorService service;

    public ConductorController(ConductorService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConductorResponse> listar(
            @RequestParam(required = false) EstadoConductor estado,
            @RequestParam(required = false) String search) {
        return service.listar(estado, search).stream().map(ConductorResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ConductorResponse obtener(@PathVariable Long id) {
        return ConductorResponse.from(service.obtener(id));
    }

    @PostMapping
    public ResponseEntity<ConductorResponse> crear(@Valid @RequestBody ConductorRequest request) {
        Conductor creado = service.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConductorResponse.from(creado));
    }

    @PutMapping("/{id}")
    public ConductorResponse actualizar(@PathVariable Long id, @Valid @RequestBody ConductorRequest request) {
        return ConductorResponse.from(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
