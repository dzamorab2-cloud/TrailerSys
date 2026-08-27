package com.trailersys.backend.viaje;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.EstadoCliente;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.EstadoConductor;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;

@DataJpaTest
class ViajeRepositoryTest {

    @Autowired
    private ViajeRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void guardaYRecuperaViajeConSusRelacionesYRuta() {
        Vehiculo vehiculo = entityManager.persist(new Vehiculo(
                "REP-0001", "Marca", "Modelo", "Tipo", 2020, "Rojo", EstadoVehiculo.DISPONIBLE, 0, 0, null, null));
        Conductor conductor = entityManager.persist(new Conductor(
                "Conductor Repo", "CI-REPO", "0999999999", null, "LIC-1", "Tipo B",
                LocalDate.now().plusYears(1), EstadoConductor.DISPONIBLE, null, null, null));
        Cliente cliente = entityManager.persist(new Cliente(
                "Cliente Repo", "CI-REPO-CLI", EstadoCliente.ACTIVO, "0999999999", null, "Direccion", null, null));

        Viaje viaje = new Viaje(vehiculo, conductor, cliente, null, "Origen Repo", "Destino Repo",
                LocalDateTime.of(2026, 1, 1, 8, 0), EstadoViaje.PROGRAMADO, null);
        viaje.setRutaDistanciaKm(123.4);
        viaje.setRutaDuracionMin(90.0);
        Viaje guardado = repository.save(viaje);

        Viaje encontrado = repository.findById(guardado.getId()).orElseThrow();
        assertThat(encontrado.getVehiculo().getPlaca()).isEqualTo("REP-0001");
        assertThat(encontrado.getConductor().getNombres()).isEqualTo("Conductor Repo");
        assertThat(encontrado.getCliente().getNombre()).isEqualTo("Cliente Repo");
        assertThat(encontrado.getCarga()).isNull();
        assertThat(encontrado.getRutaDistanciaKm()).isEqualTo(123.4);
    }

    @Test
    void buscarFiltraPorTextoIncluyendoPlacaYConductorYPorEstadoEnTodaLaTabla() {
        Vehiculo vehiculo = entityManager.persist(new Vehiculo(
                "BUS-0001", "Marca", "Modelo", "Tipo", 2020, "Rojo", EstadoVehiculo.DISPONIBLE, 0, 0, null, null));
        Conductor conductor = entityManager.persist(new Conductor(
                "Juan Perez", "CI-BUS", "0999999999", null, "LIC-1", "Tipo B",
                LocalDate.now().plusYears(1), EstadoConductor.DISPONIBLE, null, null, null));
        Cliente cliente = entityManager.persist(new Cliente(
                "Cliente Buscar", "CI-BUS-CLI", EstadoCliente.ACTIVO, "0999999999", null, "Direccion", null, null));

        repository.save(new Viaje(vehiculo, conductor, cliente, null, "Quito", "Guayaquil",
                LocalDateTime.of(2026, 1, 1, 8, 0), EstadoViaje.PROGRAMADO, null));
        repository.save(new Viaje(vehiculo, conductor, cliente, null, "Cuenca", "Loja",
                LocalDateTime.of(2026, 2, 1, 8, 0), EstadoViaje.FINALIZADO, null));

        var porPlaca = repository.buscar("bus-0001", null, PageRequest.of(0, 10));
        assertThat(porPlaca.getTotalElements()).isEqualTo(2);

        var porConductor = repository.buscar("juan perez", null, PageRequest.of(0, 10));
        assertThat(porConductor.getTotalElements()).isEqualTo(2);

        var porEstado = repository.buscar("", EstadoViaje.FINALIZADO, PageRequest.of(0, 10));
        assertThat(porEstado.getTotalElements()).isEqualTo(1);
        assertThat(porEstado.getContent().get(0).getOrigen()).isEqualTo("Cuenca");
    }
}
