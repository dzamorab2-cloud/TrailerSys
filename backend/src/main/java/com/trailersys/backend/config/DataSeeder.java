package com.trailersys.backend.config;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.trailersys.backend.carga.Carga;
import com.trailersys.backend.carga.CargaRepository;
import com.trailersys.backend.carga.EstadoCarga;
import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.cliente.EstadoCliente;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.ConductorRepository;
import com.trailersys.backend.conductor.EstadoConductor;
import com.trailersys.backend.mantenimiento.Mantenimiento;
import com.trailersys.backend.mantenimiento.MantenimientoRepository;
import com.trailersys.backend.mantenimiento.TipoMantenimiento;
import com.trailersys.backend.seguimiento.SeguimientoEvento;
import com.trailersys.backend.seguimiento.SeguimientoEventoRepository;
import com.trailersys.backend.seguimiento.TipoEvento;
import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.vehiculo.VehiculoRepository;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.ViajeRepository;

/**
 * Crea datos minimos para poder probar la API apenas arranca, igual que
 * trailersysSeedIfEmpty() hace en el frontend con localStorage. Solo
 * siembra si las tablas estan vacias, para no duplicar datos en cada
 * reinicio.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final VehiculoRepository vehiculoRepository;
    private final ConductorRepository conductorRepository;
    private final ClienteRepository clienteRepository;
    private final CargaRepository cargaRepository;
    private final ViajeRepository viajeRepository;
    private final SeguimientoEventoRepository seguimientoEventoRepository;
    private final MantenimientoRepository mantenimientoRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository, VehiculoRepository vehiculoRepository,
                       ConductorRepository conductorRepository, ClienteRepository clienteRepository,
                       CargaRepository cargaRepository, ViajeRepository viajeRepository,
                       SeguimientoEventoRepository seguimientoEventoRepository,
                       MantenimientoRepository mantenimientoRepository,
                       PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.vehiculoRepository = vehiculoRepository;
        this.conductorRepository = conductorRepository;
        this.clienteRepository = clienteRepository;
        this.cargaRepository = cargaRepository;
        this.viajeRepository = viajeRepository;
        this.seguimientoEventoRepository = seguimientoEventoRepository;
        this.mantenimientoRepository = mantenimientoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Cada cuenta se siembra de forma independiente (no todo detras de un
        // solo count()==0) para que agregar una cuenta nueva a este metodo
        // tambien se aplique en una base de datos que ya tenia usuarios.
        sembrarUsuarioSiNoExiste("admin", "admin1234", "Administrador General",
                "admin@trailersys.test", Rol.ADMINISTRADOR);
        sembrarUsuarioSiNoExiste("coordinador", "coordinador1234", "Coordinador de Operaciones",
                "coordinador@trailersys.test", Rol.COORDINADOR);
        sembrarUsuarioSiNoExiste("mantenimiento", "mantenimiento1234", "Responsable de Mantenimiento",
                "mantenimiento@trailersys.test", Rol.MANTENIMIENTO);
        sembrarUsuarioSiNoExiste("conductor", "conductor1234", "Luis Herrera",
                "luis.herrera@trailersys.test", Rol.CONDUCTOR);
        sembrarUsuarioSiNoExiste("supervisor", "supervisor1234", "Supervisor de Operaciones",
                "supervisor@trailersys.test", Rol.SUPERVISOR);

        if (vehiculoRepository.count() == 0) {
            vehiculoRepository.save(new Vehiculo(
                    "PBA-1234", "Freightliner", "Cascadia", "Tráiler", 2021, "Blanco",
                    EstadoVehiculo.DISPONIBLE, 82000, 28000,
                    "Unidad principal para rutas de larga distancia.", null));

            vehiculoRepository.save(new Vehiculo(
                    "PCD-5678", "Hino", "300", "Camión", 2019, "Azul",
                    EstadoVehiculo.EN_RUTA, 145000, 6500, null, null));
        }

        if (conductorRepository.count() == 0) {
            Vehiculo vehiculoAsignado = vehiculoRepository.findByPlacaIgnoreCase("PBA-1234").orElse(null);

            conductorRepository.save(new Conductor(
                    "Luis Herrera", "0912345678", "0991234567", "luis.herrera@trailersys.test",
                    "LIC-88213", "Tipo E", LocalDate.of(2027, 3, 15),
                    EstadoConductor.EN_RUTA, vehiculoAsignado, "", null));

            conductorRepository.save(new Conductor(
                    "Marcia Torres", "0923456789", "0987654321", null,
                    "LIC-40071", "Tipo C", LocalDate.of(2024, 1, 10),
                    EstadoConductor.DISPONIBLE, null, "Disponible para rutas cortas.", null));
        }

        if (clienteRepository.count() == 0) {
            clienteRepository.save(new Cliente(
                    "Comercial Andina S.A.", "0992345678001", EstadoCliente.ACTIVO,
                    "042345678", "contacto@comercialandina.test", "Av. Quito 456, Guayaquil",
                    "Carga seca, Paletizada", "Cliente frecuente con envíos semanales."));

            clienteRepository.save(new Cliente(
                    "Distribuidora El Roble", "0911223344", EstadoCliente.INACTIVO,
                    "0987001122", null, "Calle Bolívar y Sucre, Ambato",
                    "Refrigerados", null));
        }

        // La cuenta de autoservicio se vincula a "Comercial Andina S.A." (ya
        // sembrado arriba). boolean capturado ANTES de sembrarUsuarioSiNoExiste
        // para poder usarlo como "es la primera vez que existe esta cuenta"
        // mas abajo, sin depender de un count() que ya se uso para otra cosa.
        boolean primeraVezClienteDemo = usuarioRepository.findByUsernameIgnoreCase("cliente").isEmpty();
        Cliente clienteDemo = clienteRepository.findByIdentificacionIgnoreCase("0992345678001").orElse(null);
        sembrarUsuarioSiNoExiste("cliente", "cliente1234", "Comercial Andina S.A.",
                "pedidos@comercialandina.test", Rol.CLIENTE, clienteDemo);

        if (cargaRepository.count() == 0) {
            Cliente comercialAndina = clienteRepository.findByIdentificacionIgnoreCase("0992345678001").orElse(null);
            Cliente distribuidoraElRoble = clienteRepository.findByIdentificacionIgnoreCase("0911223344").orElse(null);

            cargaRepository.save(new Carga(
                    "Lote de telas e insumos textiles", comercialAndina, "Textiles", 3200,
                    "Guayaquil", "Quito", EstadoCarga.PENDIENTE, null));

            cargaRepository.save(new Carga(
                    "Productos refrigerados para distribución", distribuidoraElRoble, "Refrigerados", 1800,
                    "Ambato", "Riobamba", EstadoCarga.EN_TRANSITO, "Requiere cadena de frío."));
        }

        // Respaldo para poder probar el flujo de autoservicio de punta a
        // punta: si por algun motivo el cliente demo no tiene ya un pedido
        // Pendiente (la carga "Lote de telas..." de arriba deberia serlo en
        // una base nueva), se siembra uno. Solo la primera vez que se crea
        // esta cuenta, para no ir acumulando pedidos de prueba en cada
        // reinicio despues de que el cliente demo use la app y ese pedido
        // ya haya avanzado de estado.
        if (primeraVezClienteDemo && clienteDemo != null
                && cargaRepository.findByCliente_IdOrderByIdDesc(clienteDemo.getId()).stream()
                        .noneMatch(c -> c.getEstado() == EstadoCarga.PENDIENTE)) {
            cargaRepository.save(new Carga(
                    "Pedido de prueba para el cliente demo", clienteDemo, "General", 500,
                    "Guayaquil", "Quito", EstadoCarga.PENDIENTE,
                    "Sembrado para probar el flujo de autoservicio del cliente."));
        }

        if (viajeRepository.count() == 0) {
            Vehiculo vehiculo1 = vehiculoRepository.findByPlacaIgnoreCase("PBA-1234").orElse(null);
            Vehiculo vehiculo2 = vehiculoRepository.findByPlacaIgnoreCase("PCD-5678").orElse(null);
            Conductor conductor1 = conductorRepository.findByIdentificacionIgnoreCase("0912345678").orElse(null);
            Conductor conductor2 = conductorRepository.findByIdentificacionIgnoreCase("0923456789").orElse(null);
            Cliente comercialAndina = clienteRepository.findByIdentificacionIgnoreCase("0992345678001").orElse(null);
            Cliente distribuidoraElRoble = clienteRepository.findByIdentificacionIgnoreCase("0911223344").orElse(null);
            Carga cargaTextiles = cargaRepository.findAll().stream()
                    .filter(c -> "Lote de telas e insumos textiles".equals(c.getDescripcion()))
                    .findFirst().orElse(null);
            Carga cargaRefrigerados = cargaRepository.findAll().stream()
                    .filter(c -> "Productos refrigerados para distribución".equals(c.getDescripcion()))
                    .findFirst().orElse(null);

            Viaje viaje1 = new Viaje(vehiculo1, conductor1, comercialAndina, cargaTextiles,
                    "Guayaquil, Ecuador", "Quito, Ecuador", LocalDateTime.of(2026, 8, 10, 7, 0),
                    EstadoViaje.PROGRAMADO, "");
            viaje1.setRutaOrigenLat(-2.1894);
            viaje1.setRutaOrigenLng(-79.8891);
            viaje1.setRutaDestinoLat(-0.2201641);
            viaje1.setRutaDestinoLng(-78.5123274);
            viaje1.setRutaDistanciaKm(424.5);
            viaje1.setRutaDuracionMin(372.6);
            viajeRepository.save(viaje1);

            viajeRepository.save(new Viaje(vehiculo2, conductor2, distribuidoraElRoble, cargaRefrigerados,
                    "Ambato, Ecuador", "Riobamba, Ecuador", LocalDateTime.of(2026, 8, 9, 6, 0),
                    EstadoViaje.EN_CURSO, ""));
        }

        if (seguimientoEventoRepository.count() == 0) {
            Viaje viajeEnCurso = viajeRepository.findAll().stream()
                    .filter(v -> "Ambato, Ecuador".equals(v.getOrigen()) && "Riobamba, Ecuador".equals(v.getDestino()))
                    .findFirst().orElse(null);

            if (viajeEnCurso != null) {
                seguimientoEventoRepository.save(new SeguimientoEvento(
                        viajeEnCurso, viajeEnCurso.getVehiculo(), LocalDateTime.of(2026, 8, 9, 6, 5),
                        TipoEvento.SALIDA, "Terminal de Ambato", "Salida registrada a tiempo."));

                seguimientoEventoRepository.save(new SeguimientoEvento(
                        viajeEnCurso, viajeEnCurso.getVehiculo(), LocalDateTime.of(2026, 8, 9, 6, 40),
                        TipoEvento.PARADA, "Km 15 vía Ambato - Riobamba", "Parada breve por control de carga."));
            }
        }

        if (mantenimientoRepository.count() == 0) {
            Vehiculo vehiculo1 = vehiculoRepository.findByPlacaIgnoreCase("PBA-1234").orElse(null);
            Vehiculo vehiculo2 = vehiculoRepository.findByPlacaIgnoreCase("PCD-5678").orElse(null);

            mantenimientoRepository.save(new Mantenimiento(
                    vehiculo2, TipoMantenimiento.CORRECTIVO, LocalDate.of(2026, 7, 20), 145500, 340.5,
                    LocalDate.of(2026, 8, 5), "Revisión y cambio de pastillas de freno."));

            mantenimientoRepository.save(new Mantenimiento(
                    vehiculo1, TipoMantenimiento.PREVENTIVO, LocalDate.of(2026, 6, 10), 78000, 120.0,
                    LocalDate.of(2026, 9, 10), "Cambio de aceite y filtros."));
        }
    }

    /**
     * Upsert de una cuenta demo: si ya existe (de una siembra anterior con
     * otra contrasena) se actualiza para que las credenciales documentadas
     * en el README siempre funcionen, en vez de quedar "atascadas" con lo
     * que se haya sembrado la primera vez.
     */
    private void sembrarUsuarioSiNoExiste(String username, String password, String nombre, String correo, Rol rol) {
        sembrarUsuarioSiNoExiste(username, password, nombre, correo, rol, null);
    }

    /** Igual que el metodo anterior, pero vinculando (o no) un Cliente. */
    private void sembrarUsuarioSiNoExiste(String username, String password, String nombre, String correo, Rol rol,
                                           Cliente cliente) {
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (usuario == null) {
            usuario = new Usuario(username, passwordEncoder.encode(password), nombre, correo, rol);
            usuario.setCliente(cliente);
            usuarioRepository.save(usuario);
            return;
        }
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setNombre(nombre);
        usuario.setCorreo(correo);
        usuario.setRol(rol);
        usuario.setCliente(cliente);
        usuarioRepository.save(usuario);
    }
}
