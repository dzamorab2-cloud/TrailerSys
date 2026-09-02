package com.trailersys.backend.viaje;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.trailersys.backend.seguimiento.SeguimientoEvento;
import com.trailersys.backend.seguimiento.SeguimientoEventoRepository;
import com.trailersys.backend.seguimiento.TipoEvento;

/**
 * Simula el avance de los viajes "En Curso" a partir de fechaSalida y la
 * duracion calculada de la ruta (sin telemetria real ni llamadas a
 * servicios externos, igual que el resto del modulo): cuando el progreso
 * del viaje cruza un punto de control, registra automaticamente una Parada
 * en Seguimiento para que el conductor no tenga que hacerlo a mano.
 */
@Service
public class ViajeSimulacionService {

    private static final double[] PUNTOS_CONTROL = {0.35, 0.70};

    private final ViajeRepository viajeRepository;
    private final SeguimientoEventoRepository seguimientoEventoRepository;
    private final ViajeService viajeService;

    public ViajeSimulacionService(ViajeRepository viajeRepository,
                                   SeguimientoEventoRepository seguimientoEventoRepository,
                                   ViajeService viajeService) {
        this.viajeRepository = viajeRepository;
        this.seguimientoEventoRepository = seguimientoEventoRepository;
        this.viajeService = viajeService;
    }

    @Scheduled(fixedRate = 60000)
    public void simularPeriodicamente() {
        // Llamada a otro bean (ViajeService), no auto-invocacion: su propio
        // @Transactional aplica normalmente aca, sin el problema de
        // ejecutarSimulacion() de mas abajo.
        viajeService.iniciarViajesProgramadosVencidos();
        ejecutarSimulacion();
    }

    /**
     * Publico (no privado) para que los tests puedan disparar un ciclo de
     * simulacion sin esperar al scheduler real.
     */
    @Transactional
    public void ejecutarSimulacion() {
        // Ver ViajeService.omitirAuditoriaEnEstaTransaccion(): sin esto,
        // cada tick del scheduler quedaba auditado como si fuera una accion
        // de un usuario real.
        viajeService.omitirAuditoriaEnEstaTransaccion();
        LocalDateTime ahora = LocalDateTime.now();
        List<Viaje> enCurso = viajeRepository
                .findTop500ByEstadoOrderByFechaSalidaAsc(EstadoViaje.EN_CURSO).stream()
                .filter(v -> v.getRutaDistanciaKm() != null && v.getRutaDuracionMin() != null
                        && v.getRutaDuracionMin() > 0 && v.getFechaSalida() != null)
                .toList();

        for (Viaje viaje : enCurso) {
            procesarViaje(viaje, ahora);
        }
    }

    private void procesarViaje(Viaje viaje, LocalDateTime ahora) {
        double minutosTranscurridos = Duration.between(viaje.getFechaSalida(), ahora).toSeconds() / 60.0;
        double progreso = Math.min(1.0, Math.max(0.0, minutosTranscurridos / viaje.getRutaDuracionMin()));

        // Llegada a destino (100% del progreso): se revisa antes que las
        // paradas intermedias y de forma independiente de su contador, para
        // que un viaje muy corto (o revisado en un ciclo donde ya supero
        // ambos puntos de control a la vez) no se quede sin su evento de
        // Llegada. No finaliza el viaje - solo registra que llego, ver
        // ViajeService.registrarLlegada(). Coordinador/Administrador lo
        // cierran explicitamente despues, una vez el cliente revisa la carga.
        if (progreso >= 1.0 && !viaje.isEntregaConfirmada()) {
            viajeService.registrarLlegada(viaje, "Llegada registrada automáticamente por el sistema.", "Sistema");
            return;
        }

        int siguientePunto = viaje.getParadasSimuladasRegistradas();
        if (siguientePunto >= PUNTOS_CONTROL.length || progreso < PUNTOS_CONTROL[siguientePunto]) {
            return;
        }

        double fraccion = PUNTOS_CONTROL[siguientePunto];
        String ubicacion = String.format(Locale.ROOT, "Punto intermedio del trayecto (~%.0f%% del recorrido, ~%.0f km)",
                fraccion * 100, viaje.getRutaDistanciaKm() * fraccion);

        seguimientoEventoRepository.save(new SeguimientoEvento(
                viaje, viaje.getVehiculo(), ahora, TipoEvento.PARADA, ubicacion,
                "Parada registrada automáticamente por el sistema de seguimiento."));

        // Guardado explicito (no solo dirty-checking): simularPeriodicamente()
        // llama a ejecutarSimulacion() dentro de la misma clase, y esa
        // auto-invocacion no pasa por el proxy de Spring, asi que el
        // @Transactional del metodo de arriba no aplica aqui. Sin este
        // save() el contador nunca se persiste y el scheduler repite la
        // misma parada en cada ciclo.
        viaje.setParadasSimuladasRegistradas(siguientePunto + 1);
        viajeRepository.save(viaje);
    }
}
