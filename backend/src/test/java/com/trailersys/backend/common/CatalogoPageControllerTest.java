package com.trailersys.backend.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * Regresion de seguridad (hallazgo H-01 de las pruebas de V&V): las paginas
 * de catalogo {@code /api/paginas/viajes} y {@code /api/paginas/eventos} no
 * filtran por conductor, asi que un token CONDUCTOR podia paginar por toda la
 * flota (viajes y bitacora de seguimiento) via API directa aunque su
 * interfaz no muestre esos modulos. El conductor debe usar
 * {@code /api/mis-viajes} y {@code /api/seguimiento/eventos} (acotados al
 * usuario). Los roles internos (Supervisor entre ellos) conservan el acceso.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenConductor;
    private String tokenSupervisor;

    @BeforeEach
    void prepararTokens() throws Exception {
        tokenConductor = tokenPara("conductorpaginastest", Rol.CONDUCTOR);
        tokenSupervisor = tokenPara("supervisorpaginastest", Rol.SUPERVISOR);
    }

    private String tokenPara(String username, Rol rol) throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase(username).isEmpty()) {
            usuarioRepository.save(new Usuario(
                    username, passwordEncoder.encode("clave1234"), "Usuario " + username, null, rol));
        }
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"clave1234\"}".formatted(username)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void conductorNoPuedePaginarLosViajesDeLaFlota() throws Exception {
        mockMvc.perform(get("/api/paginas/viajes")
                        .header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isForbidden());
    }

    @Test
    void conductorNoPuedePaginarLaBitacoraDeLaFlota() throws Exception {
        mockMvc.perform(get("/api/paginas/eventos")
                        .header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isForbidden());
    }

    @Test
    void supervisorSiguePudiendoPaginarViajesYEventos() throws Exception {
        mockMvc.perform(get("/api/paginas/viajes")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/paginas/eventos")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isOk());
    }
}
