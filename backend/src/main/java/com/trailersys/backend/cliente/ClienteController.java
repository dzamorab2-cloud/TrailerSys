package com.trailersys.backend.cliente;

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

import com.trailersys.backend.cliente.dto.ClienteRequest;
import com.trailersys.backend.cliente.dto.ClienteResponse;

import jakarta.validation.Valid;

/**
 * Segun TRAILERSYS_ROLES (js/roles.js), solo Administrador tiene
 * "clientes" en su lista de modulos (Coordinador no lo incluye entre
 * sus funciones), asi que un unico rol controla todo el acceso.
 */
@RestController
@RequestMapping("/api/clientes")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClienteResponse> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String search) {
        EstadoCliente estadoFiltro = estado == null || estado.isBlank() ? null : EstadoCliente.desdeEtiqueta(estado);
        return service.listar(estadoFiltro, search).stream().map(ClienteResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@PathVariable Long id) {
        return ClienteResponse.from(service.obtener(id));
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteRequest request) {
        Cliente creado = service.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponse.from(creado));
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return ClienteResponse.from(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
