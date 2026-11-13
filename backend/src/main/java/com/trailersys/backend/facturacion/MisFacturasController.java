package com.trailersys.backend.facturacion;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.facturacion.dto.FacturaResponse;

/** Autoservicio del Cliente: consulta de sus propias facturas y saldos. */
@RestController
@RequestMapping("/api/mis-facturas")
@PreAuthorize("hasRole('CLIENTE')")
public class MisFacturasController {

    private final FacturacionService service;

    public MisFacturasController(FacturacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<FacturaResponse> listar(Principal principal) {
        return service.misFacturas(principal.getName());
    }
}
