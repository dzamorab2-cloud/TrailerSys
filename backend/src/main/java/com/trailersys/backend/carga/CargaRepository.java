package com.trailersys.backend.carga;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CargaRepository extends JpaRepository<Carga, Long> {

    @Query("""
            select c from Carga c
            where (:search = '' or lower(c.descripcion) like lower(concat('%', :search, '%'))
                   or lower(c.origen) like lower(concat('%', :search, '%'))
                   or lower(c.destino) like lower(concat('%', :search, '%'))
                   or lower(c.cliente.nombre) like lower(concat('%', :search, '%')))
              and (:estado is null or c.estado = :estado)
            """)
    Page<Carga> buscar(@Param("search") String search,
                       @Param("estado") EstadoCarga estado,
                       Pageable pageable);

    /** Autoservicio del rol CLIENTE: listado de sus propios pedidos. */
    List<Carga> findByCliente_IdOrderByIdDesc(Long clienteId);

    /**
     * Busqueda por id acotada al cliente dueno, en una sola consulta: evita
     * el patron "buscar por id y luego comparar el cliente en Java", que es
     * mas facil de olvidar en un endpoint nuevo y dejaria una fuga entre
     * clientes. Vacio si el id no existe O si existe pero es de otro
     * cliente (misma respuesta en ambos casos, para no revelar cual de las
     * dos paso).
     */
    Optional<Carga> findByIdAndCliente_Id(Long id, Long clienteId);
}
