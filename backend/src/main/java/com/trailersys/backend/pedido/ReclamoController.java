package com.trailersys.backend.pedido;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.pedido.dto.ResolverReclamoRequest;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.ViajeRepository;
import com.trailersys.backend.viaje.dto.ViajeResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reclamos")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
public class ReclamoController {
    private final ViajeRepository repository;
    public ReclamoController(ViajeRepository repository) { this.repository = repository; }

    @GetMapping
    public List<ViajeResponse> listar() {
        return repository.findByEstadoReclamoClienteIsNotNullOrderByFechaConfirmacionClienteDesc().stream()
                .map(ViajeResponse::from).toList();
    }

    @PutMapping("/{id}")
    public ViajeResponse responder(@PathVariable Long id, @Valid @RequestBody ResolverReclamoRequest request) {
        Viaje viaje = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado: " + id));
        if (viaje.getEstadoReclamoCliente() == null) throw new IllegalArgumentException("El viaje no tiene un reclamo.");
        viaje.setRespuestaReclamoCliente(request.respuesta().trim());
        viaje.setEstadoReclamoCliente("RESUELTO".equalsIgnoreCase(request.estado()) ? "RESUELTO" : "EN_REVISION");
        if ("RESUELTO".equals(viaje.getEstadoReclamoCliente())) viaje.setFechaResolucionReclamoCliente(LocalDateTime.now());
        return ViajeResponse.from(repository.save(viaje));
    }
}
