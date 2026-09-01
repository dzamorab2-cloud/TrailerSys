package com.trailersys.backend.operaciones;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.common.ConflictException;
import com.trailersys.backend.common.ResourceNotFoundException;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.operaciones.dto.ResumenConductorResponse;
import com.trailersys.backend.operaciones.dto.ResumenConductorResponse.ViajesPorMes;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.ViajeRepository;

/**
 * Autoservicio del rol CONDUCTOR: su Dashboard personal y "Mis viajes".
 * Calcado de PedidoClienteService (autoservicio de CLIENTE) - todo se
 * acota siempre al Conductor vinculado al Usuario autenticado
 * (Usuario.conductor), nunca a un conductorId que venga del request; un id
 * de viaje ajeno da el mismo 404 que uno inexistente (ver miViaje()).
 */
@Service
public class ViajeConductorService {

    /** Mismo limite que ya usa el front (js/conductores.js) para fotos en base64. */
    private static final int LIMITE_FOTO_CARACTERES = 3_000_000;

    private final UsuarioRepository usuarioRepository;
    private final ViajeRepository viajeRepository;

    public ViajeConductorService(UsuarioRepository usuarioRepository, ViajeRepository viajeRepository) {
        this.usuarioRepository = usuarioRepository;
        this.viajeRepository = viajeRepository;
    }

    private Conductor miConductor(String username) {
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(username)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + username));
        Conductor conductor = usuario.getConductor();
        if (conductor == null) {
            throw new ConflictException("Este usuario no tiene un conductor asociado.");
        }
        return conductor;
    }

    /**
     * Busca el viaje por id exigiendo en la misma consulta que pertenezca
     * al conductor de "username". Si el id no existe, o existe pero es de
     * otro conductor, se lanza el mismo 404 en ambos casos: nunca se revela
     * a un conductor si el id de otro conductor existe o no.
     */
    private Viaje miViaje(String username, Long viajeId) {
        Long conductorId = miConductor(username).getId();
        return viajeRepository.findByIdAndConductor_Id(viajeId, conductorId)
                .orElseThrow(() -> new ResourceNotFoundException("Viaje no encontrado: " + viajeId));
    }

    public Page<Viaje> listarMisViajes(String username, String search, EstadoViaje estado, Pageable pageable) {
        Long conductorId = miConductor(username).getId();
        return viajeRepository.buscarMisViajes(conductorId, search == null ? "" : search.trim(), estado, pageable);
    }

    public Viaje detalle(String username, Long viajeId) {
        return miViaje(username, viajeId);
    }

    /**
     * El viaje que debe disparar la alerta del Dashboard: el En Curso mas
     * reciente si hay uno, si no el Programado mas proximo. null si no
     * tiene ninguno de los dos (nada que alertar).
     */
    public Viaje viajeActivo(String username) {
        List<Viaje> viajes = viajeRepository.findByConductor_Id(miConductor(username).getId());
        return viajes.stream()
                .filter(v -> v.getEstado() == EstadoViaje.EN_CURSO)
                .max(Comparator.comparing(Viaje::getFechaSalida))
                .or(() -> viajes.stream()
                        .filter(v -> v.getEstado() == EstadoViaje.PROGRAMADO)
                        .min(Comparator.comparing(Viaje::getFechaSalida)))
                .orElse(null);
    }

    public Conductor perfil(String username) {
        return miConductor(username);
    }

    public ResumenConductorResponse resumen(String username) {
        List<Viaje> viajes = viajeRepository.findByConductor_Id(miConductor(username).getId());

        long programados = viajes.stream().filter(v -> v.getEstado() == EstadoViaje.PROGRAMADO).count();
        long enCurso = viajes.stream().filter(v -> v.getEstado() == EstadoViaje.EN_CURSO).count();
        long finalizados = viajes.stream().filter(v -> v.getEstado() == EstadoViaje.FINALIZADO).count();
        long cancelados = viajes.stream().filter(v -> v.getEstado() == EstadoViaje.CANCELADO).count();
        double km = viajes.stream()
                .filter(v -> v.getEstado() == EstadoViaje.FINALIZADO && v.getRutaDistanciaKm() != null)
                .mapToDouble(Viaje::getRutaDistanciaKm)
                .sum();

        return new ResumenConductorResponse(viajes.size(), programados, enCurso, finalizados, cancelados, km,
                viajesPorMes(viajes));
    }

    /** Ultimos 6 meses (incluye el actual), en orden cronologico, con 0 para los meses sin viajes. */
    private List<ViajesPorMes> viajesPorMes(List<Viaje> viajes) {
        LocalDateTime ahora = LocalDateTime.now();
        Map<String, Long> porMes = new TreeMap<>();
        for (int i = 5; i >= 0; i--) {
            porMes.put(claveMes(ahora.minusMonths(i)), 0L);
        }
        for (Viaje v : viajes) {
            if (v.getFechaSalida() == null) {
                continue;
            }
            String clave = claveMes(v.getFechaSalida());
            if (porMes.containsKey(clave)) {
                porMes.merge(clave, 1L, Long::sum);
            }
        }
        return porMes.entrySet().stream()
                .map(e -> new ViajesPorMes(etiquetaMes(e.getKey()), e.getValue()))
                .toList();
    }

    private String claveMes(LocalDateTime fecha) {
        return "%04d-%02d".formatted(fecha.getYear(), fecha.getMonthValue());
    }

    private String etiquetaMes(String claveAnioMes) {
        int mes = Integer.parseInt(claveAnioMes.substring(5, 7));
        String nombre = Month.of(mes).getDisplayName(TextStyle.SHORT, new Locale("es", "EC"));
        return nombre.substring(0, 1).toUpperCase() + nombre.substring(1);
    }

    @Transactional
    public Conductor actualizarFoto(String username, String foto) {
        if (foto != null && foto.length() > LIMITE_FOTO_CARACTERES) {
            throw new ConflictException("La foto supera el tamaño permitido.");
        }
        Conductor conductor = miConductor(username);
        conductor.setFoto(foto);
        return conductor;
    }
}
