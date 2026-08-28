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
}
