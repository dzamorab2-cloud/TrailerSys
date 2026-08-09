package com.trailersys.backend.cliente;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.cliente.dto.ClienteRequest;
import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;

@Service
public class ClienteService {

    private final ClienteRepository repository;

    public ClienteService(ClienteRepository repository) {
        this.repository = repository;
    }

    public List<Cliente> listar(EstadoCliente estado, String search) {
        String texto = search == null ? "" : search.trim().toLowerCase();
        return repository.findAll().stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .filter(c -> texto.isEmpty()
                        || c.getNombre().toLowerCase().contains(texto)
                        || c.getIdentificacion().toLowerCase().contains(texto)
                        || c.getTelefono().toLowerCase().contains(texto))
                .toList();
    }

    public Cliente obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + id));
    }

    @Transactional
    public Cliente crear(ClienteRequest request) {
        if (repository.existsByIdentificacionIgnoreCase(request.identificacion())) {
            throw new ConflictException("Ya existe un cliente con esta identificación.");
        }

        Cliente cliente = new Cliente(request.nombre(), request.identificacion(), request.estado(),
                request.telefono(), request.correo(), request.direccion(), request.servicios(),
                request.observaciones());
        return repository.save(cliente);
    }

    @Transactional
    public Cliente actualizar(Long id, ClienteRequest request) {
        Cliente cliente = obtener(id);

        repository.findByIdentificacionIgnoreCase(request.identificacion())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new ConflictException("Ya existe un cliente con esta identificación.");
                });

        cliente.setNombre(request.nombre());
        cliente.setIdentificacion(request.identificacion());
        cliente.setEstado(request.estado());
        cliente.setTelefono(request.telefono());
        cliente.setCorreo(request.correo());
        cliente.setDireccion(request.direccion());
        cliente.setServicios(request.servicios());
        cliente.setObservaciones(request.observaciones());

        return cliente;
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Cliente no encontrado: " + id);
        }
        repository.deleteById(id);
    }
}
