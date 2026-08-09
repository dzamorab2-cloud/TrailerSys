package com.trailersys.backend.carga;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.EstadoCliente;

@DataJpaTest
class CargaRepositoryTest {

    @Autowired
    private CargaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void guardaYRecuperaCargaConSuCliente() {
        Cliente cliente = new Cliente("Cliente de Prueba", "CI-001", EstadoCliente.ACTIVO,
                "0999999999", null, "Direccion de prueba", null, null);
        entityManager.persist(cliente);

        Carga carga = repository.save(new Carga(
                "Carga de prueba", cliente, "General", 500, "Origen", "Destino", EstadoCarga.PENDIENTE, null));

        Carga encontrada = repository.findById(carga.getId()).orElseThrow();
        assertThat(encontrada.getCliente().getNombre()).isEqualTo("Cliente de Prueba");
        assertThat(encontrada.getEstado()).isEqualTo(EstadoCarga.PENDIENTE);
    }
}
