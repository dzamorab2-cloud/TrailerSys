package com.trailersys.backend.viaje;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.carga.Carga;
import com.trailersys.backend.carga.CargaRepository;
import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.ConductorRepository;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.vehiculo.VehiculoRepository;
import com.trailersys.backend.viaje.dto.RutaDto;
import com.trailersys.backend.viaje.dto.ViajeRequest;

@Service
public class ViajeService {

    private final ViajeRepository repository;
    private final VehiculoRepository vehiculoRepository;
    private final ConductorRepository conductorRepository;
    private final ClienteRepository clienteRepository;
    private final CargaRepository cargaRepository;

    public ViajeService(ViajeRepository repository, VehiculoRepository vehiculoRepository,
                         ConductorRepository conductorRepository, ClienteRepository clienteRepository,
                         CargaRepository cargaRepository) {
        this.repository = repository;
        this.vehiculoRepository = vehiculoRepository;
        this.conductorRepository = conductorRepository;
        this.clienteRepository = clienteRepository;
        this.cargaRepository = cargaRepository;
    }

    public List<Viaje> listar(EstadoViaje estado, String search) {
        String texto = search == null ? "" : search.trim().toLowerCase();
        return repository.findAll().stream()
                .filter(v -> estado == null || v.getEstado() == estado)
                .filter(v -> texto.isEmpty()
                        || v.getOrigen().toLowerCase().contains(texto)
                        || v.getDestino().toLowerCase().contains(texto)
                        || v.getVehiculo().getPlaca().toLowerCase().contains(texto)
                        || v.getConductor().getNombres().toLowerCase().contains(texto))
                .toList();
    }

    public Viaje obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado: " + id));
    }

    @Transactional
    public Viaje crear(ViajeRequest request) {
        Viaje viaje = new Viaje(
                resolverVehiculo(request.vehiculoId()),
                resolverConductor(request.conductorId()),
                resolverCliente(request.clienteId()),
                resolverCarga(request.cargaId()),
                request.origen(), request.destino(), request.fechaSalida(), request.estado(), request.observaciones());
        aplicarRuta(viaje, request.ruta());
        return repository.save(viaje);
    }

    @Transactional
    public Viaje actualizar(Long id, ViajeRequest request) {
        Viaje viaje = obtener(id);

        viaje.setVehiculo(resolverVehiculo(request.vehiculoId()));
        viaje.setConductor(resolverConductor(request.conductorId()));
        viaje.setCliente(resolverCliente(request.clienteId()));
        viaje.setCarga(resolverCarga(request.cargaId()));
        viaje.setOrigen(request.origen());
        viaje.setDestino(request.destino());
        viaje.setFechaSalida(request.fechaSalida());
        viaje.setEstado(request.estado());
        viaje.setObservaciones(request.observaciones());
        aplicarRuta(viaje, request.ruta());

        return viaje;
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Viaje no encontrado: " + id);
        }
        repository.deleteById(id);
    }

    private void aplicarRuta(Viaje viaje, RutaDto ruta) {
        if (ruta == null) {
            viaje.setRutaOrigenLat(null);
            viaje.setRutaOrigenLng(null);
            viaje.setRutaDestinoLat(null);
            viaje.setRutaDestinoLng(null);
            viaje.setRutaDistanciaKm(null);
            viaje.setRutaDuracionMin(null);
            viaje.setRutaPath(null);
            return;
        }

        viaje.setRutaOrigenLat(ruta.origenLat());
        viaje.setRutaOrigenLng(ruta.origenLng());
        viaje.setRutaDestinoLat(ruta.destinoLat());
        viaje.setRutaDestinoLng(ruta.destinoLng());
        viaje.setRutaDistanciaKm(ruta.distanciaKm());
        viaje.setRutaDuracionMin(ruta.duracionMin());
        viaje.setRutaPath(RutaPathCodec.serializar(ruta.path()));
    }

    private Vehiculo resolverVehiculo(Long id) {
        return vehiculoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehiculo no encontrado: " + id));
    }

    private Conductor resolverConductor(Long id) {
        return conductorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conductor no encontrado: " + id));
    }

    private Cliente resolverCliente(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + id));
    }

    private Carga resolverCarga(Long id) {
        if (id == null) {
            return null;
        }
        return cargaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carga no encontrada: " + id));
    }
}
