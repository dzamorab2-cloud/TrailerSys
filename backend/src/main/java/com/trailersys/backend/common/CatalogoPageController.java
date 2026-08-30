package com.trailersys.backend.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.carga.CargaRepository;
import com.trailersys.backend.carga.EstadoCarga;
import com.trailersys.backend.carga.dto.CargaResponse;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.cliente.EstadoCliente;
import com.trailersys.backend.cliente.dto.ClienteResponse;
import com.trailersys.backend.conductor.ConductorRepository;
import com.trailersys.backend.conductor.EstadoConductor;
import com.trailersys.backend.conductor.dto.ConductorResponse;
import com.trailersys.backend.mantenimiento.MantenimientoRepository;
import com.trailersys.backend.mantenimiento.TipoMantenimiento;
import com.trailersys.backend.mantenimiento.dto.MantenimientoResponse;
import com.trailersys.backend.seguimiento.SeguimientoEventoRepository;
import com.trailersys.backend.seguimiento.dto.SeguimientoEventoResponse;
import com.trailersys.backend.vehiculo.VehiculoRepository;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.dto.VehiculoResponse;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.ViajeRepository;
import com.trailersys.backend.viaje.dto.ViajeResponse;

@RestController
@RequestMapping("/api/paginas")
public class CatalogoPageController {
    private final VehiculoRepository vehiculos; private final ConductorRepository conductores;
    private final ClienteRepository clientes; private final CargaRepository cargas;
    private final ViajeRepository viajes; private final MantenimientoRepository mantenimientos;
    private final SeguimientoEventoRepository eventos;

    public CatalogoPageController(VehiculoRepository vehiculos, ConductorRepository conductores,
            ClienteRepository clientes, CargaRepository cargas, ViajeRepository viajes,
            MantenimientoRepository mantenimientos, SeguimientoEventoRepository eventos) {
        this.vehiculos=vehiculos; this.conductores=conductores; this.clientes=clientes;
        this.cargas=cargas; this.viajes=viajes; this.mantenimientos=mantenimientos; this.eventos=eventos;
    }

    private PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(0,page), Math.min(100,Math.max(12,size)), Sort.by(Sort.Direction.DESC,"id"));
    }

    @GetMapping("/vehiculos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR','MANTENIMIENTO','SUPERVISOR')")
    public Page<VehiculoResponse> vehiculos(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="24") int size,
            @RequestParam(defaultValue="") String search,
            @RequestParam(required=false) String estado) {
        EstadoVehiculo estadoFiltro = estado == null || estado.isBlank() ? null : EstadoVehiculo.desdeEtiqueta(estado);
        return vehiculos.buscar(search.trim(), estadoFiltro, page(page,size)).map(VehiculoResponse::from);
    }
    @GetMapping("/conductores")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
    public Page<ConductorResponse> conductores(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="24") int size,
            @RequestParam(defaultValue="") String search,
            @RequestParam(required=false) String estado) {
        EstadoConductor estadoFiltro = estado == null || estado.isBlank() ? null : EstadoConductor.desdeEtiqueta(estado);
        return conductores.buscar(search.trim(), estadoFiltro, page(page,size)).map(ConductorResponse::from);
    }
    // Administrador gestiona el modulo Clientes completo; Coordinador no lo
    // tiene como modulo propio pero necesita buscar clientes aqui mismo para
    // asignarlos a una Carga (que si gestiona) sin poder editarlos.
    @GetMapping("/clientes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
    public Page<ClienteResponse> clientes(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="24") int size,
            @RequestParam(defaultValue="") String search,
            @RequestParam(required=false) String estado) {
        EstadoCliente estadoFiltro = estado == null || estado.isBlank() ? null : EstadoCliente.desdeEtiqueta(estado);
        return clientes.buscar(search.trim(), estadoFiltro, page(page,size)).map(ClienteResponse::from);
    }
    @GetMapping("/cargas")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR')")
    public Page<CargaResponse> cargas(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="24") int size,
            @RequestParam(defaultValue="") String search,
            @RequestParam(required=false) String estado) {
        EstadoCarga estadoFiltro = estado == null || estado.isBlank() ? null : EstadoCarga.desdeEtiqueta(estado);
        return cargas.buscar(search.trim(), estadoFiltro, page(page,size)).map(CargaResponse::from);
    }
    // desde/hasta filtran por fecha (solo el dia, sin hora) sobre la fecha
    // principal del recurso (fechaSalida en viajes, fecha en mantenimientos).
    // Pensado para los Reportes: antes se traian como mucho 100 registros de
    // catalogos con decenas o cientos de miles de filas y se mostraban como
    // si fueran el total; acotando por fecha (ej. "hoy", o un rango) el
    // conteo real que devuelve la pagina vuelve a ser representativo.
    @GetMapping("/viajes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR','CONDUCTOR','SUPERVISOR')")
    public Page<ViajeResponse> viajes(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="24") int size,
            @RequestParam(defaultValue="") String search,
            @RequestParam(required=false) String estado,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        EstadoViaje estadoFiltro = estado == null || estado.isBlank() ? null : EstadoViaje.desdeEtiqueta(estado);
        LocalDateTime desdeFiltro = desde == null ? null : desde.atStartOfDay();
        LocalDateTime hastaFiltro = hasta == null ? null : hasta.atTime(LocalTime.MAX);
        return viajes.buscar(search.trim(), estadoFiltro, desdeFiltro, hastaFiltro, page(page,size)).map(ViajeResponse::from);
    }
    @GetMapping("/mantenimientos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','MANTENIMIENTO')")
    public Page<MantenimientoResponse> mantenimientos(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="24") int size,
            @RequestParam(defaultValue="") String search,
            @RequestParam(required=false) Long vehiculoId,
            @RequestParam(required=false) String tipo,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required=false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        TipoMantenimiento tipoFiltro = tipo == null || tipo.isBlank() ? null : TipoMantenimiento.desdeEtiqueta(tipo);
        return mantenimientos.buscar(search.trim(), vehiculoId, tipoFiltro, desde, hasta, page(page,size)).map(MantenimientoResponse::from);
    }
    @GetMapping("/eventos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','COORDINADOR','CONDUCTOR','SUPERVISOR')")
    public Page<SeguimientoEventoResponse> eventos(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size){return eventos.findAll(page(page,size)).map(SeguimientoEventoResponse::from);}
}
