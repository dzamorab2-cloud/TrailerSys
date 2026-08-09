package com.trailersys.backend.vehiculo;

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

import com.trailersys.backend.vehiculo.dto.VehiculoRequest;
import com.trailersys.backend.vehiculo.dto.VehiculoResponse;

import jakarta.validation.Valid;

/**
 * Los roles autorizados reflejan TRAILERSYS_ROLES en js/roles.js: los
 * modulos que incluyen "vehiculos" pueden consultar, y solo los que lo
 * incluyen en "manage" (administrador y coordinador) pueden escribir.
 */
@RestController
@RequestMapping("/api/vehiculos")
public class VehiculoController {

    private static final String PUEDE_CONSULTAR = "hasAnyRole('ADMINISTRADOR','COORDINADOR','MANTENIMIENTO','SUPERVISOR')";
    private static final String PUEDE_GESTIONAR = "hasAnyRole('ADMINISTRADOR','COORDINADOR')";

    private final VehiculoService service;

    public VehiculoController(VehiculoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(PUEDE_CONSULTAR)
    public List<VehiculoResponse> listar(
            @RequestParam(required = false) EstadoVehiculo estado,
            @RequestParam(required = false) String search) {
        return service.listar(estado, search).stream().map(VehiculoResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(PUEDE_CONSULTAR)
    public VehiculoResponse obtener(@PathVariable Long id) {
        return VehiculoResponse.from(service.obtener(id));
    }

    @PostMapping
    @PreAuthorize(PUEDE_GESTIONAR)
    public ResponseEntity<VehiculoResponse> crear(@Valid @RequestBody VehiculoRequest request) {
        Vehiculo creado = service.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(VehiculoResponse.from(creado));
    }

    @PutMapping("/{id}")
    @PreAuthorize(PUEDE_GESTIONAR)
    public VehiculoResponse actualizar(@PathVariable Long id, @Valid @RequestBody VehiculoRequest request) {
        return VehiculoResponse.from(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PUEDE_GESTIONAR)
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
