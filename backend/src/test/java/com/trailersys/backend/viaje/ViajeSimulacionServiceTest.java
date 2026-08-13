package com.trailersys.backend.viaje;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.cliente.EstadoCliente;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.ConductorRepository;
import com.trailersys.backend.conductor.EstadoConductor;
import com.trailersys.backend.seguimiento.SeguimientoEventoRepository;
import com.trailersys.backend.seguimiento.TipoEvento;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.vehiculo.VehiculoRepository;

@SpringBootTest
class ViajeSimulacionServiceTest {

    @Autowired
    private ViajeSimulacionService simulacionService;

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @Autowired
    private ConductorRepository conductorRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private SeguimientoEventoRepository seguimientoEventoRepository;

    @Autowired
    private ViajeService viajeService;

    private Viaje crearViajeProgramado(String sufijo, LocalDateTime fechaSalida) {
        Vehiculo vehiculo = vehiculoRepository.save(new Vehiculo(
                "SIM-" + sufijo, "Marca", "Modelo", "Camión", 2022, "Blanco",
                EstadoVehiculo.DISPONIBLE, 1000, 5000, null, null));
        Conductor conductor = conductorRepository.save(new Conductor(
                "Conductor Sim " + sufijo, "CI-SIM-" + sufijo, "0999999999", null,
                "LIC-1", "Tipo E", LocalDate.of(2030, 1, 1), EstadoConductor.DISPONIBLE, null, null, null));
        Cliente cliente = clienteRepository.save(new Cliente(
                "Cliente Sim " + sufijo, "CLI-SIM-" + sufijo, EstadoCliente.ACTIVO,
                "0999999999", null, "Direccion", null, null));

        Viaje viaje = new Viaje(vehiculo, conductor, cliente, null, "Origen Sim", "Destino Sim",
                fechaSalida, EstadoViaje.PROGRAMADO, "");
        return viajeRepository.save(viaje);
    }

    @Test
    void iniciarViajesProgramadosVencidosPasaAEnCursoYRegistraSalida() {
        Viaje viaje = crearViajeProgramado("PROG1", LocalDateTime.now().minusMinutes(5));

        viajeService.iniciarViajesProgramadosVencidos();

        Viaje actualizado = viajeRepository.findById(viaje.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoViaje.EN_CURSO);

        long salidas = seguimientoEventoRepository.findByViajeIdOrderByFechaHoraDesc(viaje.getId()).stream()
                .filter(e -> e.getEvento() == TipoEvento.SALIDA)
                .count();
        assertThat(salidas).isEqualTo(1);
    }

    @Test
    void iniciarViajesProgramadosVencidosNoTocaViajesConFechaFutura() {
        Viaje viaje = crearViajeProgramado("PROG2", LocalDateTime.now().plusHours(2));

        viajeService.iniciarViajesProgramadosVencidos();

        Viaje actualizado = viajeRepository.findById(viaje.getId()).orElseThrow();
        assertThat(actualizado.getEstado()).isEqualTo(EstadoViaje.PROGRAMADO);
    }

    @Test
    void iniciarViajesProgramadosVencidosNoArrancaSiElVehiculoOConductorYaEstaEnOtroViajeActivo() {
        // Reproduce el caso real encontrado: dos viajes Programados vencidos
        // comparten vehiculo/conductor. El primero arranca normal; el
        // segundo debe quedarse esperando en vez de duplicar la asignacion.
        Viaje viajeActivo = crearViajeProgramado("CONFLICTO1", LocalDateTime.now().minusMinutes(10));
        viajeService.iniciarViajesProgramadosVencidos();
        Viaje viajeActivoActualizado = viajeRepository.findById(viajeActivo.getId()).orElseThrow();
        assertThat(viajeActivoActualizado.getEstado()).isEqualTo(EstadoViaje.EN_CURSO);

        Cliente otroCliente = clienteRepository.save(new Cliente(
                "Cliente Sim CONFLICTO2", "CLI-SIM-CONFLICTO2", EstadoCliente.ACTIVO,
                "0999999999", null, "Direccion", null, null));
        Viaje viajeConflicto = viajeRepository.save(new Viaje(
                viajeActivoActualizado.getVehiculo(), viajeActivoActualizado.getConductor(), otroCliente, null,
                "Otro origen", "Otro destino", LocalDateTime.now().minusMinutes(5), EstadoViaje.PROGRAMADO, ""));

        viajeService.iniciarViajesProgramadosVencidos();

        Viaje viajeConflictoActualizado = viajeRepository.findById(viajeConflicto.getId()).orElseThrow();
        assertThat(viajeConflictoActualizado.getEstado()).isEqualTo(EstadoViaje.PROGRAMADO);
    }

