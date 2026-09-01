package com.trailersys.backend.dashboard;

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
 * GET /api/dashboard/disponibilidad alimenta el "Panel de disponibilidad"
 * (Administrador/Coordinador) y los anillos de "Flota disponible" del
 * Dashboard (generico y del Supervisor) - Mantenimiento tambien lo usa
 * ahora para su propio anillo con el detalle de vehiculos por estado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DisponibilidadControllerTest {

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
    void administradorPuedeVerLaDisponibilidad() throws Exception {
        String token = tokenPara("admindisponibilidad", Rol.ADMINISTRADOR);
        mockMvc.perform(get("/api/dashboard/disponibilidad").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void mantenimientoPuedeVerLaDisponibilidad() throws Exception {
        String token = tokenPara("mantenimientodisponibilidad", Rol.MANTENIMIENTO);
        mockMvc.perform(get("/api/dashboard/disponibilidad").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void supervisorPuedeVerLaDisponibilidad() throws Exception {
        String token = tokenPara("supervisordisponibilidad", Rol.SUPERVISOR);
        mockMvc.perform(get("/api/dashboard/disponibilidad").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void conductorNoPuedeVerLaDisponibilidadDeLaOperacion() throws Exception {
        String token = tokenPara("conductordisponibilidad", Rol.CONDUCTOR);
        mockMvc.perform(get("/api/dashboard/disponibilidad").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteNoPuedeVerLaDisponibilidadDeLaOperacion() throws Exception {
        String token = tokenPara("clientedisponibilidad", Rol.CLIENTE);
        mockMvc.perform(get("/api/dashboard/disponibilidad").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
