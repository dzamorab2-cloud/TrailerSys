package com.trailersys.backend.seguimiento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.mantenimiento.MantenimientoRepository;
import com.trailersys.backend.seguimiento.dto.AlertaDto;
import com.trailersys.backend.seguimiento.dto.SeguimientoEventoRequest;
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

    public SeguimientoService(SeguimientoEventoRepository repository, ViajeRepository viajeRepository,
                               MantenimientoRepository mantenimientoRepository) {
        this.repository = repository;
        this.viajeRepository = viajeRepository;
        this.mantenimientoRepository = mantenimientoRepository;
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
    public SeguimientoEvento crearEvento(SeguimientoEventoRequest request) {
        Viaje viaje = viajeRepository.findById(request.viajeId())
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado: " + request.viajeId()));

        SeguimientoEvento evento = new SeguimientoEvento(
                viaje, viaje.getVehiculo(), request.fechaHora(), request.evento(),
                request.ubicacion(), request.observacion());
        return repository.save(evento);
    }

    @Transactional
    public void eliminarEvento(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Evento de seguimiento no encontrado: " + id);
        }
        repository.deleteById(id);
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

        List<Viaje> viajes = viajeRepository.findAll();
        List<Viaje> activos = viajes.stream()
                .filter(v -> v.getEstado() == EstadoViaje.PROGRAMADO || v.getEstado() == EstadoViaje.EN_CURSO)
                .toList();

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

        viajes.stream()
                .filter(v -> v.getEstado() == EstadoViaje.PROGRAMADO
                        && v.getFechaSalida() != null && v.getFechaSalida().isBefore(ahora))
                .forEach(v -> alertas.add(new AlertaDto("warning", "bi-alarm", String.format(
                        "El viaje de %s a %s sigue \"Programado\" pero su salida (%s) ya pasó.",
                        v.getOrigen(), v.getDestino(), v.getFechaSalida().format(FORMATO_FECHA)))));

        viajes.stream()
                .filter(v -> v.getEstado() == EstadoViaje.EN_CURSO && v.getRutaDistanciaKm() == null)
                .forEach(v -> alertas.add(new AlertaDto("warning", "bi-map", String.format(
                        "El viaje de %s a %s está \"En Curso\" pero no tiene una ruta calculada todavía.",
                        v.getOrigen(), v.getDestino()))));

        mantenimientoRepository.findAll().stream()
                .filter(m -> m.getProximoServicio() != null && m.getProximoServicio().isBefore(hoy))
                .forEach(m -> alertas.add(new AlertaDto("warning", "bi-tools", String.format(
                        "El próximo servicio del vehículo %s venció el %s.",
                        m.getVehiculo().getPlaca(), m.getProximoServicio()))));

        return alertas;
    }
}
