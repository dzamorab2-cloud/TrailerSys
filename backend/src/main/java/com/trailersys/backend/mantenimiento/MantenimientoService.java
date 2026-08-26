package com.trailersys.backend.mantenimiento;

import java.util.List;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.mantenimiento.dto.MantenimientoRequest;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.vehiculo.VehiculoRepository;

@Service
public class MantenimientoService {

    private final MantenimientoRepository repository;
    private final VehiculoRepository vehiculoRepository;

    public MantenimientoService(MantenimientoRepository repository, VehiculoRepository vehiculoRepository) {
        this.repository = repository;
        this.vehiculoRepository = vehiculoRepository;
    }

    public List<Mantenimiento> listar(Long vehiculoId, TipoMantenimiento tipo, String search) {
        String texto = search == null ? "" : search.trim().toLowerCase();
        List<Mantenimiento> base = vehiculoId != null
                ? repository.findByVehiculoIdOrderByFechaDesc(vehiculoId)
                : repository.findAll();

        return base.stream()
                .filter(m -> tipo == null || m.getTipo() == tipo)
                .filter(m -> texto.isEmpty()
                        || m.getDescripcion().toLowerCase().contains(texto)
                        || m.getVehiculo().getPlaca().toLowerCase().contains(texto))
                .sorted((a, b) -> b.getFecha().compareTo(a.getFecha()))
                .toList();
    }

    public Mantenimiento obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mantenimiento no encontrado: " + id));
    }

    @Transactional
    public Mantenimiento crear(MantenimientoRequest request) {
        validarFechas(request);
        Vehiculo vehiculo = resolverVehiculo(request.vehiculoId());
        var proximoServicio = calcularProximoServicio(request);

        Mantenimiento mantenimiento = new Mantenimiento(vehiculo, request.tipo(), request.fecha(),
                request.kilometraje(), request.costo(), proximoServicio, request.descripcion());
        sincronizarKilometraje(vehiculo, request.kilometraje());
        return repository.save(mantenimiento);
    }

    @Transactional
    public Mantenimiento actualizar(Long id, MantenimientoRequest request) {
        validarFechas(request);
        Mantenimiento mantenimiento = obtener(id);

        Vehiculo vehiculo = resolverVehiculo(request.vehiculoId());
        mantenimiento.setVehiculo(vehiculo);
        mantenimiento.setTipo(request.tipo());
        mantenimiento.setFecha(request.fecha());
        mantenimiento.setKilometraje(request.kilometraje());
        mantenimiento.setCosto(request.costo());
        mantenimiento.setProximoServicio(calcularProximoServicio(request));
        mantenimiento.setDescripcion(request.descripcion());
        sincronizarKilometraje(vehiculo, request.kilometraje());

        return mantenimiento;
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Mantenimiento no encontrado: " + id);
        }
        repository.deleteById(id);
    }

    private void validarFechas(MantenimientoRequest request) {
        if (request.proximoServicio() != null && request.proximoServicio().isBefore(request.fecha())) {
            throw new IllegalArgumentException("El próximo servicio debe ser posterior a la fecha del mantenimiento.");
        }
    }

    public List<Mantenimiento> calendario(LocalDate desde, LocalDate hasta) {
        return repository.findByProximoServicioBetweenOrderByProximoServicioAsc(desde, hasta);
    }

    private java.time.LocalDate calcularProximoServicio(MantenimientoRequest request) {
        if (request.proximoServicio() != null) {
            return request.proximoServicio();
        }
        // El próximo control preventivo es mensual, incluso cuando el registro
        // actual corresponde a una reparación correctiva.
        return request.fecha().plusMonths(1);
    }

    private Vehiculo resolverVehiculo(Long id) {
        return vehiculoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehiculo no encontrado: " + id));
    }

    private void sincronizarKilometraje(Vehiculo vehiculo, Integer kilometrajeMantenimiento) {
        if (kilometrajeMantenimiento != null && kilometrajeMantenimiento > vehiculo.getKilometraje()) {
            vehiculo.setKilometraje(kilometrajeMantenimiento);
        }
    }
}
