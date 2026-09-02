package com.trailersys.backend.seguimiento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.mantenimiento.MantenimientoRepository;
import com.trailersys.backend.seguimiento.dto.AlertaDto;
import com.trailersys.backend.seguimiento.dto.SeguimientoEventoRequest;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.ViajeRepository;

@Service
public class SeguimientoService {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", new Locale("es", "EC"));

    private final SeguimientoEventoRepository repository;
    private final ViajeRepository viajeRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final UsuarioRepository usuarioRepository;

    public SeguimientoService(SeguimientoEventoRepository repository, ViajeRepository viajeRepository,
                               MantenimientoRepository mantenimientoRepository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.viajeRepository = viajeRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<SeguimientoEvento> listarEventos(Long viajeId) {
        if (viajeId != null) {
            return repository.findByViajeIdOrderByFechaHoraDesc(viajeId);
        }
        return repository.findAll().stream()
                .sorted(Comparator.comparing(SeguimientoEvento::getFechaHora).reversed())
                .toList();
    }

    @Transactional
    public SeguimientoEvento crearEvento(SeguimientoEventoRequest request, String username) {
        Viaje viaje = viajeRepository.findById(request.viajeId())
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado: " + request.viajeId()));
        verificarPropioViajeSiEsConductor(viaje, username);

        SeguimientoEvento evento = new SeguimientoEvento(
                viaje, viaje.getVehiculo(), request.fechaHora(), request.evento(),
                request.ubicacion(), request.observacion());
        return repository.save(evento);
    }

    @Transactional
    public void eliminarEvento(Long id, String username) {
        SeguimientoEvento evento = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento de seguimiento no encontrado: " + id));
        verificarPropioViajeSiEsConductor(evento.getViaje(), username);
        repository.deleteById(id);
    }

    /**
     * PUEDE_GESTIONAR (SeguimientoController) incluye a CONDUCTOR "para que
     * registre sus propios eventos de ruta" - pero nada comprobaba que el
     * viaje fuera realmente suyo: cualquier cuenta CONDUCTOR autenticada
     * podia crear o borrar eventos de seguimiento de UN VIAJE AJENO con solo
     * cambiar el viajeId/id en la peticion. Administrador y Coordinador (sin
     * un Conductor vinculado a su Usuario) siguen sin restriccion, igual que
     * ya gestionan cualquier viaje desde el modulo Seguimiento.
     */
    private void verificarPropioViajeSiEsConductor(Viaje viaje, String username) {
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (usuario == null || usuario.getConductor() == null) {
            return;
        }
        if (!usuario.getConductor().getId().equals(viaje.getConductor().getId())) {
            throw new AccessDeniedException("Solo puedes gestionar eventos de tus propios viajes.");
        }
    }

    /**
     * Replica computeAlerts() de js/seguimiento.js: nada se guarda aparte,
     * todo se calcula en el momento a partir de Conductor/Vehiculo/Viaje/
     * Mantenimiento.
     */
    public List<AlertaDto> obtenerAlertas() {
        List<AlertaDto> alertas = new ArrayList<>();
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        List<Viaje> activos = viajeRepository.findTop100ByEstadoInOrderByFechaSalidaAsc(
                List.of(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO));

        for (Viaje viaje : activos) {
            Conductor conductor = viaje.getConductor();
            if (conductor.getLicenciaVencimiento() != null && conductor.getLicenciaVencimiento().isBefore(hoy)) {
                alertas.add(new AlertaDto("danger", "bi-person-x", String.format(
                        "La licencia de %s está vencida (venció el %s) y tiene un viaje %s de %s a %s.",
                        conductor.getNombres(), conductor.getLicenciaVencimiento(),
                        viaje.getEstado().getEtiqueta().toLowerCase(Locale.ROOT), viaje.getOrigen(), viaje.getDestino())));
            }

            Vehiculo vehiculo = viaje.getVehiculo();
            if (vehiculo.getEstado() == EstadoVehiculo.MANTENIMIENTO || vehiculo.getEstado() == EstadoVehiculo.FUERA_DE_SERVICIO) {
                alertas.add(new AlertaDto("danger", "bi-tools", String.format(
                        "El vehículo %s está en estado \"%s\" pero tiene un viaje %s asignado (%s → %s).",
                        vehiculo.getPlaca(), vehiculo.getEstado().getEtiqueta(),
                        viaje.getEstado().getEtiqueta().toLowerCase(Locale.ROOT), viaje.getOrigen(), viaje.getDestino())));
            }
        }

        activos.stream()
                .filter(v -> v.getEstado() == EstadoViaje.PROGRAMADO
                        && v.getFechaSalida() != null && v.getFechaSalida().isBefore(ahora))
                .forEach(v -> alertas.add(new AlertaDto("warning", "bi-alarm", String.format(
                        "El viaje de %s a %s sigue \"Programado\" pero su salida (%s) ya pasó.",
                        v.getOrigen(), v.getDestino(), v.getFechaSalida().format(FORMATO_FECHA)))));

        activos.stream()
                .filter(v -> v.getEstado() == EstadoViaje.EN_CURSO && v.getRutaDistanciaKm() == null)
                .forEach(v -> alertas.add(new AlertaDto("warning", "bi-map", String.format(
                        "El viaje de %s a %s está \"En Curso\" pero no tiene una ruta calculada todavía.",
                        v.getOrigen(), v.getDestino()))));

        // Diversifica antes de cortar en 100: en la practica muchos vehiculos
        // comparten la misma fecha de proximo servicio (una flota grande
        // suele agendar mantenimientos por lotes), asi que tomar directo
        // "los primeros 100 ordenados por fecha" dejaba una sola fecha
        // repetida 100 veces en vez de un panorama util. Se limita a un
        // vehiculo por alerta (el mas urgente que tenga) y a MAX_POR_FECHA
        // por cada fecha exacta, avanzando a la siguiente fecha una vez
        // cubierta esa cuota - conserva el orden "mas vencido primero" pero
        // sin ahogar el panel en duplicados del mismo dia.
        final int MAX_POR_FECHA = 10;
        java.util.Set<Long> vehiculosConAlerta = new java.util.HashSet<>();
        java.util.Map<LocalDate, Integer> alertasPorFecha = new java.util.HashMap<>();
        mantenimientoRepository.findTop2000ByProximoServicioLessThanEqualOrderByProximoServicioAsc(hoy.plusDays(7)).stream()
                .filter(m -> vehiculosConAlerta.add(m.getVehiculo().getId()))
                .filter(m -> alertasPorFecha.merge(m.getProximoServicio(), 1, Integer::sum) <= MAX_POR_FECHA)
                .limit(100)
                .forEach(m -> {
                    boolean vencido = m.getProximoServicio().isBefore(hoy);
                    alertas.add(new AlertaDto(vencido ? "danger" : "warning", "bi-tools", String.format(
                            vencido
                                    ? "El mantenimiento preventivo del vehículo %s venció el %s."
                                    : "El mantenimiento preventivo del vehículo %s corresponde el %s.",
                            m.getVehiculo().getPlaca(), m.getProximoServicio())));
                });

        // Aviso para el supervisor: el conductor ya confirmo la llegada,
        // falta que el supervisor le de el visto bueno (POST
        // /viajes/{id}/validar-entrega). Este mismo panel de alertas ya lo
        // ve el supervisor en Dashboard/Seguimiento, asi que no hace falta
        // un canal de notificacion aparte.
        viajeRepository.findTop100ByEntregaConfirmadaTrueAndEntregaValidadaFalseOrderByFechaEntregaConfirmadaDesc().stream()
                .forEach(v -> alertas.add(new AlertaDto("info", "bi-patch-check", String.format(
                        "El conductor confirmó la llegada del viaje %s → %s el %s. Pendiente de validación del supervisor.",
                        v.getOrigen(), v.getDestino(), v.getFechaEntregaConfirmada().format(FORMATO_FECHA)))));

        // El panel es operativo, no un reporte histórico: devolver una muestra
        // prioritaria evita crear miles de nodos y ocultar el mapa bajo la lista.
        return alertas.stream().limit(100).toList();
    }
}
