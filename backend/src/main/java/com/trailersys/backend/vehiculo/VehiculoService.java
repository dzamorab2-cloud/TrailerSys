package com.trailersys.backend.vehiculo;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.vehiculo.dto.VehiculoRequest;

@Service
public class VehiculoService {

    private final VehiculoRepository repository;

    public VehiculoService(VehiculoRepository repository) {
        this.repository = repository;
    }

    public List<Vehiculo> listar(EstadoVehiculo estado, String search) {
        String texto = search == null ? "" : search.trim().toLowerCase();
        return repository.findAll().stream()
                .filter(v -> estado == null || v.getEstado() == estado)
                .filter(v -> texto.isEmpty()
                        || v.getPlaca().toLowerCase().contains(texto)
                        || v.getMarca().toLowerCase().contains(texto)
                        || v.getModelo().toLowerCase().contains(texto))
                .toList();
    }

    public Vehiculo obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehiculo no encontrado: " + id));
    }

    @Transactional
    public Vehiculo crear(VehiculoRequest request) {
        if (repository.existsByPlacaIgnoreCase(request.placa())) {
            throw new ConflictException("Ya existe un vehiculo con esta placa.");
        }

        Vehiculo vehiculo = new Vehiculo(request.placa(), request.marca(), request.modelo(), request.tipo(),
                request.anio(), request.color(), request.estado(), request.kilometraje(), request.capacidad(),
                request.observaciones(), request.foto());
        return repository.save(vehiculo);
    }

    @Transactional
    public Vehiculo actualizar(Long id, VehiculoRequest request) {
        Vehiculo vehiculo = obtener(id);

        repository.findByPlacaIgnoreCase(request.placa())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new ConflictException("Ya existe un vehiculo con esta placa.");
                });

        vehiculo.setPlaca(request.placa());
        vehiculo.setMarca(request.marca());
        vehiculo.setModelo(request.modelo());
        vehiculo.setTipo(request.tipo());
        vehiculo.setAnio(request.anio());
        vehiculo.setColor(request.color());
        vehiculo.setEstado(request.estado());
        vehiculo.setKilometraje(request.kilometraje());
        vehiculo.setCapacidad(request.capacidad());
        vehiculo.setObservaciones(request.observaciones());
        vehiculo.setFoto(request.foto());

        return vehiculo;
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Vehiculo no encontrado: " + id);
        }
        repository.deleteById(id);
    }
}