    private Viaje crearViajeEnCursoConRuta(String sufijo, int minutosDesdeSalida, double duracionMin) {
        Vehiculo vehiculo = vehiculoRepository.save(new Vehiculo(
                "SIM-" + sufijo, "Marca", "Modelo", "Camión", 2022, "Blanco",
                EstadoVehiculo.EN_RUTA, 1000, 5000, null, null));
        Conductor conductor = conductorRepository.save(new Conductor(
                "Conductor Sim " + sufijo, "CI-SIM-" + sufijo, "0999999999", null,
                "LIC-1", "Tipo E", LocalDate.of(2030, 1, 1), EstadoConductor.EN_RUTA, null, null, null));
        Cliente cliente = clienteRepository.save(new Cliente(
                "Cliente Sim " + sufijo, "CLI-SIM-" + sufijo, EstadoCliente.ACTIVO,
                "0999999999", null, "Direccion", null, null));

        Viaje viaje = new Viaje(vehiculo, conductor, cliente, null, "Origen Sim", "Destino Sim",
                LocalDateTime.now().minusMinutes(minutosDesdeSalida), EstadoViaje.EN_CURSO, "");
        viaje.setRutaOrigenLat(-0.22);
        viaje.setRutaOrigenLng(-78.51);
        viaje.setRutaDestinoLat(-2.18);
        viaje.setRutaDestinoLng(-79.88);
        viaje.setRutaDistanciaKm(400.0);
        viaje.setRutaDuracionMin(duracionMin);
        return viajeRepository.save(viaje);
    }

    @Test
    void ejecutarSimulacionRegistraParadaAlSuperarElPrimerPuntoDeControl() {
        // 40% de una ruta de 100 min ya transcurrido -> supera el primer punto (35%)
        Viaje viaje = crearViajeEnCursoConRuta("PARADA1", 40, 100.0);

        simulacionService.ejecutarSimulacion();

        Viaje actualizado = viajeRepository.findById(viaje.getId()).orElseThrow();
        assertThat(actualizado.getParadasSimuladasRegistradas()).isEqualTo(1);

        long paradas = seguimientoEventoRepository.findByViajeIdOrderByFechaHoraDesc(viaje.getId()).stream()
                .filter(e -> e.getEvento() == TipoEvento.PARADA)
                .count();
        assertThat(paradas).isEqualTo(1);
    }

    @Test
    void ejecutarSimulacionNoDuplicaLaMismaParadaEnDosCorridas() {
        Viaje viaje = crearViajeEnCursoConRuta("PARADA2", 40, 100.0);

        simulacionService.ejecutarSimulacion();
        simulacionService.ejecutarSimulacion();

        long paradas = seguimientoEventoRepository.findByViajeIdOrderByFechaHoraDesc(viaje.getId()).stream()
                .filter(e -> e.getEvento() == TipoEvento.PARADA)
                .count();
        assertThat(paradas).isEqualTo(1);
    }

    @Test
    void simularPeriodicamenteNoDuplicaAlLlamarseComoLoHaceElScheduler() {
        // simularPeriodicamente() es el metodo que invoca @Scheduled: a
        // diferencia de llamar ejecutarSimulacion() directo desde el test
        // (que pasa por el proxy de Spring), esto reproduce la
        // auto-invocacion real que en su momento no persistia el contador.
        Viaje viaje = crearViajeEnCursoConRuta("PARADA4", 40, 100.0);

        simulacionService.simularPeriodicamente();
        simulacionService.simularPeriodicamente();

        Viaje actualizado = viajeRepository.findById(viaje.getId()).orElseThrow();
        assertThat(actualizado.getParadasSimuladasRegistradas()).isEqualTo(1);

        long paradas = seguimientoEventoRepository.findByViajeIdOrderByFechaHoraDesc(viaje.getId()).stream()
                .filter(e -> e.getEvento() == TipoEvento.PARADA)
                .count();
        assertThat(paradas).isEqualTo(1);
    }

    @Test
    void ejecutarSimulacionNoRegistraNadaAntesDelPrimerPuntoDeControl() {
        // 10% de una ruta de 100 min -> no llega al primer punto (35%)
        Viaje viaje = crearViajeEnCursoConRuta("PARADA3", 10, 100.0);

        simulacionService.ejecutarSimulacion();

        Viaje actualizado = viajeRepository.findById(viaje.getId()).orElseThrow();
        assertThat(actualizado.getParadasSimuladasRegistradas()).isEqualTo(0);
    }
}
