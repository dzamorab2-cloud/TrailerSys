package com.trailersys.backend.facturacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.facturacion.dto.AnularFacturaRequest;
import com.trailersys.backend.facturacion.dto.CarteraResumenResponse;
import com.trailersys.backend.facturacion.dto.CrearFacturaRequest;
import com.trailersys.backend.facturacion.dto.FacturaResponse;
import com.trailersys.backend.facturacion.dto.ItemFacturaRequest;
import com.trailersys.backend.facturacion.dto.RegistrarPagoRequest;
import com.trailersys.backend.facturacion.dto.ViajeFacturableResponse;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.ViajeRepository;

/**
 * Facturacion interna y cuentas por cobrar. Reglas:
 * - Solo se facturan viajes FINALIZADOS del mismo cliente, y un viaje no
 *   puede estar en dos facturas vigentes (una anulada lo libera).
 * - Los pagos no pueden superar el saldo; al llegar a 0 la factura queda PAGADA.
 * - Una factura con pagos no se anula (habria que revertir el cobro primero).
 */
@Service
public class FacturacionService {

    private static final int LIMITE_LISTADO = 500;
    private static final int LIMITE_FACTURABLES = 200;
    private static final Set<EstadoFactura> ABIERTAS = EnumSet.of(EstadoFactura.PENDIENTE, EstadoFactura.PARCIAL);
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FacturaRepository facturaRepository;
    private final DetalleFacturaRepository detalleRepository;
    private final PagoRepository pagoRepository;
    private final ClienteRepository clienteRepository;
    private final ViajeRepository viajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final BigDecimal ivaPorDefecto;
    private final int diasCreditoPorDefecto;

    public FacturacionService(FacturaRepository facturaRepository, DetalleFacturaRepository detalleRepository,
                              PagoRepository pagoRepository, ClienteRepository clienteRepository,
                              ViajeRepository viajeRepository, UsuarioRepository usuarioRepository,
                              @Value("${trailersys.facturacion.iva-porcentaje:15}") BigDecimal ivaPorDefecto,
                              @Value("${trailersys.facturacion.dias-credito:30}") int diasCreditoPorDefecto) {
        this.facturaRepository = facturaRepository;
        this.detalleRepository = detalleRepository;
        this.pagoRepository = pagoRepository;
        this.clienteRepository = clienteRepository;
        this.viajeRepository = viajeRepository;
        this.usuarioRepository = usuarioRepository;
        this.ivaPorDefecto = ivaPorDefecto;
        this.diasCreditoPorDefecto = diasCreditoPorDefecto;
    }

    public BigDecimal getIvaPorDefecto() {
        return ivaPorDefecto;
    }

    public int getDiasCreditoPorDefecto() {
        return diasCreditoPorDefecto;
    }

    @Transactional(readOnly = true)
    public List<FacturaResponse> listar(Long clienteId, String estado, String search) {
        LocalDate hoy = LocalDate.now();
        Set<EstadoFactura> estados = EnumSet.allOf(EstadoFactura.class);
        boolean soloVencidas = false;
        boolean excluirVencidas = false;
        if (estado != null && !estado.isBlank()) {
            EstadoFactura filtro = EstadoFactura.desdeEtiqueta(estado.trim());
            switch (filtro) {
                case VENCIDA -> {
                    estados = ABIERTAS;
                    soloVencidas = true;
                }
                case PENDIENTE, PARCIAL -> {
                    estados = EnumSet.of(filtro);
                    excluirVencidas = true;
                }
                default -> estados = EnumSet.of(filtro);
            }
        }
        return facturaRepository.buscar(clienteId == null ? 0L : clienteId, estados, soloVencidas, excluirVencidas,
                        hoy, search == null ? "" : search.trim(), PageRequest.of(0, LIMITE_LISTADO))
                .stream().map(f -> FacturaResponse.from(f, hoy)).toList();
    }

