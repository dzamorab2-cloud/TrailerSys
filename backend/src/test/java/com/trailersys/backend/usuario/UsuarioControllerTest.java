package com.trailersys.backend.usuario;

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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * "Cliente asociado"/"Conductor asociado" vinculan la cuenta de acceso
 * (Usuario) con el registro operativo (Cliente/Conductor) que ya existe en
 * su propio modulo. Sin la validacion que cubren estas pruebas, nada
 * impedia que dos cuentas distintas quedaran vinculadas a la misma persona.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UsuarioControllerTest {

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
        if (usuarioRepository.findByUsernameIgnoreCase("admintestusuario").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "admintestusuario", passwordEncoder.encode("clave1234"), "Admin Test", null, Rol.ADMINISTRADOR));
        }
        tokenAdmin = login("admintestusuario", "clave1234");
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private Long crearCliente(String identificacion) throws Exception {
        String cliente = """
                {"nombre":"Cliente %s","identificacion":"%s","estado":"Activo",
                 "telefono":"0999999999","direccion":"Direccion de prueba"}
                """.formatted(identificacion, identificacion);
        String creado = mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private Long crearConductor(String identificacion) throws Exception {
        String conductor = """
                {"nombres":"Conductor %s","identificacion":"%s","telefono":"0999999999",
                 "licenciaNumero":"LIC-%s","licenciaCategoria":"Tipo E",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible"}
                """.formatted(identificacion, identificacion, identificacion);
        String creado = mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private String usuarioClientePayload(String username, Long clienteId) {
        return """
                {"username":"%s","password":"clave1234","nombre":"Usuario %s","rol":"CLIENTE","activo":true,"clienteId":%d}
                """.formatted(username, username, clienteId);
    }

    private String usuarioConductorPayload(String username, Long conductorId) {
        return """
                {"username":"%s","password":"clave1234","nombre":"Usuario %s","rol":"CONDUCTOR","activo":true,"conductorId":%d}
                """.formatted(username, username, conductorId);
    }

    @Test
    void dosUsuariosNoPuedenQuedarVinculadosAlMismoCliente() throws Exception {
        Long clienteId = crearCliente("CI-USR-CLI-DUP");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuarioClientePayload("usrclidupa", clienteId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuarioClientePayload("usrclidupb", clienteId)))
                .andExpect(status().isConflict());
    }

    @Test
    void dosUsuariosNoPuedenQuedarVinculadosAlMismoConductor() throws Exception {
        Long conductorId = crearConductor("CI-USR-CON-DUP");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuarioConductorPayload("usrcondupa", conductorId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuarioConductorPayload("usrcondupb", conductorId)))
                .andExpect(status().isConflict());
    }

    @Test
    void editarUnUsuarioSinCambiarSuClienteNoSeRechazaASiMismo() throws Exception {
        Long clienteId = crearCliente("CI-USR-CLI-EDIT");
        String creado = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuarioClientePayload("usrcliedit", clienteId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long usuarioId = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(put("/api/usuarios/" + usuarioId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"usrcliedit","nombre":"Usuario Editado","rol":"CLIENTE",
                                 "activo":true,"clienteId":%d}
                                """.formatted(clienteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Usuario Editado"))
                .andExpect(jsonPath("$.clienteId").value(clienteId));
    }

    @Test
    void editarUnUsuarioParaApuntarAUnConductorYaVinculadoAOtroDaConflicto() throws Exception {
        Long conductorLibre = crearConductor("CI-USR-CON-LIBRE");
        Long conductorOcupado = crearConductor("CI-USR-CON-OCUPADO");

        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuarioConductorPayload("usrconocupado", conductorOcupado)))
                .andExpect(status().isCreated());

        String creado = mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuarioConductorPayload("usrconlibre", conductorLibre)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long usuarioId = objectMapper.readTree(creado).get("id").asLong();

        // Intenta reasignar "usrconlibre" al conductor que ya tiene dueño.
        mockMvc.perform(put("/api/usuarios/" + usuarioId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"usrconlibre","nombre":"Usuario Conductor Libre","rol":"CONDUCTOR",
                                 "activo":true,"conductorId":%d}
                                """.formatted(conductorOcupado)))
                .andExpect(status().isConflict());
    }
}
