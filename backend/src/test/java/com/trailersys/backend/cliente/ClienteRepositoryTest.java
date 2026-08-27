package com.trailersys.backend.cliente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void buscarFiltraPorTextoYPorEstadoEnTodaLaTabla() {
        repository.save(new Cliente("Transportes Andinos", "CI-100", EstadoCliente.ACTIVO,
                "0999999999", null, "Direccion 1", null, null));
        repository.save(new Cliente("Comercial Pacifico", "CI-200", EstadoCliente.INACTIVO,
                "0988888888", null, "Direccion 2", null, null));

        var porTexto = repository.buscar("andinos", null, PageRequest.of(0, 10));
        assertThat(porTexto.getTotalElements()).isEqualTo(1);
        assertThat(porTexto.getContent().get(0).getIdentificacion()).isEqualTo("CI-100");

        var porEstado = repository.buscar("", EstadoCliente.INACTIVO, PageRequest.of(0, 10));
        assertThat(porEstado.getTotalElements()).isEqualTo(1);
        assertThat(porEstado.getContent().get(0).getIdentificacion()).isEqualTo("CI-200");

        var sinFiltro = repository.buscar("", null, PageRequest.of(0, 10));
        assertThat(sinFiltro.getTotalElements()).isEqualTo(2);
    }
}
