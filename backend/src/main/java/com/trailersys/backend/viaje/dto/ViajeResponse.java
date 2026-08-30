package com.trailersys.backend.viaje.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.RutaPathCodec;
import com.trailersys.backend.viaje.Viaje;

public record ViajeResponse(
        Long id,
        Long vehiculoId,
        String vehiculoPlaca,
        // Datos del vehiculo/conductor/carga denormalizados: la guia del
        // viaje (Ver e imprimir guia, en Viajes y en el modulo Guías) los
        // necesita, pero /api/vehiculos/{id}, /api/conductores/{id} y
        // /api/cargas/{id} solo permiten Administrador/Coordinador (y
        // Vehiculos ademas Mantenimiento/Supervisor). Conductor y Supervisor
        // SI pueden ver este recurso (/api/paginas/viajes), asi que antes su
        // guia salia casi vacia (esos 3 fetches devolvian 403 en silencio).
        // Trayendo los datos aqui mismo se evita esa dependencia por completo.
        String vehiculoMarca,
        String vehiculoModelo,
        String vehiculoTipo,
        Integer vehiculoAnio,
        String vehiculoColor,
        Integer vehiculoCapacidad,
        Long conductorId,
        String conductorNombres,
        String conductorIdentificacion,
        String conductorTelefono,
        String conductorLicenciaNumero,
        String conductorLicenciaCategoria,
        LocalDate conductorLicenciaVencimiento,
        Long clienteId,
        String clienteNombre,
        Long cargaId,
        String cargaDescripcion,
        String cargaTipo,
        Integer cargaPeso,
        String origen,
        String destino,
        LocalDateTime fechaSalida,
        EstadoViaje estado,
        String observaciones,
        RutaDto ruta,
        boolean entregaConfirmada,
        LocalDateTime fechaEntregaConfirmada,
        String observacionEntrega,
        String confirmadoPor,
        boolean entregaValidada,
        LocalDateTime fechaValidacionEntrega,
        String observacionValidacion,
        String validadoPor,
        boolean entregaConfirmadaCliente,
        LocalDateTime fechaConfirmacionCliente,
        String observacionConfirmacionCliente,
        String confirmadoPorCliente,
        String novedadRecepcionCliente,
        String evidenciaRecepcionCliente,
        String estadoReclamoCliente,
        LocalDateTime fechaResolucionReclamoCliente,
        String respuestaReclamoCliente
) {
    public static ViajeResponse from(Viaje v) {
        RutaDto ruta = null;
        if (v.getRutaDistanciaKm() != null) {
            ruta = new RutaDto(
                    v.getRutaOrigenLat(), v.getRutaOrigenLng(), v.getRutaDestinoLat(), v.getRutaDestinoLng(),
                    v.getRutaDistanciaKm(), v.getRutaDuracionMin(), RutaPathCodec.deserializar(v.getRutaPath()));
        }

        return new ViajeResponse(
                v.getId(),
                v.getVehiculo().getId(), v.getVehiculo().getPlaca(),
                v.getVehiculo().getMarca(), v.getVehiculo().getModelo(), v.getVehiculo().getTipo(),
                v.getVehiculo().getAnio(), v.getVehiculo().getColor(), v.getVehiculo().getCapacidad(),
                v.getConductor().getId(), v.getConductor().getNombres(),
                v.getConductor().getIdentificacion(), v.getConductor().getTelefono(),
                v.getConductor().getLicenciaNumero(), v.getConductor().getLicenciaCategoria(),
                v.getConductor().getLicenciaVencimiento(),
                v.getCliente().getId(), v.getCliente().getNombre(),
                v.getCarga() != null ? v.getCarga().getId() : null,
                v.getCarga() != null ? v.getCarga().getDescripcion() : null,
                v.getCarga() != null ? v.getCarga().getTipo() : null,
                v.getCarga() != null ? v.getCarga().getPeso() : null,
                v.getOrigen(), v.getDestino(), v.getFechaSalida(), v.getEstado(), v.getObservaciones(), ruta,
                v.isEntregaConfirmada(), v.getFechaEntregaConfirmada(), v.getObservacionEntrega(), v.getConfirmadoPor(),
                v.isEntregaValidada(), v.getFechaValidacionEntrega(), v.getObservacionValidacion(), v.getValidadoPor(),
                v.isEntregaConfirmadaCliente(), v.getFechaConfirmacionCliente(),
                v.getObservacionConfirmacionCliente(), v.getConfirmadoPorCliente(),
                v.getNovedadRecepcionCliente(), v.getEvidenciaRecepcionCliente(),
                v.getEstadoReclamoCliente(), v.getFechaResolucionReclamoCliente(),
                v.getRespuestaReclamoCliente());
    }
}
