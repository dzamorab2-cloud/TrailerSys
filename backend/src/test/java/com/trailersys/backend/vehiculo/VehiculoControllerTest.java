package com.trailersys.backend.vehiculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;

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
}
