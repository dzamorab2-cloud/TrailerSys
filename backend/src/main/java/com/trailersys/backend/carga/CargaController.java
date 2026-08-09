package com.trailersys.backend.carga;

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

import com.trailersys.backend.carga.dto.CargaRequest;
import com.trailersys.backend.carga.dto.CargaResponse;

import jakarta.validation.Valid;

/**
 * Segun TRAILERSYS_ROLES (js/roles.js), Administrador y Coordinador
 * tienen "cargas" tanto en modulos como en manage: no hay caso de solo
 * lectura para este modulo.
 */
@RestController
@RequestMapping("/api/cargas")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
public class CargaController {

    private final CargaService service;

    public CargaController(CargaService service) {
        this.service = service;
    }

    @GetMapping
    public List<CargaResponse> listar(
            @RequestParam(required = false) EstadoCarga estado,
            @RequestParam(required = false) String search) {
        return service.listar(estado, search).stream().map(CargaResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CargaResponse obtener(@PathVariable Long id) {
        return CargaResponse.from(service.obtener(id));
    }

    @PostMapping
    public ResponseEntity<CargaResponse> crear(@Valid @RequestBody CargaRequest request) {
        Carga creada = service.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CargaResponse.from(creada));
    }

    @PutMapping("/{id}")
    public CargaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CargaRequest request) {
        return CargaResponse.from(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
