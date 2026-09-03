package com.trailersys.backend.respaldo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.respaldo.dto.ConfiguracionRespaldoRequest;
import com.trailersys.backend.respaldo.dto.ConfiguracionRespaldoResponse;
import com.trailersys.backend.respaldo.dto.RespaldoResponse;

import jakarta.validation.Valid;

/** Respaldos de la base de datos - exclusivo del rol Administrador. */
@RestController
@RequestMapping("/api/respaldos")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class RespaldoController {

    private final RespaldoService service;

    public RespaldoController(RespaldoService service) {
        this.service = service;
    }

    @GetMapping
    public List<RespaldoResponse> listar() {
        return service.listar().stream().map(RespaldoResponse::from).toList();
    }

    @GetMapping("/configuracion")
    public ConfiguracionRespaldoResponse obtenerConfiguracion() {
        return ConfiguracionRespaldoResponse.from(service.obtenerOCrearConfiguracion());
    }

    @PutMapping("/configuracion")
    public ConfiguracionRespaldoResponse actualizarConfiguracion(@Valid @RequestBody ConfiguracionRespaldoRequest request) {
        return ConfiguracionRespaldoResponse.from(service.actualizarConfiguracion(request));
    }

    @PostMapping("/completo")
    public ResponseEntity<RespaldoResponse> crearCompleto(Authentication auth) {
        Respaldo respaldo = service.crearCompleto(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(RespaldoResponse.from(respaldo));
    }

    @PostMapping("/incremental")
    public ResponseEntity<RespaldoResponse> crearIncremental(Authentication auth) {
        Respaldo respaldo = service.crearIncremental(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(RespaldoResponse.from(respaldo));
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) throws IOException {
        Path archivo = service.resolverArchivoParaDescarga(id);
        byte[] contenido = Files.readAllBytes(archivo);
        String nombre = archivo.getFileName().toString();
        MediaType tipo = nombre.endsWith(".json") ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .body(contenido);
    }
}
