package com.trailersys.backend.conductor;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.conductor.dto.ConductorRequest;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.vehiculo.VehiculoRepository;

@Service
public class ConductorService {

    private final ConductorRepository repository;
    private final VehiculoRepository vehiculoRepository;

    public ConductorService(ConductorRepository repository, VehiculoRepository vehiculoRepository) {
        this.repository = repository;
        this.vehiculoRepository = vehiculoRepository;
    }

    public List<Conductor> listar(EstadoConductor estado, String search) {
        String texto = search == null ? "" : search.trim().toLowerCase();
        return repository.findAll().stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .filter(c -> texto.isEmpty()
                        || c.getNombres().toLowerCase().contains(texto)
                        || c.getIdentificacion().toLowerCase().contains(texto)
                        || c.getTelefono().toLowerCase().contains(texto))
                .toList();
    }

    public Conductor obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conductor no encontrado: " + id));
    }

    public Optional<Conductor> buscarPorVehiculo(Long vehiculoId) {
        return repository.findFirstByVehiculo_Id(vehiculoId);
    }

    @Transactional
    public Conductor crear(ConductorRequest request) {
        if (repository.existsByIdentificacionIgnoreCase(request.identificacion())) {
            throw new ConflictException("Ya existe un conductor con esta identificación.");
        }
        validarVehiculoDisponible(request.vehiculoId(), null);

        Conductor conductor = new Conductor(request.nombres(), request.identificacion(), request.telefono(),
                request.correo(), request.licenciaNumero(), request.licenciaCategoria(), request.licenciaVencimiento(),
                request.estado(), resolverVehiculo(request.vehiculoId()), request.observaciones(), request.foto());
        conductor.setFechaNacimiento(request.fechaNacimiento());
        return repository.save(conductor);
    }

    @Transactional
    public Conductor actualizar(Long id, ConductorRequest request) {
        Conductor conductor = obtener(id);

        repository.findByIdentificacionIgnoreCase(request.identificacion())
                .filter(existente -> !existente.getId().equals(id))
                .ifPresent(existente -> {
                    throw new ConflictException("Ya existe un conductor con esta identificación.");
                });
        validarVehiculoDisponible(request.vehiculoId(), id);

        conductor.setNombres(request.nombres());
        conductor.setIdentificacion(request.identificacion());
        conductor.setTelefono(request.telefono());
        conductor.setCorreo(request.correo());
        conductor.setLicenciaNumero(request.licenciaNumero());
        conductor.setLicenciaCategoria(request.licenciaCategoria());
        conductor.setLicenciaVencimiento(request.licenciaVencimiento());
        conductor.setEstado(request.estado());
        conductor.setVehiculo(resolverVehiculo(request.vehiculoId()));
        conductor.setObservaciones(request.observaciones());
        conductor.setFoto(request.foto());
        conductor.setFechaNacimiento(request.fechaNacimiento());

        return conductor;
    }

    /**
     * Sin este chequeo, el selector "Vehículo asignado" no impedia elegir un
     * vehiculo que YA es el vehiculo de OTRO conductor - quedaban dos
     * conductores distintos "siendo" el dueño del mismo vehiculo (el campo
     * es informativo, "mi vehiculo" en el Dashboard del conductor, pero
     * ambos lo verian como propio). idActual excluye al propio conductor
     * cuando se esta editando, para que guardarlo sin tocar su vehiculo no
     * se rechace a si mismo.
     */
    private void validarVehiculoDisponible(Long vehiculoId, Long idActual) {
        if (vehiculoId == null) {
            return;
        }
        repository.findFirstByVehiculo_Id(vehiculoId)
                .filter(c -> !c.getId().equals(idActual))
                .ifPresent(c -> {
                    throw new ConflictException(
                            "Ese vehículo ya está asignado al conductor \"" + c.getNombres() + "\".");
                });
    }

    @Transactional
    public void eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Conductor no encontrado: " + id);
        }
        repository.deleteById(id);
    }

    private Vehiculo resolverVehiculo(Long vehiculoId) {
        if (vehiculoId == null) {
            return null;
        }
        return vehiculoRepository.findById(vehiculoId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehiculo no encontrado: " + vehiculoId));
    }
}
