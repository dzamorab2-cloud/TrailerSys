package com.trailersys.backend.mantenimiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EvidenciaMantenimientoRepository extends JpaRepository<EvidenciaMantenimiento,Long>{
    List<EvidenciaMantenimiento> findByMantenimientoIdOrderByFechaCargaDesc(Long mantenimientoId);
}