    @Transactional(readOnly = true)
    public FacturaResponse obtener(Long id) {
        return FacturaResponse.from(buscar(id), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<ViajeFacturableResponse> viajesFacturables(Long clienteId) {
        return detalleRepository.viajesFacturables(clienteId, PageRequest.of(0, LIMITE_FACTURABLES))
                .stream().map(ViajeFacturableResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CarteraResumenResponse resumen() {
        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = hoy.withDayOfMonth(1);
        return new CarteraResumenResponse(
                facturaRepository.sumarSaldo(ABIERTAS),
                facturaRepository.sumarSaldoVencido(ABIERTAS, hoy),
                facturaRepository.countByEstadoIn(ABIERTAS),
                facturaRepository.contarVencidas(ABIERTAS, hoy),
                facturaRepository.sumarFacturadoDesde(inicioMes),
                pagoRepository.sumarCobradoDesde(inicioMes));
    }

    @Transactional
    public FacturaResponse crear(CrearFacturaRequest request, String usuario) {
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado: " + request.clienteId()));
        BigDecimal ivaPorcentaje = request.ivaPorcentaje() == null ? ivaPorDefecto : request.ivaPorcentaje();
        int diasCredito = request.diasCredito() == null ? diasCreditoPorDefecto : request.diasCredito();
        LocalDate hoy = LocalDate.now();

        Factura factura = new Factura(cliente, hoy, hoy.plusDays(diasCredito),
                ivaPorcentaje.setScale(2, RoundingMode.HALF_UP), limpiar(request.observaciones()), usuario);

        Set<Long> vistos = new HashSet<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemFacturaRequest item : request.items()) {
            if (!vistos.add(item.viajeId())) {
                throw new ConflictException("El viaje #" + item.viajeId() + " está repetido en la factura.");
            }
            Viaje viaje = viajeRepository.findById(item.viajeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado: " + item.viajeId()));
            if (!viaje.getCliente().getId().equals(cliente.getId())) {
                throw new ConflictException("El viaje #" + viaje.getId() + " no pertenece a " + cliente.getNombre() + ".");
            }
            if (viaje.getEstado() != EstadoViaje.FINALIZADO) {
                throw new ConflictException("Solo se pueden facturar viajes finalizados (el viaje #"
                        + viaje.getId() + " está " + viaje.getEstado().getEtiqueta() + ").");
            }
            if (detalleRepository.existsByViaje_IdAndFactura_EstadoNot(viaje.getId(), EstadoFactura.ANULADA)) {
                throw new ConflictException("El viaje #" + viaje.getId() + " ya está facturado.");
            }
            BigDecimal monto = item.monto().setScale(2, RoundingMode.HALF_UP);
            String descripcion = limpiar(item.descripcion());
            if (descripcion == null) {
                descripcion = descripcionPorDefecto(viaje);
            }
            factura.agregarDetalle(new DetalleFactura(factura, viaje, descripcion, monto));
            subtotal = subtotal.add(monto);
        }

        BigDecimal iva = subtotal.multiply(factura.getIvaPorcentaje())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(iva);
        factura.setSubtotal(subtotal);
        factura.setIva(iva);
        factura.setTotal(total);
        factura.setSaldo(total);

        factura = facturaRepository.save(factura);
        factura.setNumero(String.format("FAC-%06d", factura.getId()));
        return FacturaResponse.from(factura, hoy);
    }

    @Transactional
    public FacturaResponse registrarPago(Long id, RegistrarPagoRequest request, String usuario) {
        Factura factura = buscar(id);
        if (factura.getEstado() == EstadoFactura.ANULADA) {
            throw new ConflictException("No se pueden registrar pagos en una factura anulada.");
        }
        if (factura.getEstado() == EstadoFactura.PAGADA) {
            throw new ConflictException("La factura ya está pagada.");
        }
        LocalDate hoy = LocalDate.now();
        LocalDate fecha = request.fecha() == null ? hoy : request.fecha();
        if (fecha.isAfter(hoy)) {
            throw new ConflictException("La fecha del pago no puede ser futura.");
        }
        if (fecha.isBefore(factura.getFechaEmision())) {
            throw new ConflictException("La fecha del pago no puede ser anterior a la emisión de la factura.");
        }
        BigDecimal monto = request.monto().setScale(2, RoundingMode.HALF_UP);
        if (monto.compareTo(factura.getSaldo()) > 0) {
            throw new ConflictException("El pago ($" + monto + ") supera el saldo pendiente ($" + factura.getSaldo() + ").");
        }
        factura.agregarPago(new Pago(factura, fecha, monto, request.metodo(), limpiar(request.referencia()), usuario));
        factura.setSaldo(factura.getSaldo().subtract(monto));
        factura.setEstado(factura.getSaldo().signum() == 0 ? EstadoFactura.PAGADA : EstadoFactura.PARCIAL);
        return FacturaResponse.from(facturaRepository.save(factura), hoy);
    }

    @Transactional
    public FacturaResponse anular(Long id, AnularFacturaRequest request) {
        Factura factura = buscar(id);
        if (factura.getEstado() == EstadoFactura.ANULADA) {
            throw new ConflictException("La factura ya está anulada.");
        }
        if (!factura.getPagos().isEmpty()) {
            throw new ConflictException("No se puede anular una factura que ya tiene pagos registrados.");
        }
        factura.setEstado(EstadoFactura.ANULADA);
        factura.setMotivoAnulacion(request.motivo().trim());
        factura.setFechaAnulacion(LocalDateTime.now());
        return FacturaResponse.from(facturaRepository.save(factura), LocalDate.now());
    }

    /** Autoservicio: facturas del cliente vinculado a la cuenta (sin anuladas). */
    @Transactional(readOnly = true)
    public List<FacturaResponse> misFacturas(String username) {
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(username)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));
        Cliente cliente = usuario.getCliente();
        if (cliente == null) {
            throw new ConflictException("Este usuario no tiene un cliente asociado.");
        }
        LocalDate hoy = LocalDate.now();
        return facturaRepository.findByCliente_IdOrderByFechaEmisionDescIdDesc(cliente.getId()).stream()
                .filter(f -> f.getEstado() != EstadoFactura.ANULADA)
                .map(f -> FacturaResponse.from(f, hoy)).toList();
    }

    private Factura buscar(Long id) {
        return facturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura no encontrada: " + id));
    }

    private static String descripcionPorDefecto(Viaje viaje) {
        String fecha = viaje.getFechaSalida() == null ? "" : " (" + viaje.getFechaSalida().format(FORMATO_FECHA) + ")";
        return "Transporte " + viaje.getOrigen() + " → " + viaje.getDestino() + fecha + " · Viaje #" + viaje.getId();
    }

    private static String limpiar(String texto) {
        if (texto == null) return null;
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
