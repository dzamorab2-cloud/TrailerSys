package com.trailersys.backend.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * GET /api/mantenimientos/reportes/tendencia alimenta la grafica de
 * tendencia del Dashboard personal del rol Mantenimiento.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MantenimientoReporteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

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
    void mantenimientoPuedeVerLaTendencia() throws Exception {
        String token = tokenPara("mantenimientoreportetend", Rol.MANTENIMIENTO);
        String body = mockMvc.perform(get("/api/mantenimientos/reportes/tendencia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var puntos = objectMapper.readTree(body).get("mantenimientosPorMes");
        assertThat(puntos).hasSize(6);
    }

    @Test
    void administradorPuedeVerLaTendencia() throws Exception {
        String token = tokenPara("adminreportetend", Rol.ADMINISTRADOR);
        mockMvc.perform(get("/api/mantenimientos/reportes/tendencia").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void coordinadorNoPuedeVerLaTendenciaDeMantenimientos() throws Exception {
        String token = tokenPara("coordinadorreportetend", Rol.COORDINADOR);
        mockMvc.perform(get("/api/mantenimientos/reportes/tendencia").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
