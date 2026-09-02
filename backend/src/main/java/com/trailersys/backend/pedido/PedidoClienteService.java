package com.trailersys.backend.pedido;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.carga.Carga;
import com.trailersys.backend.carga.CargaRepository;
import com.trailersys.backend.carga.EstadoCarga;
import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.pedido.dto.PedidoCargaRequest;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.ViajeRepository;
import com.trailersys.backend.seguimiento.SeguimientoEventoRepository;
import com.trailersys.backend.seguimiento.dto.SeguimientoEventoResponse;
import com.trailersys.backend.pedido.dto.DetallePedidoResponse;
import com.trailersys.backend.pedido.dto.PerfilClienteResponse;
import com.trailersys.backend.carga.dto.CargaResponse;
import com.trailersys.backend.viaje.dto.ViajeResponse;

/**
 * Autoservicio del rol CLIENTE: crear pedidos (Cargas en Pendiente),
 * consultar unicamente sus propias cargas/viajes, y confirmar la
 * recepcion de una entrega ya Finalizada. Todo se acota al Cliente
 * vinculado al Usuario autenticado (Usuario.cliente); nunca se confia en
 * un clienteId que venga del request ni en el id de una carga por si
 * solo, siempre se verifica la pertenencia primero (ver miCarga()).
 */
@Service
public class PedidoClienteService {

    private final UsuarioRepository usuarioRepository;
    private final CargaRepository cargaRepository;
    private final ViajeRepository viajeRepository;
    private final SeguimientoEventoRepository seguimientoRepository;

    public PedidoClienteService(UsuarioRepository usuarioRepository, CargaRepository cargaRepository,
                                 ViajeRepository viajeRepository, SeguimientoEventoRepository seguimientoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.cargaRepository = cargaRepository;
        this.viajeRepository = viajeRepository;
        this.seguimientoRepository = seguimientoRepository;
    }

