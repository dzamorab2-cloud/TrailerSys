package com.trailersys.backend.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;

@DataJpaTest
class MantenimientoRepositoryTest {

    @Autowired
    private MantenimientoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void listaMantenimientosDeUnVehiculoOrdenadosPorFechaDescendente() {
        Vehiculo vehiculo = entityManager.persist(new Vehiculo(
                "MNT-0001", "Marca", "Modelo", "Tipo", 2020, "Rojo", EstadoVehiculo.DISPONIBLE, 0, 0, null, null));

        repository.save(new Mantenimiento(vehiculo, TipoMantenimiento.PREVENTIVO,
                LocalDate.of(2026, 1, 1), 1000, 50.0, null, "Primer mantenimiento"));
        repository.save(new Mantenimiento(vehiculo, TipoMantenimiento.CORRECTIVO,
                LocalDate.of(2026, 3, 1), 2000, 150.0, null, "Segundo mantenimiento"));

        var mantenimientos = repository.findByVehiculoIdOrderByFechaDesc(vehiculo.getId());

        assertThat(mantenimientos).hasSize(2);
        assertThat(mantenimientos.get(0).getDescripcion()).isEqualTo("Segundo mantenimiento");
        assertThat(mantenimientos.get(1).getDescripcion()).isEqualTo("Primer mantenimiento");
    }

    @Test
    void buscarFiltraPorTextoPorVehiculoYPorTipoEnTodaLaTabla() {
        Vehiculo vehiculoA = entityManager.persist(new Vehiculo(
                "MNT-A001", "Marca", "Modelo", "Tipo", 2020, "Rojo", EstadoVehiculo.DISPONIBLE, 0, 0, null, null));
        Vehiculo vehiculoB = entityManager.persist(new Vehiculo(
                "MNT-B002", "Marca", "Modelo", "Tipo", 2020, "Azul", EstadoVehiculo.DISPONIBLE, 0, 0, null, null));

        repository.save(new Mantenimiento(vehiculoA, TipoMantenimiento.PREVENTIVO,
                LocalDate.of(2026, 1, 1), 1000, 50.0, null, "Cambio de aceite"));
        repository.save(new Mantenimiento(vehiculoB, TipoMantenimiento.CORRECTIVO,
                LocalDate.of(2026, 2, 1), 2000, 150.0, null, "Reparacion de frenos"));

        var porTexto = repository.buscar("aceite", null, null, PageRequest.of(0, 10));
        assertThat(porTexto.getTotalElements()).isEqualTo(1);
        assertThat(porTexto.getContent().get(0).getDescripcion()).isEqualTo("Cambio de aceite");

        var porVehiculo = repository.buscar("", vehiculoB.getId(), null, PageRequest.of(0, 10));
        assertThat(porVehiculo.getTotalElements()).isEqualTo(1);
        assertThat(porVehiculo.getContent().get(0).getVehiculo().getId()).isEqualTo(vehiculoB.getId());

        var porTipo = repository.buscar("", null, TipoMantenimiento.CORRECTIVO, PageRequest.of(0, 10));
        assertThat(porTipo.getTotalElements()).isEqualTo(1);
        assertThat(porTipo.getContent().get(0).getTipo()).isEqualTo(TipoMantenimiento.CORRECTIVO);
    }
}
