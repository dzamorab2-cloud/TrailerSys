package com.trailersys.backend.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.EstadoCliente;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.EstadoConductor;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.Viaje;

@DataJpaTest
class SeguimientoEventoRepositoryTest {

    @Autowired
    private SeguimientoEventoRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Viaje crearViajeDePrueba() {
        Vehiculo vehiculo = entityManager.persist(new Vehiculo(
                "SEG-0001", "Marca", "Modelo", "Tipo", 2020, "Rojo", EstadoVehiculo.DISPONIBLE, 0, 0, null, null));
        Conductor conductor = entityManager.persist(new Conductor(
                "Conductor Seg", "CI-SEG", "0999999999", null, "LIC-1", "Tipo B",
                LocalDate.now().plusYears(1), EstadoConductor.DISPONIBLE, null, null, null));
        Cliente cliente = entityManager.persist(new Cliente(
                "Cliente Seg", "CI-SEG-CLI", EstadoCliente.ACTIVO, "0999999999", null, "Direccion", null, null));

        return entityManager.persist(new Viaje(vehiculo, conductor, cliente, null, "Origen Seg", "Destino Seg",
                LocalDateTime.of(2026, 1, 1, 8, 0), EstadoViaje.EN_CURSO, null));
    }

    @Test
    void listaEventosDeUnViajeOrdenadosPorFechaDescendente() {
        Viaje viaje = crearViajeDePrueba();

        repository.save(new SeguimientoEvento(
                viaje, viaje.getVehiculo(), LocalDateTime.of(2026, 1, 1, 8, 0), TipoEvento.SALIDA, "Origen Seg", null));
        repository.save(new SeguimientoEvento(
                viaje, viaje.getVehiculo(), LocalDateTime.of(2026, 1, 1, 9, 30), TipoEvento.PARADA, "Km 20", null));

        var eventos = repository.findByViajeIdOrderByFechaHoraDesc(viaje.getId());

        assertThat(eventos).hasSize(2);
        assertThat(eventos.get(0).getEvento()).isEqualTo(TipoEvento.PARADA);
        assertThat(eventos.get(1).getEvento()).isEqualTo(TipoEvento.SALIDA);
    }
}