    private Cliente miCliente(String username) {
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(username)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));
        Cliente cliente = usuario.getCliente();
        if (cliente == null) {
            throw new ConflictException("Este usuario no tiene un cliente asociado.");
        }
        return cliente;
    }

    /**
     * Busca la carga por id exigiendo en la misma consulta que pertenezca
     * al cliente de "username". Si el id no existe, o existe pero es de
     * otro cliente, se lanza el mismo 404 en ambos casos: nunca se revela
     * a un cliente si el id de otro cliente existe o no.
     */
    private Carga miCarga(String username, Long cargaId) {
        Long clienteId = miCliente(username).getId();
        return cargaRepository.findByIdAndCliente_Id(cargaId, clienteId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado: " + cargaId));
    }

    public List<Carga> listarMisCargas(String username) {
        return cargaRepository.findByCliente_IdOrderByIdDesc(miCliente(username).getId());
    }

    @Transactional
    public Carga crearPedido(String username, PedidoCargaRequest request) {
        Cliente cliente = miCliente(username);
        Carga carga = new Carga(request.descripcion(), cliente, request.tipo(), request.peso(),
                request.origen(), request.destino(), EstadoCarga.PENDIENTE, request.observaciones());
        return cargaRepository.save(carga);
    }

    /**
     * El cliente solo puede editar/cancelar su pedido mientras sigue
     * "Pendiente" (todavia sin viaje asignado, nadie de Operacion
     * comprometio un vehiculo/conductor con el). En cuanto Coordinador lo
     * asigna a un viaje, queda fuera de su alcance - igual que
     * confirmarRecepcion() ya exige lo contrario (una carga con viaje) para
     * su propio paso.
     */
    private Carga miCargaPendiente(String username, Long cargaId) {
        Carga carga = miCarga(username, cargaId);
        if (carga.getEstado() != EstadoCarga.PENDIENTE) {
            throw new ConflictException("Ya no puedes modificar este pedido: Operación ya le asignó un viaje.");
        }
        return carga;
    }

    @Transactional
    public Carga actualizarPedido(String username, Long cargaId, PedidoCargaRequest request) {
        Carga carga = miCargaPendiente(username, cargaId);
        carga.setDescripcion(request.descripcion());
        carga.setTipo(request.tipo());
        carga.setPeso(request.peso());
        carga.setOrigen(request.origen());
        carga.setDestino(request.destino());
        carga.setObservaciones(request.observaciones());
        return carga;
    }

    /**
     * "Cancelar" un pedido Pendiente antes se hacia con un DELETE real: la
     * fila desaparecia sin dejar rastro visible en ninguna pantalla (solo
     * quedaba en la auditoria de la base). Carga ya tenia el mismo patron
     * que Viaje (que si distingue "Cancelado" de "eliminado") sin usarlo -
     * ahora se archiva como CANCELADA en vez de borrarse, igual que un
     * viaje cancelado sigue visible en Viajes/Guias/Reportes.
     */
    @Transactional
    public void eliminarPedido(String username, Long cargaId) {
        Carga carga = miCargaPendiente(username, cargaId);
        carga.setEstado(EstadoCarga.CANCELADA);
    }

    public Viaje obtenerViajeDeMiCarga(String username, Long cargaId) {
        Carga carga = miCarga(username, cargaId);
        return viajeRepository.findFirstByCarga_IdOrderByIdDesc(carga.getId()).orElse(null);
    }

    public PerfilClienteResponse perfil(String username) {
        return PerfilClienteResponse.from(miCliente(username));
    }

    public DetallePedidoResponse detalle(String username, Long cargaId) {
        Carga carga = miCarga(username, cargaId);
        Viaje viaje = viajeRepository.findFirstByCarga_IdOrderByIdDesc(cargaId).orElse(null);
        var eventos = viaje == null ? List.<SeguimientoEventoResponse>of()
                : seguimientoRepository.findByViajeIdOrderByFechaHoraDesc(viaje.getId()).stream()
                        .map(SeguimientoEventoResponse::from).toList();
        return new DetallePedidoResponse(CargaResponse.from(carga), viaje == null ? null : ViajeResponse.from(viaje), eventos);
    }

    @Transactional
    public Viaje confirmarRecepcion(String username, Long cargaId, String observacion, String novedad, String evidencia) {
        Carga carga = miCarga(username, cargaId);
        Viaje viaje = viajeRepository.findFirstByCarga_IdOrderByIdDesc(carga.getId())
                .orElseThrow(() -> new ConflictException("Este pedido todavía no tiene un viaje asociado."));

        // Ya no exige viaje Finalizado: el cliente debe poder revisar su
        // carga apenas se confirma la llegada (automatica o manual, ver
        // ViajeService.registrarLlegada), antes de que Coordinador/
        // Administrador cierren el viaje con finalizarViaje(). Ese orden es
        // el que permite que un reclamo del cliente bloquee la finalizacion.
        if (!viaje.isEntregaConfirmada()) {
            throw new ConflictException("Solo puedes confirmar la recepción de un pedido cuya llegada ya fue confirmada.");
        }
        if (viaje.isEntregaConfirmadaCliente()) {
            throw new ConflictException("Ya confirmaste la recepción de este pedido.");
        }

        viaje.setEntregaConfirmadaCliente(true);
        viaje.setFechaConfirmacionCliente(LocalDateTime.now());
        viaje.setObservacionConfirmacionCliente(observacion);
        viaje.setConfirmadoPorCliente(username);
        String tipo = novedad == null || novedad.isBlank() ? "COMPLETO" : novedad.trim().toUpperCase();
        if (!List.of("COMPLETO", "INCOMPLETO", "DANADO", "INCORRECTO", "OTRO").contains(tipo)) {
            throw new ConflictException("Tipo de recepción no válido.");
        }
        if (!"COMPLETO".equals(tipo) && (observacion == null || observacion.isBlank())) {
            throw new ConflictException("Describe el problema para registrar el reclamo.");
        }
        if (evidencia != null && evidencia.length() > 7_000_000) {
            throw new ConflictException("La evidencia supera el tamaño permitido.");
        }
        viaje.setNovedadRecepcionCliente(tipo);
        viaje.setEvidenciaRecepcionCliente(evidencia);
        viaje.setEstadoReclamoCliente("COMPLETO".equals(tipo) ? null : "ABIERTO");
        return viaje;
    }
}
