package com.trailersys.backend.vehiculo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void buscarFiltraPorTextoYPorEstadoEnTodaLaTabla() {
        repository.save(new Vehiculo("BUS-0001", "Freightliner", "Cascadia", "Tipo", 2020, "Rojo",
                EstadoVehiculo.DISPONIBLE, 0, 0, null, null));
        repository.save(new Vehiculo("BUS-0002", "Kenworth", "T680", "Tipo", 2020, "Azul",
                EstadoVehiculo.EN_RUTA, 0, 0, null, null));

        var porTexto = repository.buscar("cascadia", null, PageRequest.of(0, 10));
        assertThat(porTexto.getTotalElements()).isEqualTo(1);
        assertThat(porTexto.getContent().get(0).getPlaca()).isEqualTo("BUS-0001");

        var porEstado = repository.buscar("", EstadoVehiculo.EN_RUTA, PageRequest.of(0, 10));
        assertThat(porEstado.getTotalElements()).isEqualTo(1);
        assertThat(porEstado.getContent().get(0).getPlaca()).isEqualTo("BUS-0002");

        var sinFiltro = repository.buscar("", null, PageRequest.of(0, 10));
        assertThat(sinFiltro.getTotalElements()).isEqualTo(2);
    }
}
