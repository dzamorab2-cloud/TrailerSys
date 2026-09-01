package com.trailersys.backend.conductor.dto;

import java.time.LocalDate;
import java.time.Period;

import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.EstadoConductor;

public record ConductorResponse(
        Long id,
        String nombres,
        String identificacion,
        String telefono,
        String correo,
        String licenciaNumero,
        String licenciaCategoria,
        LocalDate licenciaVencimiento,
        boolean licenciaVencida,
        EstadoConductor estado,
        Long vehiculoId,
        String vehiculoPlaca,
        String observaciones,
        String foto,
        LocalDate fechaNacimiento,
        Integer edad
) {
    public static ConductorResponse from(Conductor c) {
        boolean vencida = c.getLicenciaVencimiento() != null && c.getLicenciaVencimiento().isBefore(LocalDate.now());
        Long vehiculoId = c.getVehiculo() != null ? c.getVehiculo().getId() : null;
        String vehiculoPlaca = c.getVehiculo() != null ? c.getVehiculo().getPlaca() : null;
        // Se calcula siempre al leer (nunca se guarda un numero de edad
        // aparte) para que nunca quede desactualizada.
        Integer edad = c.getFechaNacimiento() == null ? null
                : Period.between(c.getFechaNacimiento(), LocalDate.now()).getYears();

        return new ConductorResponse(c.getId(), c.getNombres(), c.getIdentificacion(), c.getTelefono(), c.getCorreo(),
                c.getLicenciaNumero(), c.getLicenciaCategoria(), c.getLicenciaVencimiento(), vencida, c.getEstado(),
                vehiculoId, vehiculoPlaca, c.getObservaciones(), c.getFoto(), c.getFechaNacimiento(), edad);
    }
}
