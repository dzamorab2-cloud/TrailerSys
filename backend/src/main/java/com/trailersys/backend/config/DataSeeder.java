package com.trailersys.backend.config;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository, VehiculoRepository vehiculoRepository,
                       ConductorRepository conductorRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.vehiculoRepository = vehiculoRepository;
        this.conductorRepository = conductorRepository;
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
    }
}
