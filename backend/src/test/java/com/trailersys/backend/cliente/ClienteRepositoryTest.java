package com.trailersys.backend.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ClienteRepositoryTest {

    @Autowired
    private ClienteRepository repository;

    @Test
    void guardaYEncuentraPorIdentificacionSinImportarMayusculas() {
        repository.save(new Cliente(
                "Cliente Prueba", "xyz789", EstadoCliente.ACTIVO,
                "0999999999", null, "Direccion de prueba", null, null));

        assertThat(repository.findByIdentificacionIgnoreCase("XYZ789")).isPresent();
        assertThat(repository.existsByIdentificacionIgnoreCase("xyz789")).isTrue();
        assertThat(repository.existsByIdentificacionIgnoreCase("no-existe")).isFalse();
    }
}
