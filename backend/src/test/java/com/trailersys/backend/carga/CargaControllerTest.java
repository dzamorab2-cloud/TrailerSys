package com.trailersys.backend.carga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class CargaControllerTest {

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

    private Long crearVehiculoDePrueba(String placa) throws Exception {
        String vehiculo = """
                {"placa":"%s","marca":"M","modelo":"M","tipo":"Camión","anio":2020,"color":"Rojo",
                 "estado":"Disponible","kilometraje":0,"capacidad":0}
                """.formatted(placa);
        String creado = mockMvc.perform(post("/api/vehiculos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiculo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private Long crearConductorDePrueba(String identificacion) throws Exception {
        String conductor = """
                {"nombres":"Conductor Carga Test","identificacion":"%s","telefono":"0999999999",
                 "licenciaNumero":"LIC-%s","licenciaCategoria":"Tipo E",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible"}
                """.formatted(identificacion, identificacion);
        String creado = mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private Long crearClienteDePrueba(String identificacion) throws Exception {
        String cliente = """
                {"nombre":"Cliente Carga Test","identificacion":"%s","estado":"Activo",
                 "telefono":"0999999999","direccion":"Direccion de prueba"}
                """.formatted(identificacion);

        String creado = mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(creado).get("id").asLong();
    }

    @Test
    void listarSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/cargas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearConsultarYListarCarga() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-CARGA-001");

        String carga = """
                {
                  "descripcion": "Repuestos automotrices",
                  "clienteId": %d,
                  "tipo": "Autopartes",
                  "peso": 950,
                  "origen": "Cuenca",
                  "destino": "Loja",
                  "estado": "Pendiente"
                }
                """.formatted(clienteId);

        String creada = mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andExpect(jsonPath("$.clienteNombre").value("Cliente Carga Test"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(creada).get("id").asLong()).isPositive();

        String listado = mockMvc.perform(get("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Ejercita repository.findAll() + mapeo a DTO fuera de la transaccion
        // de lectura: si Carga.cliente fuera LAZY, cliente.getNombre() aqui
        // lanzaria LazyInitializationException (ver comentario en Carga.cliente).
        String clienteNombreEnListado = null;
        for (JsonNode nodo : objectMapper.readTree(listado)) {
            if ("Repuestos automotrices".equals(nodo.get("descripcion").asText())) {
                clienteNombreEnListado = nodo.get("clienteNombre").asText();
                break;
            }
        }
        assertThat(clienteNombreEnListado).isEqualTo("Cliente Carga Test");
    }

    @Test
    void crearConClienteInexistenteDevuelveNoEncontrado() throws Exception {
        String carga = """
                {"descripcion":"Carga sin cliente","clienteId":999999,"tipo":"General",
                 "peso":100,"origen":"A","destino":"B","estado":"Pendiente"}
                """;

        mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearConPesoNegativoDevuelve400() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-CARGA-002");

        String carga = """
                {"descripcion":"Carga con peso invalido","clienteId":%d,"tipo":"General",
                 "peso":-10,"origen":"A","destino":"B","estado":"Pendiente"}
                """.formatted(clienteId);

        mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolSinAccesoAModuloDevuelveProhibido() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("supervisortest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "supervisortest", passwordEncoder.encode("clave1234"), "Supervisor Test", null, Rol.SUPERVISOR));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"supervisortest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenSupervisor = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/cargas")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarUnaCargaPendienteFunciona() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-CARGA-EDIT");
        String carga = """
                {"descripcion":"Original","clienteId":%d,"tipo":"General",
                 "peso":100,"origen":"A","destino":"B","estado":"Pendiente"}
                """.formatted(clienteId);
        String creada = mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cargaId = objectMapper.readTree(creada).get("id").asLong();

        String edicion = """
                {"descripcion":"Corregida","clienteId":%d,"tipo":"Textiles",
                 "peso":200,"origen":"C","destino":"D","estado":"Pendiente"}
                """.formatted(clienteId);
        mockMvc.perform(put("/api/cargas/" + cargaId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edicion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Corregida"))
                .andExpect(jsonPath("$.peso").value(200));
    }

    @Test
    void eliminarUnaCargaPendienteFunciona() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-CARGA-DEL");
        String carga = """
                {"descripcion":"A borrar","clienteId":%d,"tipo":"General",
                 "peso":100,"origen":"A","destino":"B","estado":"Pendiente"}
                """.formatted(clienteId);
        String creada = mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cargaId = objectMapper.readTree(creada).get("id").asLong();

        mockMvc.perform(delete("/api/cargas/" + cargaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cargas/" + cargaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
    }

    @Test
    void editarNiEliminarUnaCargaYaAsignadaAUnViajeDaConflicto() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-CARGA-ASIG");
        Long vehiculoId = crearVehiculoDePrueba("CRG-ASIG-01");
        Long conductorId = crearConductorDePrueba("CI-CARGA-ASIG-COND");

        String carga = """
                {"descripcion":"Ya en proceso","clienteId":%d,"tipo":"General",
                 "peso":100,"origen":"Quito","destino":"Guayaquil","estado":"Pendiente"}
                """.formatted(clienteId);
        String creada = mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cargaId = objectMapper.readTree(creada).get("id").asLong();

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"Quito","destino":"Guayaquil","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId, cargaId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated());

        // La carga ya paso a "Asignada" sola (sincronizarEstadoCarga): a
        // partir de aca, editar o eliminar deben rechazarse.
        mockMvc.perform(get("/api/cargas/" + cargaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("Asignada"));

        String edicion = """
                {"descripcion":"Intento de edicion","clienteId":%d,"tipo":"General",
                 "peso":100,"origen":"Quito","destino":"Guayaquil","estado":"Asignada"}
                """.formatted(clienteId);
        mockMvc.perform(put("/api/cargas/" + cargaId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edicion))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/cargas/" + cargaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());
    }
}
