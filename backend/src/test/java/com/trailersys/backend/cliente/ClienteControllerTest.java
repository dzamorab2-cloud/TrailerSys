package com.trailersys.backend.cliente;

import static org.assertj.core.api.Assertions.assertThat;
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
class ClienteControllerTest {

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
        mockMvc.perform(get("/api/clientes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearConsultarYListarCliente() throws Exception {
        String cliente = """
                {
                  "nombre": "Textiles del Norte",
                  "identificacion": "1790011223001",
                  "estado": "Activo",
                  "telefono": "022345566",
                  "correo": "contacto@textilesdelnorte.test",
                  "direccion": "Panamericana Norte km 12, Quito",
                  "servicios": "Carga seca, Urgente"
                }
                """;

        String creado = mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Textiles del Norte"))
                .andExpect(jsonPath("$.estado").value("Activo"))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(creado).get("id").asLong()).isPositive();

        String listado = mockMvc.perform(get("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean encontrado = false;
        for (JsonNode nodo : objectMapper.readTree(listado)) {
            if ("1790011223001".equals(nodo.get("identificacion").asText())) {
                encontrado = true;
                break;
            }
        }
        assertThat(encontrado).isTrue();
    }

    @Test
    void filtrarPorEstadoConLaEtiquetaQueUsaElFrontendFunciona() throws Exception {
        String cliente = """
                {"nombre":"Cliente Filtro","identificacion":"CI-CLI-FILTRO","estado":"Activo",
                 "telefono":"022345566","direccion":"Direccion de prueba"}
                """;
        mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isCreated());

        // "Activo" es la etiqueta que manda el frontend, no el nombre interno
        // del enum: antes de este fix, el binder por defecto de Spring solo
        // aceptaba "ACTIVO" y esta llamada devolvia 400.
        String listado = mockMvc.perform(get("/api/clientes")
                        .param("estado", "Activo")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(listado)).isNotEmpty();
    }

    @Test
    void crearConIdentificacionDuplicadaDevuelveConflicto() throws Exception {
        String cliente = """
                {"nombre":"Uno","identificacion":"DUP-CLI","estado":"Activo",
                 "telefono":"0999999999","direccion":"Direccion"}
                """;

        mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isConflict());
    }

    @Test
    void crearConCorreoInvalidoDevuelve400() throws Exception {
        String cliente = """
                {"nombre":"Cliente Correo Malo","identificacion":"CI-CORREO","estado":"Activo",
                 "telefono":"0999999999","correo":"no-es-un-correo","direccion":"Direccion"}
                """;

        mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rolCoordinadorDevuelveProhibido() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("coordinadortest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "coordinadortest", passwordEncoder.encode("clave1234"), "Coordinador Test", null, Rol.COORDINADOR));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"coordinadortest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenCoordinador = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/clientes")
                        .header("Authorization", "Bearer " + tokenCoordinador))
                .andExpect(status().isForbidden());
    }
}
