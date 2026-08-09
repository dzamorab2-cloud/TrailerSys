package com.trailersys.backend.carga;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.carga.dto.CargaRequest;
import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.common.ResourceNotFoundException;

@Service
public class CargaService {

    private final CargaRepository repository;
    private final ClienteRepository clienteRepository;

    public CargaService(CargaRepository repository, ClienteRepository clienteRepository) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
    }

    public List<Carga> listar(EstadoCarga estado, String search) {
        String texto = search == null ? "" : search.trim().toLowerCase();
        return repository.findAll().stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .filter(c -> texto.isEmpty()
                        || c.getDescripcion().toLowerCase().contains(texto)
                        || c.getOrigen().toLowerCase().contains(texto)
                        || c.getDestino().toLowerCase().contains(texto)
                        || c.getCliente().getNombre().toLowerCase().contains(texto))
                .toList();
    }

    public Carga obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carga no encontrada: " + id));
    }

    @Transactional
    public Carga crear(CargaRequest request) {
        Cliente cliente = resolverCliente(request.clienteId());
        Carga carga = new Carga(request.descripcion(), cliente, request.tipo(), request.peso(),
                request.origen(), request.destino(), request.estado(), request.observaciones());
        return repository.save(carga);
    }

    @Transactional
    public Carga actualizar(Long id, CargaRequest request) {
        Carga carga = obtener(id);
        Cliente cliente = resolverCliente(request.clienteId());

        carga.setDescripcion(request.descripcion());
        carga.setCliente(cliente);
        carga.setTipo(request.tipo());
        carga.setPeso(request.peso());
        carga.setOrigen(request.origen());
        carga.setDestino(request.destino());
        carga.setEstado(request.estado());
        carga.setObservaciones(request.observaciones());

        return carga;
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Carga no encontrada: " + id);
        }
        repository.deleteById(id);
    }

    private Cliente resolverCliente(Long clienteId) {
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + clienteId));
    }
}
