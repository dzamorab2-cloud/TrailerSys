package com.trailersys.backend.facturacion;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.facturacion.dto.AnularFacturaRequest;
import com.trailersys.backend.facturacion.dto.CarteraResumenResponse;
import com.trailersys.backend.facturacion.dto.CrearFacturaRequest;
import com.trailersys.backend.facturacion.dto.FacturaResponse;
import com.trailersys.backend.facturacion.dto.RegistrarPagoRequest;
import com.trailersys.backend.facturacion.dto.ViajeFacturableResponse;

import jakarta.validation.Valid;

/**
 * Facturacion y cobros. Consultar: Administrador, Coordinador y Supervisor.
 * Emitir facturas y registrar pagos: Administrador y Coordinador. Anular:
 * solo Administrador.
 */
@RestController
@RequestMapping("/api/facturas")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR','SUPERVISOR')")
public class FacturaController {

    private final FacturacionService service;

    public FacturaController(FacturacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<FacturaResponse> listar(@RequestParam(required = false) Long clienteId,
                                        @RequestParam(required = false) String estado,
                                        @RequestParam(required = false) String search) {
        return service.listar(clienteId, estado, search);
    }

    @GetMapping("/resumen")
    public CarteraResumenResponse resumen() {
        return service.resumen();
    }

    @GetMapping("/configuracion")
    public Map<String, Object> configuracion() {
        BigDecimal iva = service.getIvaPorDefecto();
        return Map.of("ivaPorcentaje", iva, "diasCredito", service.getDiasCreditoPorDefecto());
    }

    @GetMapping("/viajes-facturables")
    public List<ViajeFacturableResponse> viajesFacturables(@RequestParam Long clienteId) {
        return service.viajesFacturables(clienteId);
    }

    @GetMapping("/{id}")
    public FacturaResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
    public FacturaResponse crear(@Valid @RequestBody CrearFacturaRequest request, Principal principal) {
        return service.crear(request, principal.getName());
    }

    @PostMapping("/{id}/pagos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
    public FacturaResponse registrarPago(@PathVariable Long id, @Valid @RequestBody RegistrarPagoRequest request,
                                         Principal principal) {
        return service.registrarPago(id, request, principal.getName());
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public FacturaResponse anular(@PathVariable Long id, @Valid @RequestBody AnularFacturaRequest request) {
        return service.anular(id, request);
    }
}
