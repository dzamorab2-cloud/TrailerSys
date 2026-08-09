package com.trailersys.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository, VehiculoRepository vehiculoRepository,
                       PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.vehiculoRepository = vehiculoRepository;
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
    }
}
