package com.trailersys.backend.pedido;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.carga.Carga;
import com.trailersys.backend.carga.dto.CargaResponse;
import com.trailersys.backend.pedido.dto.ConfirmarRecepcionClienteRequest;
import com.trailersys.backend.pedido.dto.PedidoCargaRequest;
import com.trailersys.backend.pedido.dto.DetallePedidoResponse;
import com.trailersys.backend.pedido.dto.PerfilClienteResponse;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.dto.ViajeResponse;

import jakarta.validation.Valid;

/**
 * Superficie de API exclusiva del rol CLIENTE ("hacer un pedido" = crear
 * una Carga en Pendiente). Deliberadamente separada de CargaController y
 * ViajeController (uso interno de Administrador/Coordinador/etc.): aqui
 * cada operacion se acota siempre al Cliente vinculado al usuario
 * autenticado, nunca a un id que llegue en la URL o el body.
 */
@RestController
@RequestMapping("/api/mis-cargas")
@PreAuthorize("hasRole('CLIENTE')")
public class PedidoClienteController {

    private final PedidoClienteService service;

    public PedidoClienteController(PedidoClienteService service) {
        this.service = service;
    }

    @GetMapping
    public List<CargaResponse> listar(Principal principal) {
        return service.listarMisCargas(principal.getName()).stream().map(CargaResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<CargaResponse> crear(@Valid @RequestBody PedidoCargaRequest request, Principal principal) {
        Carga creada = service.crearPedido(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(CargaResponse.from(creada));
    }

    @GetMapping("/{id}/viaje")
    public ResponseEntity<ViajeResponse> obtenerViaje(@PathVariable Long id, Principal principal) {
        Viaje viaje = service.obtenerViajeDeMiCarga(principal.getName(), id);
        return viaje == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(ViajeResponse.from(viaje));
    }

    @PostMapping("/{id}/confirmar-recepcion")
    public ViajeResponse confirmarRecepcion(@PathVariable Long id,
            @RequestBody(required = false) ConfirmarRecepcionClienteRequest request, Principal principal) {
        String observacion = request != null ? request.observacion() : null;
        String novedad = request != null ? request.novedad() : null;
        String evidencia = request != null ? request.evidencia() : null;
        return ViajeResponse.from(service.confirmarRecepcion(principal.getName(), id, observacion, novedad, evidencia));
    }

    @GetMapping("/perfil")
    public PerfilClienteResponse perfil(Principal principal) {
        return service.perfil(principal.getName());
    }

    @GetMapping("/{id}/detalle")
    public DetallePedidoResponse detalle(@PathVariable Long id, Principal principal) {
        return service.detalle(principal.getName(), id);
    }
}
