package com.trailersys.backend.vehiculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.ViajeRepository;

@SpringBootTest
@AutoConfigureMockMvc
class VehiculoControllerTest {

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

    private String tokenAdmin;

    @BeforeEach
    void prepararUsuarioYToken() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("admintest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "admintest", passwordEncoder.encode("clave1234"), "Admin Test", null, Rol.ADMINISTRADOR));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admintest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();

        tokenAdmin = objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void listarSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/vehiculos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearConsultarYEliminarVehiculo() throws Exception {
        String nuevoVehiculo = """
                {
                  "placa": "TST-0001",
                  "marca": "Marca Test",
                  "modelo": "Modelo Test",
                  "tipo": "Camión",
                  "anio": 2022,
                  "color": "Negro",
                  "estado": "Disponible",
                  "kilometraje": 1000,
                  "capacidad": 500,
                  "observaciones": "",
                  "foto": null
                }
                """;

        String creado = mockMvc.perform(post("/api/vehiculos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoVehiculo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placa").value("TST-0001"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(creado).get("id").asLong();

        String listado = mockMvc.perform(get("/api/vehiculos")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean contieneNuevo = false;
        for (JsonNode nodo : objectMapper.readTree(listado)) {
            if ("TST-0001".equals(nodo.get("placa").asText())) {
                contieneNuevo = true;
                break;
            }
        }
        assertThat(contieneNuevo).isTrue();

        mockMvc.perform(delete("/api/vehiculos/" + id)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    void crearConPlacaDuplicadaDevuelveConflicto() throws Exception {
        String vehiculo = """
                {"placa":"DUP-0001","marca":"M","modelo":"M","tipo":"Camión","anio":2020,"color":"Rojo",
                 "estado":"Disponible","kilometraje":0,"capacidad":0}
                """;

        mockMvc.perform(post("/api/vehiculos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiculo))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/vehiculos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiculo))
                .andExpect(status().isConflict());
    }

    @Test
    void crearSinPermisosDevuelveProhibido() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("conductortest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "conductortest", passwordEncoder.encode("clave1234"), "Conductor Test", null, Rol.CONDUCTOR));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"conductortest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenConductor = objectMapper.readTree(body).get("token").asText();

        String nuevoVehiculo = """
                {"placa":"XXX-0000","marca":"M","modelo":"M","tipo":"Camión","anio":2020,"color":"Rojo",
                 "estado":"Disponible","kilometraje":0,"capacidad":0}
                """;

        mockMvc.perform(post("/api/vehiculos")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoVehiculo))
                .andExpect(status().isForbidden());
    }

    /**
     * Eliminar un vehiculo referenciado por un Viaje (vehiculo_id es NOT
     * NULL en la tabla viajes) rompe la restriccion de clave foranea en la
     * base de datos. Antes, VehiculoService.eliminar() no comprobaba esto
     * de antemano y GlobalExceptionHandler no traducia
     * DataIntegrityViolationException, asi que esa violacion caia en el
     * manejador generico y devolvia 500 "Ocurrio un error inesperado" - sin
     * ninguna pista de que el vehiculo seguia en uso.
     */
    @Test
    void eliminarVehiculoConViajesAsociadosDevuelveConflictoNoErrorDeServidor() throws Exception {
        Vehiculo vehiculo = vehiculoRepository.save(new Vehiculo(
                "REF-0001", "Marca", "Modelo", "Tipo", 2020, "Rojo", EstadoVehiculo.DISPONIBLE, 0, 0, null, null));
        Conductor conductor = conductorRepository.save(new Conductor(
                "Conductor Referenciado", "CI-REF", "0999999999", null, "LIC-REF", "Tipo B",
                LocalDate.now().plusYears(1), EstadoConductor.DISPONIBLE, null, null, null));
        Cliente cliente = clienteRepository.save(new Cliente(
                "Cliente Referenciado", "CI-REF-CLI", EstadoCliente.ACTIVO, "0999999999", null, "Direccion", null, null));
        viajeRepository.save(new Viaje(vehiculo, conductor, cliente, null, "Origen", "Destino",
                LocalDateTime.now().plusDays(1), EstadoViaje.PROGRAMADO, null));

        mockMvc.perform(delete("/api/vehiculos/" + vehiculo.getId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "No se puede eliminar: otros registros (por ejemplo, viajes) todavia hacen referencia a este."));
    }
}
