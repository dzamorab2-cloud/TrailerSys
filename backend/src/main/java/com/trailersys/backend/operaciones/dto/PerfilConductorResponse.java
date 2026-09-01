package com.trailersys.backend.operaciones.dto;

import java.time.LocalDate;
import java.time.Period;

import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.EstadoConductor;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;

/**
 * Perfil propio del conductor para su Dashboard: sus datos personales mas
 * los del vehiculo que tiene asignado, aplanados (igual que ViajeResponse
 * aplana vehiculo/conductor) para que el frontend no necesite otro fetch.
 * vehiculoXxx queda todo null si el conductor no tiene vehiculo asignado.
 */
public record PerfilConductorResponse(
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
        String foto,
        LocalDate fechaNacimiento,
        Integer edad,
        Long vehiculoId,
        String vehiculoPlaca,
        String vehiculoMarca,
        String vehiculoModelo,
        String vehiculoTipo,
        Integer vehiculoAnio,
        String vehiculoColor,
        EstadoVehiculo vehiculoEstado,
        Integer vehiculoCapacidad,
        String vehiculoFoto
) {
    public static PerfilConductorResponse from(Conductor c) {
        boolean vencida = c.getLicenciaVencimiento() != null && c.getLicenciaVencimiento().isBefore(LocalDate.now());
        // Se calcula siempre al leer (nunca se guarda un numero de edad
        // aparte) para que nunca quede desactualizada.
        Integer edad = c.getFechaNacimiento() == null ? null
                : Period.between(c.getFechaNacimiento(), LocalDate.now()).getYears();
        Vehiculo v = c.getVehiculo();

        return new PerfilConductorResponse(c.getId(), c.getNombres(), c.getIdentificacion(), c.getTelefono(),
                c.getCorreo(), c.getLicenciaNumero(), c.getLicenciaCategoria(), c.getLicenciaVencimiento(), vencida,
                c.getEstado(), c.getFoto(), c.getFechaNacimiento(), edad,
                v == null ? null : v.getId(),
                v == null ? null : v.getPlaca(),
                v == null ? null : v.getMarca(),
                v == null ? null : v.getModelo(),
                v == null ? null : v.getTipo(),
                v == null ? null : v.getAnio(),
                v == null ? null : v.getColor(),
                v == null ? null : v.getEstado(),
                v == null ? null : v.getCapacidad(),
                v == null ? null : v.getFoto());
    }
}
