package com.trailersys.backend.mantenimiento;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.trailersys.backend.common.ResourceNotFoundException;

@RestController
@RequestMapping("/api/mantenimientos/{mantenimientoId}/evidencias")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','MANTENIMIENTO')")
public class EvidenciaMantenimientoController {
    private static final Set<String> CATEGORIAS=Set.of("FACTURA","INFORME_TECNICO","FOTO_ANTES","FOTO_DESPUES");
    private static final Set<String> TIPOS=Set.of("application/pdf","image/jpeg","image/png","image/webp");
    private final EvidenciaMantenimientoRepository repository; private final MantenimientoService service;
    public EvidenciaMantenimientoController(EvidenciaMantenimientoRepository r,MantenimientoService s){repository=r;service=s;}
    @GetMapping public List<EvidenciaMantenimientoResponse> listar(@PathVariable Long mantenimientoId){service.obtener(mantenimientoId);return repository.findByMantenimientoIdOrderByFechaCargaDesc(mantenimientoId).stream().map(EvidenciaMantenimientoResponse::from).toList();}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenciaMantenimientoResponse> subir(@PathVariable Long mantenimientoId,@RequestParam String categoria,@RequestPart MultipartFile archivo) throws IOException {
        if(!CATEGORIAS.contains(categoria)) throw new IllegalArgumentException("Categoría de evidencia inválida.");
        if(archivo.isEmpty()||archivo.getSize()>10*1024*1024) throw new IllegalArgumentException("El archivo debe pesar entre 1 byte y 10 MB.");
        String tipo=archivo.getContentType()==null?"":archivo.getContentType().toLowerCase();
        if(!TIPOS.contains(tipo)) throw new IllegalArgumentException("Solo se permiten PDF, JPG, PNG o WEBP.");
        String nombre=archivo.getOriginalFilename()==null?"evidencia":archivo.getOriginalFilename().replaceAll("[\\r\\n\\\\/]","_");
        var e=repository.save(new EvidenciaMantenimiento(service.obtener(mantenimientoId),categoria,nombre,tipo,archivo.getBytes()));
        return ResponseEntity.status(HttpStatus.CREATED).body(EvidenciaMantenimientoResponse.from(e));
    }
    @GetMapping("/{id}/archivo") public ResponseEntity<byte[]> descargar(@PathVariable Long mantenimientoId,@PathVariable Long id){
        var e=repository.findById(id).filter(x->x.getMantenimiento().getId().equals(mantenimientoId)).orElseThrow(()->new ResourceNotFoundException("Evidencia no encontrada: "+id));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(e.getTipoContenido())).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+e.getNombre()+"\"").body(e.getContenido());
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> eliminar(@PathVariable Long mantenimientoId,@PathVariable Long id){
        var e=repository.findById(id).filter(x->x.getMantenimiento().getId().equals(mantenimientoId)).orElseThrow(()->new ResourceNotFoundException("Evidencia no encontrada: "+id));repository.delete(e);return ResponseEntity.noContent().build();
    }
}
