package com.trailersys.backend.mantenimiento;
import java.time.LocalDateTime;
public record EvidenciaMantenimientoResponse(Long id,String categoria,String nombre,String tipoContenido,long tamano,LocalDateTime fechaCarga){
    static EvidenciaMantenimientoResponse from(EvidenciaMantenimiento e){return new EvidenciaMantenimientoResponse(e.getId(),e.getCategoria(),e.getNombre(),e.getTipoContenido(),e.getTamano(),e.getFechaCarga());}
}
