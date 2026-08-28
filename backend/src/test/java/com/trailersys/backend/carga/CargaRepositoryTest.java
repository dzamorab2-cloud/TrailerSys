package com.trailersys.backend.carga;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void buscarFiltraPorTextoIncluyendoNombreDelClienteYPorEstadoEnTodaLaTabla() {
        Cliente cliente = entityManager.persist(new Cliente("Textiles del Norte", "CI-900",
                EstadoCliente.ACTIVO, "0999999999", null, "Direccion", null, null));

        repository.save(new Carga("Rollos de tela", cliente, "General", 500,
                "Quito", "Guayaquil", EstadoCarga.PENDIENTE, null));
        repository.save(new Carga("Maquinaria pesada", cliente, "Industrial", 2000,
                "Cuenca", "Loja", EstadoCarga.ENTREGADA, null));

        var porClienteNombre = repository.buscar("textiles del norte", null, PageRequest.of(0, 10));
        assertThat(porClienteNombre.getTotalElements()).isEqualTo(2);

        var porDescripcion = repository.buscar("maquinaria", null, PageRequest.of(0, 10));
        assertThat(porDescripcion.getTotalElements()).isEqualTo(1);
        assertThat(porDescripcion.getContent().get(0).getDescripcion()).isEqualTo("Maquinaria pesada");

        var porEstado = repository.buscar("", EstadoCarga.ENTREGADA, PageRequest.of(0, 10));
        assertThat(porEstado.getTotalElements()).isEqualTo(1);
        assertThat(porEstado.getContent().get(0).getEstado()).isEqualTo(EstadoCarga.ENTREGADA);
    }

    @Test
    void findByClienteIdSoloDevuelveLasCargasDeEseClienteYFindByIdAndClienteIdAislaEntreClientes() {
        Cliente clienteA = entityManager.persist(new Cliente("Cliente A", "CI-A01",
                EstadoCliente.ACTIVO, "0999999999", null, "Direccion", null, null));
        Cliente clienteB = entityManager.persist(new Cliente("Cliente B", "CI-B01",
                EstadoCliente.ACTIVO, "0999999999", null, "Direccion", null, null));

        Carga cargaDeA = repository.save(new Carga("Pedido de A", clienteA, "General", 100,
                "Quito", "Guayaquil", EstadoCarga.PENDIENTE, null));
        repository.save(new Carga("Pedido de B", clienteB, "General", 200,
                "Cuenca", "Loja", EstadoCarga.PENDIENTE, null));

        var cargasDeA = repository.findByCliente_IdOrderByIdDesc(clienteA.getId());
        assertThat(cargasDeA).hasSize(1);
        assertThat(cargasDeA.get(0).getDescripcion()).isEqualTo("Pedido de A");

        assertThat(repository.findByIdAndCliente_Id(cargaDeA.getId(), clienteA.getId())).isPresent();
        assertThat(repository.findByIdAndCliente_Id(cargaDeA.getId(), clienteB.getId())).isEmpty();
    }
}
