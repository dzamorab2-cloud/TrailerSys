package com.trailersys.backend.carga;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.carga.dto.CargaRequest;
import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.common.ConflictException;
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

    /**
     * Igual que PedidoClienteService.miCargaPendiente() del lado del
     * cliente: editar o eliminar una carga solo tiene sentido mientras
     * sigue "Pendiente" (todavia sin viaje asignado). En cuanto un
     * Coordinador le arma un viaje, sincronizarEstadoCarga() ya la saca de
     * Pendiente (ver ViajeService), asi que este chequeo alcanza para
     * saber si algun viaje depende de ella - sin esto, eliminar() podia
     * toparse con el mismo tipo de error de integridad referencial que se
     * corrigio para Viajes (viajes.carga_id apunta a esta fila), y
     * actualizar() dejaba pisar el origen/destino/cliente/estado de una
     * carga que Operacion ya esta transportando, desincronizandola del
     * viaje que la referencia (que guarda su propia copia de esos datos).
     */
    private void requerirPendiente(Carga carga) {
        if (carga.getEstado() != EstadoCarga.PENDIENTE) {
            throw new ConflictException(
                    "Solo se puede editar o eliminar una carga que sigue \"Pendiente\": esta ya tiene un viaje asignado.");
        }
    }

    @Transactional
    public Carga actualizar(Long id, CargaRequest request) {
        Carga carga = obtener(id);
        requerirPendiente(carga);
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
        Carga carga = obtener(id);
        requerirPendiente(carga);
        repository.deleteById(id);
    }

    private Cliente resolverCliente(Long clienteId) {
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + clienteId));
    }
}
