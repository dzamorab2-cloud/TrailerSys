package com.trailersys.backend.facturacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacturaRepository extends JpaRepository<Factura, Long> {

    /**
     * clienteId = 0 significa "todos". soloVencidas/excluirVencidas permiten
     * filtrar por el estado VENCIDA, que no se guarda (ver EstadoFactura).
     */
    @Query("""
            select f from Factura f
            where (:clienteId = 0 or f.cliente.id = :clienteId)
              and f.estado in :estados
              and (:soloVencidas = false or f.fechaVencimiento < :hoy)
              and (:excluirVencidas = false or f.fechaVencimiento >= :hoy)
              and (:search = '' or lower(f.numero) like lower(concat('%', :search, '%'))
                   or lower(f.cliente.nombre) like lower(concat('%', :search, '%')))
            order by f.fechaEmision desc, f.id desc
            """)
    List<Factura> buscar(@Param("clienteId") Long clienteId,
                         @Param("estados") Collection<EstadoFactura> estados,
                         @Param("soloVencidas") boolean soloVencidas,
                         @Param("excluirVencidas") boolean excluirVencidas,
                         @Param("hoy") LocalDate hoy,
                         @Param("search") String search,
                         Pageable pageable);

    List<Factura> findByCliente_IdOrderByFechaEmisionDescIdDesc(Long clienteId);

    @Query("select coalesce(sum(f.saldo), 0) from Factura f where f.estado in :estados")
    BigDecimal sumarSaldo(@Param("estados") Collection<EstadoFactura> estados);

    @Query("select coalesce(sum(f.saldo), 0) from Factura f where f.estado in :estados and f.fechaVencimiento < :hoy")
    BigDecimal sumarSaldoVencido(@Param("estados") Collection<EstadoFactura> estados, @Param("hoy") LocalDate hoy);

    long countByEstadoIn(Collection<EstadoFactura> estados);

    @Query("select count(f) from Factura f where f.estado in :estados and f.fechaVencimiento < :hoy")
    long contarVencidas(@Param("estados") Collection<EstadoFactura> estados, @Param("hoy") LocalDate hoy);

    @Query("""
            select coalesce(sum(f.total), 0) from Factura f
            where f.estado <> com.trailersys.backend.facturacion.EstadoFactura.ANULADA
              and f.fechaEmision >= :desde
            """)
    BigDecimal sumarFacturadoDesde(@Param("desde") LocalDate desde);
}
