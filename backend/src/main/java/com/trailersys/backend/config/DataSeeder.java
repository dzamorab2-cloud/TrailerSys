package com.trailersys.backend.config;

import java.time.LocalDate;

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
import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.vehiculo.VehiculoRepository;

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
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository, VehiculoRepository vehiculoRepository,
                       ConductorRepository conductorRepository, ClienteRepository clienteRepository,
                       CargaRepository cargaRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.vehiculoRepository = vehiculoRepository;
        this.conductorRepository = conductorRepository;
        this.clienteRepository = clienteRepository;
        this.cargaRepository = cargaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() == 0) {
            usuarioRepository.save(new Usuario(
                    "admin",
                    passwordEncoder.encode("admin1234"),
                    "Administrador General",
                    "admin@trailersys.test",
                    Rol.ADMINISTRADOR));
        }

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
    }
}
