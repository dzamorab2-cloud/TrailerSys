package com.trailersys.backend.operaciones;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.conductor.dto.ConductorResponse;
import com.trailersys.backend.operaciones.dto.ActualizarFotoConductorRequest;
import com.trailersys.backend.operaciones.dto.PerfilConductorResponse;
import com.trailersys.backend.operaciones.dto.ResumenConductorResponse;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.dto.ViajeResponse;

/**
 * Superficie de API exclusiva del rol CONDUCTOR ("Mis viajes" + Dashboard
 * personal). Deliberadamente separada de ViajeController (uso interno de
 * Administrador/Coordinador/Supervisor): aqui cada operacion se acota
 * siempre al Conductor vinculado al usuario autenticado, nunca a un id que
 * llegue en la URL. Calcada de PedidoClienteController.
 */
@RestController
@RequestMapping("/api/mis-viajes")
@PreAuthorize("hasRole('CONDUCTOR')")
public class ViajeConductorController {

    private final ViajeConductorService service;

    public ViajeConductorController(ViajeConductorService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ViajeResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String estado,
            Principal principal) {
        EstadoViaje estadoFiltro = estado == null || estado.isBlank() ? null : EstadoViaje.desdeEtiqueta(estado);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "fechaSalida"));
        return service.listarMisViajes(principal.getName(), search, estadoFiltro, pageable).map(ViajeResponse::from);
    }

    @GetMapping("/{id}")
    public ViajeResponse obtener(@PathVariable Long id, Principal principal) {
        return ViajeResponse.from(service.detalle(principal.getName(), id));
    }

    @GetMapping("/activo")
    public ResponseEntity<ViajeResponse> activo(Principal principal) {
        Viaje viaje = service.viajeActivo(principal.getName());
        return viaje == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(ViajeResponse.from(viaje));
    }

    @GetMapping("/resumen")
    public ResumenConductorResponse resumen(Principal principal) {
        return service.resumen(principal.getName());
    }

    @GetMapping("/perfil")
    public PerfilConductorResponse perfil(Principal principal) {
        return PerfilConductorResponse.from(service.perfil(principal.getName()));
    }

    @PutMapping("/perfil/foto")
    public ConductorResponse actualizarFoto(@RequestBody ActualizarFotoConductorRequest request, Principal principal) {
        return ConductorResponse.from(service.actualizarFoto(principal.getName(), request.foto()));
    }
}
