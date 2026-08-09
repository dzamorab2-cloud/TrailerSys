package com.trailersys.backend.vehiculo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class VehiculoRepositoryTest {

    @Autowired
    private VehiculoRepository repository;

    @Test
    void guardaYEncuentraPorPlacaSinImportarMayusculas() {
        repository.save(new Vehiculo("ABC-123", "Marca", "Modelo", "Tipo", 2020, "Rojo",
                EstadoVehiculo.DISPONIBLE, 1000, 500, null, null));

        assertThat(repository.findByPlacaIgnoreCase("abc-123")).isPresent();
        assertThat(repository.existsByPlacaIgnoreCase("ABC-123")).isTrue();
        assertThat(repository.existsByPlacaIgnoreCase("XYZ-999")).isFalse();
    }
}
