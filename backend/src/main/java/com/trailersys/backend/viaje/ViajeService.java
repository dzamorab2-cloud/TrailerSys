package com.trailersys.backend.viaje;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.carga.Carga;
import com.trailersys.backend.carga.CargaRepository;
import com.trailersys.backend.carga.EstadoCarga;
import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.ConductorRepository;
import com.trailersys.backend.seguimiento.SeguimientoEvento;
import com.trailersys.backend.seguimiento.SeguimientoEventoRepository;
import com.trailersys.backend.seguimiento.TipoEvento;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.vehiculo.VehiculoRepository;
import com.trailersys.backend.conductor.EstadoConductor;
import com.trailersys.backend.viaje.dto.RutaDto;
import com.trailersys.backend.viaje.dto.ViajeRequest;

@Service
public class ViajeService {

    private final ViajeRepository repository;
    private final VehiculoRepository vehiculoRepository;
    private final ConductorRepository conductorRepository;
    private final ClienteRepository clienteRepository;
    private final CargaRepository cargaRepository;
    private final SeguimientoEventoRepository seguimientoEventoRepository;

    public ViajeService(ViajeRepository repository, VehiculoRepository vehiculoRepository,
                         ConductorRepository conductorRepository, ClienteRepository clienteRepository,
                         CargaRepository cargaRepository, SeguimientoEventoRepository seguimientoEventoRepository) {
        this.repository = repository;
        this.vehiculoRepository = vehiculoRepository;
        this.conductorRepository = conductorRepository;
        this.clienteRepository = clienteRepository;
        this.cargaRepository = cargaRepository;
        this.seguimientoEventoRepository = seguimientoEventoRepository;
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

    public Optional<Viaje> buscarUltimoPorCarga(Long cargaId) {
        return repository.findFirstByCarga_IdOrderByIdDesc(cargaId);
    }

    @Transactional
    public Viaje crear(ViajeRequest request) {
        Cliente cliente = resolverCliente(request.clienteId());
        Carga carga = resolverCarga(request.cargaId());
        Vehiculo vehiculo = resolverVehiculo(request.vehiculoId());
        Conductor conductor = resolverConductor(request.conductorId());
        if (esActivo(request.estado())) {
            validarCarga(carga, cliente, null);
            validarDisponibilidad(vehiculo, conductor, null);
        }

        Viaje viaje = new Viaje(
                vehiculo,
                conductor,
                cliente,
                carga,
                request.origen(), request.destino(), request.fechaSalida(), request.estado(), request.observaciones());
        aplicarRuta(viaje, request.ruta());
        viaje = repository.save(viaje);

        if (viaje.getEstado() == EstadoViaje.EN_CURSO) {
            registrarSalidaAutomatica(viaje);
        }
        sincronizarEstadoCarga(viaje);
        sincronizarEstadoVehiculoYConductor(viaje);
        return viaje;
    }

    @Transactional
    public Viaje actualizar(Long id, ViajeRequest request) {
        Viaje viaje = obtener(id);
        EstadoViaje estadoAnterior = viaje.getEstado();
        Carga cargaAnterior = viaje.getCarga();
        Vehiculo vehiculoAnterior = viaje.getVehiculo();
        Conductor conductorAnterior = viaje.getConductor();

        Cliente cliente = resolverCliente(request.clienteId());
        Carga carga = resolverCarga(request.cargaId());
        Vehiculo vehiculo = resolverVehiculo(request.vehiculoId());
        Conductor conductor = resolverConductor(request.conductorId());
        if (esActivo(request.estado())) {
            validarCarga(carga, cliente, id);
            validarDisponibilidad(vehiculo, conductor, id);
        }

        viaje.setVehiculo(vehiculo);
        viaje.setConductor(conductor);
        viaje.setCliente(cliente);
        viaje.setCarga(carga);
        viaje.setOrigen(request.origen());
        viaje.setDestino(request.destino());
        viaje.setFechaSalida(request.fechaSalida());
        viaje.setEstado(request.estado());
        viaje.setObservaciones(request.observaciones());
        aplicarRuta(viaje, request.ruta());

        if (estadoAnterior != EstadoViaje.EN_CURSO && viaje.getEstado() == EstadoViaje.EN_CURSO) {
            registrarSalidaAutomatica(viaje);
        }
        sincronizarEstadoCarga(viaje);
        liberarCargaSiCambio(cargaAnterior, carga);
        sincronizarEstadoVehiculoYConductor(viaje);
        liberarVehiculoYConductorSiCambiaron(vehiculoAnterior, vehiculo, conductorAnterior, conductor);
        return viaje;
    }

    /**
     * Pasa a EN_CURSO (con su evento de Salida y sincronizacion de carga)
     * cualquier viaje Programado cuya fechaSalida ya llego, para que el
     * coordinador no tenga que cambiar el estado a mano. Llamado desde
     * ViajeSimulacionService en cada ciclo del scheduler.
     */
    @Transactional
    public void iniciarViajesProgramadosVencidos() {
        LocalDateTime ahora = LocalDateTime.now();
        repository.findTop500ByEstadoAndFechaSalidaLessThanEqualOrderByFechaSalidaAsc(
                        EstadoViaje.PROGRAMADO, ahora).stream()
                // Si el vehiculo o el conductor ya estan en otro viaje activo, se
                // deja en Programado (se reintenta en el proximo ciclo) en vez
                // de arrancarlo igual y duplicar la asignacion.
                .filter(v -> estaDisponible(v.getVehiculo(), v.getConductor(), v.getId()))
                .forEach(viaje -> {
                    viaje.setEstado(EstadoViaje.EN_CURSO);
                    registrarSalidaAutomatica(viaje);
                    sincronizarEstadoCarga(viaje);
                    sincronizarEstadoVehiculoYConductor(viaje);
                });
    }

    private void registrarSalidaAutomatica(Viaje viaje) {
        seguimientoEventoRepository.save(new SeguimientoEvento(
                viaje, viaje.getVehiculo(), LocalDateTime.now(), TipoEvento.SALIDA, viaje.getOrigen(),
                "Salida registrada automáticamente al iniciar el viaje."));
    }

    /**
     * Evita que una carga quede asignada a un cliente distinto del viaje, o
     * a dos viajes activos (Programado/En Curso) al mismo tiempo. En
     * actualizar(), viajeIdActual excluye al propio viaje de la comparacion.
     */
    private void validarCarga(Carga carga, Cliente cliente, Long viajeIdActual) {
        if (carga == null) {
            return;
        }
        if (!carga.getCliente().getId().equals(cliente.getId())) {
            throw new IllegalArgumentException("La carga seleccionada pertenece a otro cliente.");
        }
        boolean asignadaAOtroViajeActivo = repository.findByCarga_Id(carga.getId()).stream()
                .anyMatch(v -> !v.getId().equals(viajeIdActual)
                        && (v.getEstado() == EstadoViaje.PROGRAMADO || v.getEstado() == EstadoViaje.EN_CURSO));
        if (asignadaAOtroViajeActivo) {
            throw new ConflictException("Esta carga ya está asignada a otro viaje activo.");
        }
    }

    /**
     * Mantiene el estado de la Carga alineado con el del Viaje que la
     * transporta, para no tener que actualizarlo a mano en dos modulos.
     * Cancelado no se mapea a proposito (fuera de alcance).
     */
    private void sincronizarEstadoCarga(Viaje viaje) {
        Carga carga = viaje.getCarga();
        if (carga == null) {
            return;
        }
        EstadoCarga nuevoEstado = switch (viaje.getEstado()) {
            case PROGRAMADO -> EstadoCarga.ASIGNADA;
            case EN_CURSO -> EstadoCarga.EN_TRANSITO;
            case FINALIZADO -> EstadoCarga.ENTREGADA;
            case CANCELADO -> null;
        };
        if (nuevoEstado != null && carga.getEstado() != nuevoEstado) {
            carga.setEstado(nuevoEstado);
            cargaRepository.save(carga);
        }
    }

    /**
     * Las validaciones de disponibilidad (carga, vehiculo, conductor) solo
     * tienen sentido cuando el viaje va a quedar Programado o En Curso. Si
     * se esta finalizando o cancelando, el viaje deja de "ocupar" esos
     * recursos, asi que no hay nada que validar: bloquearlo aqui impediria
     * cerrar un viaje precisamente para liberar un vehiculo/conductor que
     * otro viaje activo necesita.
     */
    private boolean esActivo(EstadoViaje estado) {
        return estado == EstadoViaje.PROGRAMADO || estado == EstadoViaje.EN_CURSO;
    }

    /**
     * Evita asignar a un viaje un vehiculo o conductor que ya esta en otro
     * viaje activo (Programado/En Curso). Mismo patron que validarCarga.
     *
     * El vehiculo y el conductor se evaluan de forma INDEPENDIENTE al
     * decidir si hace falta revisar su estado actual: si el vehiculo nuevo
     * es el mismo que ya tenia este viaje, no se revalida su estado (esta
     * en EN_RUTA precisamente porque este viaje lo esta usando), sin
     * importar si el conductor cambio o no, y viceversa con el conductor.
     * Antes una sola bandera "mismosRecursos" exigia que AMBOS coincidieran
     * a la vez para saltarse la revalidacion, lo que rechazaba con un 409
     * incorrecto el caso normal de "cambiar solo la unidad asignada"
     * (mismo conductor, otro vehiculo): al no coincidir el vehiculo, se
     * volvia a exigir tambien que el conductor estuviera Disponible, y el
     * conductor seguia EN_RUTA en la base por este mismo viaje.
     */
    private void validarDisponibilidad(Vehiculo vehiculo, Conductor conductor, Long viajeIdActual) {
        Viaje viajeActual = viajeIdActual == null ? null : repository.findById(viajeIdActual).orElse(null);
        boolean vehiculoSinCambios = viajeActual != null && viajeActual.getVehiculo().getId().equals(vehiculo.getId());
        boolean conductorSinCambios = viajeActual != null && viajeActual.getConductor().getId().equals(conductor.getId());

        if (!vehiculoSinCambios && vehiculo.getEstado() != EstadoVehiculo.DISPONIBLE) {
            throw new ConflictException("El vehículo seleccionado no está disponible.");
        }
        if (!conductorSinCambios && conductor.getEstado() != EstadoConductor.DISPONIBLE) {
            throw new ConflictException("El conductor seleccionado no está disponible.");
        }
        if (!estaDisponible(vehiculo, conductor, viajeIdActual)) {
            boolean vehiculoOcupado = !vehiculoLibre(vehiculo.getId(), viajeIdActual);
            throw new ConflictException(vehiculoOcupado
                    ? "El vehículo seleccionado ya está asignado a otro viaje activo."
                    : "El conductor seleccionado ya está asignado a otro viaje activo.");
        }
    }

    private boolean estaDisponible(Vehiculo vehiculo, Conductor conductor, Long viajeIdActual) {
        return vehiculoLibre(vehiculo.getId(), viajeIdActual) && conductorLibre(conductor.getId(), viajeIdActual);
    }

    private boolean vehiculoLibre(Long vehiculoId, Long viajeIdActual) {
        return repository.findByVehiculo_Id(vehiculoId).stream()
                .noneMatch(v -> !v.getId().equals(viajeIdActual)
                        && (v.getEstado() == EstadoViaje.PROGRAMADO || v.getEstado() == EstadoViaje.EN_CURSO));
    }

    private boolean conductorLibre(Long conductorId, Long viajeIdActual) {
        return repository.findByConductor_Id(conductorId).stream()
                .noneMatch(v -> !v.getId().equals(viajeIdActual)
                        && (v.getEstado() == EstadoViaje.PROGRAMADO || v.getEstado() == EstadoViaje.EN_CURSO));
    }

    /**
     * Si actualizar() reasigna el viaje a otra carga, la carga anterior no
     * queda referenciada por esta fila y sincronizarEstadoCarga() ya no la
     * toca (opera sobre viaje.getCarga(), que ahora es la nueva). Sin este
     * metodo, la carga anterior se quedaba "Asignada"/"En Transito" para
     * siempre aunque ningun viaje activo la citara.
     */
    private void liberarCargaSiCambio(Carga cargaAnterior, Carga cargaNueva) {
        if (cargaAnterior == null || (cargaNueva != null && cargaAnterior.getId().equals(cargaNueva.getId()))) {
            return;
        }
        boolean referenciadaPorOtroViajeActivo = repository.findByCarga_Id(cargaAnterior.getId()).stream()
                .anyMatch(v -> v.getEstado() == EstadoViaje.PROGRAMADO || v.getEstado() == EstadoViaje.EN_CURSO);
        if (!referenciadaPorOtroViajeActivo && cargaAnterior.getEstado() != EstadoCarga.ENTREGADA) {
            cargaAnterior.setEstado(EstadoCarga.PENDIENTE);
            cargaRepository.save(cargaAnterior);
        }
    }

    /**
     * Mismo problema que liberarCargaSiCambio pero para el vehiculo y el
     * conductor: si actualizar() les asigna otro vehiculo/conductor al
     * viaje, el anterior se quedaba en EN_RUTA de forma permanente porque
     * sincronizarEstadoVehiculoYConductor() ya solo ve al nuevo.
     */
    private void liberarVehiculoYConductorSiCambiaron(Vehiculo vehiculoAnterior, Vehiculo vehiculoNuevo,
                                                       Conductor conductorAnterior, Conductor conductorNuevo) {
        if (!vehiculoAnterior.getId().equals(vehiculoNuevo.getId())
                && vehiculoAnterior.getEstado() == EstadoVehiculo.EN_RUTA
                && vehiculoLibre(vehiculoAnterior.getId(), null)) {
            vehiculoAnterior.setEstado(EstadoVehiculo.DISPONIBLE);
            vehiculoRepository.save(vehiculoAnterior);
        }
        if (!conductorAnterior.getId().equals(conductorNuevo.getId())
                && conductorAnterior.getEstado() == EstadoConductor.EN_RUTA
                && conductorLibre(conductorAnterior.getId(), null)) {
            conductorAnterior.setEstado(EstadoConductor.DISPONIBLE);
            conductorRepository.save(conductorAnterior);
        }
    }

    /**
     * Igual que sincronizarEstadoCarga pero para el vehiculo y el
     * conductor: solo mueve DISPONIBLE<->EN_RUTA, sin pisar otros estados
     * manuales (Mantenimiento, Fuera de Servicio, Descanso, Inactivo).
     */
    private void sincronizarEstadoVehiculoYConductor(Viaje viaje) {
        boolean activo = viaje.getEstado() == EstadoViaje.PROGRAMADO || viaje.getEstado() == EstadoViaje.EN_CURSO;
        boolean terminado = viaje.getEstado() == EstadoViaje.FINALIZADO || viaje.getEstado() == EstadoViaje.CANCELADO;

        Vehiculo vehiculo = viaje.getVehiculo();
        if (activo && vehiculo.getEstado() == EstadoVehiculo.DISPONIBLE) {
            vehiculo.setEstado(EstadoVehiculo.EN_RUTA);
            vehiculoRepository.save(vehiculo);
        } else if (terminado && vehiculo.getEstado() == EstadoVehiculo.EN_RUTA) {
            vehiculo.setEstado(EstadoVehiculo.DISPONIBLE);
            vehiculoRepository.save(vehiculo);
        }

        Conductor conductor = viaje.getConductor();
        if (activo && conductor.getEstado() == EstadoConductor.DISPONIBLE) {
            conductor.setEstado(EstadoConductor.EN_RUTA);
            conductorRepository.save(conductor);
        } else if (terminado && conductor.getEstado() == EstadoConductor.EN_RUTA) {
            conductor.setEstado(EstadoConductor.DISPONIBLE);
            conductorRepository.save(conductor);
        }
    }

    /**
     * El conductor confirma que la carga llego a destino: cierra el viaje
     * (pasa a FINALIZADO) y deja un registro de auditoria (quien, cuando,
     * observacion). La validacion del supervisor es un paso aparte que no
     * bloquea este cierre.
     */
    @Transactional
    public Viaje confirmarEntrega(Long id, String observacion, String username) {
        Viaje viaje = obtener(id);

        if (viaje.getEstado() != EstadoViaje.EN_CURSO) {
            throw new ConflictException("Solo se puede confirmar la llegada de un viaje que esta \"En Curso\".");
        }
        if (viaje.isEntregaConfirmada()) {
            throw new ConflictException("La llegada de este viaje ya fue confirmada.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        viaje.setEntregaConfirmada(true);
        viaje.setFechaEntregaConfirmada(ahora);
        viaje.setObservacionEntrega(observacion);
        viaje.setConfirmadoPor(username);
        viaje.setEstado(EstadoViaje.FINALIZADO);

        seguimientoEventoRepository.save(new SeguimientoEvento(
                viaje, viaje.getVehiculo(), ahora, TipoEvento.LLEGADA, viaje.getDestino(),
                observacion == null || observacion.isBlank()
                        ? "Llegada confirmada por el conductor."
                        : observacion));

        sincronizarEstadoCarga(viaje);
        sincronizarEstadoVehiculoYConductor(viaje);
        return viaje;
    }

    /**
     * El supervisor valida una entrega ya confirmada por el conductor. Es
     * un registro de auditoria aparte: no cambia el estado del viaje.
     */
    @Transactional
    public Viaje validarEntrega(Long id, String observacion, String username) {
        Viaje viaje = obtener(id);

        if (!viaje.isEntregaConfirmada()) {
            throw new ConflictException("Todavia no hay una llegada confirmada por el conductor para validar.");
        }
        if (viaje.isEntregaValidada()) {
            throw new ConflictException("Esta entrega ya fue validada.");
        }

        viaje.setEntregaValidada(true);
        viaje.setFechaValidacionEntrega(LocalDateTime.now());
        viaje.setObservacionValidacion(observacion);
        viaje.setValidadoPor(username);

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
