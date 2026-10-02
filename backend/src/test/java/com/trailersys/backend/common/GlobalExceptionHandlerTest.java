package com.trailersys.backend.common;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;

/**
 * Regresion del hallazgo H-02: una ruta inexistente bajo un prefijo valido
 * (ej. /api/viajes/no-existe) devolvia 500 "Ocurrio un error inesperado" por
 * el manejo por defecto de recursos estaticos de Spring Boot. Ahora
 * GlobalExceptionHandler.handleNoHandlerFound() la traduce a 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void prepararToken() throws Exception {
        String username = "adminh02test";
        if (usuarioRepository.findByUsernameIgnoreCase(username).isEmpty()) {
            usuarioRepository.save(new Usuario(
                    username, passwordEncoder.encode("clave1234"), "Usuario " + username, null, Rol.ADMINISTRADOR));
        }
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"clave1234\"}".formatted(username)))
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void rutaInexistenteBajoPrefijoValidoDevuelve404() throws Exception {
        // /api/viajes/{id} existe (GET), pero no /api/viajes/{id}/subrecurso-inventado:
        // un id numerico valido evita el 400 de MethodArgumentTypeMismatchException
        // y deja ver el caso real de H-02, una ruta que ningun @GetMapping declara.
        mockMvc.perform(get("/api/viajes/1/subrecurso-inventado")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rutaInexistenteFueraDeApiDevuelve404() throws Exception {
        mockMvc.perform(get("/no-existe-ningun-prefijo")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
