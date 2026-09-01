package com.trailersys.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.cliente.EstadoCliente;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.ConductorRepository;
import com.trailersys.backend.conductor.EstadoConductor;
import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.vehiculo.VehiculoRepository;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.ViajeRepository;

/**
 * /api/dashboard/resumen expone origen/destino/placa/conductor de los
 * proximos viajes de TODOS los clientes: con la llegada del rol CLIENTE
 * (autoservicio de pedidos) debe quedar excluido, para que un cliente no
 * pueda ver informacion de viajes de otros clientes por esta via aunque el
 * modulo no aparezca en su menu.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @Autowired
    private ConductorRepository conductorRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ViajeRepository viajeRepository;

    private String tokenPara(String username, Rol rol) throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase(username).isEmpty()) {
            usuarioRepository.save(new Usuario(username, passwordEncoder.encode("clave1234"), "Usuario " + username, null, rol));
        }
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"clave1234\"}".formatted(username)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void administradorPuedeVerElResumen() throws Exception {
        String token = tokenPara("admindashboard", Rol.ADMINISTRADOR);
        mockMvc.perform(get("/api/dashboard/resumen").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void clienteNoPuedeVerElResumenDeLaOperacion() throws Exception {
        String token = tokenPara("clientedashboard", Rol.CLIENTE);
        mockMvc.perform(get("/api/dashboard/resumen").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * El conductor tiene su propio resumen personalizado en
     * GET /api/mis-viajes/resumen (ver ViajeConductorController) y ya no
     * debe poder ver el de TODA la operacion por esta via.
     */
    @Test
    void conductorNoPuedeVerElResumenDeLaOperacion() throws Exception {
        String token = tokenPara("conductordashboard", Rol.CONDUCTOR);
        mockMvc.perform(get("/api/dashboard/resumen").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    /**
     * Un viaje puede quedar "PROGRAMADO" con su fecha_salida ya en el pasado
     * (el propio panel de Alertas operativas de Seguimiento marca ese caso
     * como alerta). "Próximos viajes" del Dashboard debe mostrar solo los
     * que de verdad estan por salir, no uno atrasado que ordenaria primero
     * por fecha_salida ASC.
     */
    @Test
    void proximosViajesExcluyeProgramadosConFechaDeSalidaYaPasada() throws Exception {
        Vehiculo vehiculo = vehiculoRepository.save(new Vehiculo(
                "DASH-0001", "Marca", "Modelo", "Tipo", 2020, "Rojo", EstadoVehiculo.DISPONIBLE, 0, 0, null, null));
        Conductor conductor = conductorRepository.save(new Conductor(
                "Conductor Dashboard", "CI-DASH", "0999999999", null, "LIC-DASH", "Tipo B",
                LocalDate.now().plusYears(1), EstadoConductor.DISPONIBLE, null, null, null));
        Cliente cliente = clienteRepository.save(new Cliente(
                "Cliente Dashboard", "CI-DASH-CLI", EstadoCliente.ACTIVO, "0999999999", null, "Direccion", null, null));

        viajeRepository.save(new Viaje(vehiculo, conductor, cliente, null, "Atrasado", "Atrasado",
                LocalDateTime.now().minusDays(1), EstadoViaje.PROGRAMADO, null));
        viajeRepository.save(new Viaje(vehiculo, conductor, cliente, null, "Futuro", "Futuro",
                LocalDateTime.now().plusDays(1), EstadoViaje.PROGRAMADO, null));

        String token = tokenPara("admindashboard2", Rol.ADMINISTRADOR);
        String body = mockMvc.perform(get("/api/dashboard/resumen").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var origenes = objectMapper.readTree(body).get("proximosViajes").findValuesAsText("origen");
        assertThat(origenes).contains("Futuro");
        assertThat(origenes).doesNotContain("Atrasado");
    }

    @Test
    void tendenciaDevuelveSieteDias() throws Exception {
        String token = tokenPara("admindashboard3", Rol.ADMINISTRADOR);
        String body = mockMvc.perform(get("/api/dashboard/tendencia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var puntos = objectMapper.readTree(body).get("viajesPorDia");
        assertThat(puntos).hasSize(7);
    }

    @Test
    void tendenciaCuentaSoloLosViajesDelDiaCorrespondiente() throws Exception {
        Vehiculo vehiculo = vehiculoRepository.save(new Vehiculo(
                "DASH-0002", "Marca", "Modelo", "Tipo", 2020, "Rojo", EstadoVehiculo.DISPONIBLE, 0, 0, null, null));
        Conductor conductor = conductorRepository.save(new Conductor(
                "Conductor Dashboard Tendencia", "CI-DASH-TEND", "0999999999", null, "LIC-TEND", "Tipo B",
                LocalDate.now().plusYears(1), EstadoConductor.DISPONIBLE, null, null, null));
        Cliente cliente = clienteRepository.save(new Cliente(
                "Cliente Dashboard Tendencia", "CI-DASH-TEND", EstadoCliente.ACTIVO, "0999999999", null, "Direccion", null, null));

        // "Hoy" a mediodia: cae dentro de la ventana [hoy 00:00, mañana 00:00)
        // que usa tendencia(), asi que debe sumar al ultimo punto.
        viajeRepository.save(new Viaje(vehiculo, conductor, cliente, null, "Hoy", "Hoy",
                LocalDateTime.now().withHour(12).withMinute(0), EstadoViaje.PROGRAMADO, null));

        String token = tokenPara("admindashboard4", Rol.ADMINISTRADOR);
        String body = mockMvc.perform(get("/api/dashboard/tendencia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var puntos = objectMapper.readTree(body).get("viajesPorDia");
        long ultimoDia = puntos.get(puntos.size() - 1).get("cantidad").asLong();
        assertThat(ultimoDia).isGreaterThanOrEqualTo(1);
    }

    @Test
    void supervisorPuedeVerLaTendencia() throws Exception {
        String token = tokenPara("supervisordashboard", Rol.SUPERVISOR);
        mockMvc.perform(get("/api/dashboard/tendencia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void conductorNoPuedeVerLaTendenciaDeLaOperacion() throws Exception {
        String token = tokenPara("conductordashboardtend", Rol.CONDUCTOR);
        mockMvc.perform(get("/api/dashboard/tendencia").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
