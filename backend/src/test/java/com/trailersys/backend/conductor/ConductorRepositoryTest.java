package com.trailersys.backend.conductor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ConductorRepositoryTest {

    @Autowired
    private ConductorRepository repository;

    @Test
    void guardaYEncuentraPorIdentificacionSinImportarMayusculas() {
        repository.save(new Conductor(
                "Nombre Prueba", "abc123", "0999999999", null,
                "LIC-1", "Tipo B", LocalDate.now().plusYears(1),
                EstadoConductor.DISPONIBLE, null, null, null));

        assertThat(repository.findByIdentificacionIgnoreCase("ABC123")).isPresent();
        assertThat(repository.existsByIdentificacionIgnoreCase("abc123")).isTrue();
        assertThat(repository.existsByIdentificacionIgnoreCase("no-existe")).isFalse();
    }
}
