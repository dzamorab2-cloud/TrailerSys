package com.trailersys.backend.respaldo;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;

/**
 * Solo cubre lo que H2 puede probar: permisos (exclusivo de Administrador) y
 * el CRUD de ConfiguracionRespaldo/el listado. La ejecucion real de
 * pg_dump y la consulta a la tabla "auditoria" (que no existe en H2, se crea
 * a mano en Postgres) se verifican en vivo, mismo criterio ya usado para
 * AuditoriaController en este proyecto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RespaldoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenAdmin;
    private String tokenSupervisor;

    @BeforeEach
    void prepararUsuariosYTokens() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("admintestrespaldo").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "admintestrespaldo", passwordEncoder.encode("clave1234"), "Admin Test Respaldo", null, Rol.ADMINISTRADOR));
        }
        if (usuarioRepository.findByUsernameIgnoreCase("supervisortestrespaldo").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "supervisortestrespaldo", passwordEncoder.encode("clave1234"), "Supervisor Test Respaldo", null, Rol.SUPERVISOR));
        }
        tokenAdmin = login("admintestrespaldo", "clave1234");
        tokenSupervisor = login("supervisortestrespaldo", "clave1234");
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void unRolQueNoEsAdministradorNoPuedeVerElHistorial() throws Exception {
        mockMvc.perform(get("/api/respaldos")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isForbidden());
    }

    @Test
    void unRolQueNoEsAdministradorNoPuedeVerNiEditarLaConfiguracion() throws Exception {
        mockMvc.perform(get("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true,\"frecuencia\":\"DIARIO\",\"horaProgramada\":\"03:30:00\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unRolQueNoEsAdministradorNoPuedeDispararRespaldos() throws Exception {
        mockMvc.perform(post("/api/respaldos/completo")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/respaldos/incremental")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isForbidden());
    }

    @Test
    void elAdministradorPuedeVerElHistorialYLaConfiguracion() throws Exception {
        // No se asume un valor por defecto especifico: la fila de configuracion
        // es unica y compartida, y otra prueba de esta misma clase puede haberla
        // modificado antes (el orden entre @Test no esta garantizado). Solo se
        // verifica que ambos endpoints responden con la forma esperada.
        mockMvc.perform(get("/api/respaldos")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horaProgramada").exists());
    }

    @Test
    void elAdministradorPuedeActualizarLaConfiguracionYQuedaGuardada() throws Exception {
        mockMvc.perform(put("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true,\"frecuencia\":\"DIARIO\",\"horaProgramada\":\"04:15:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.frecuencia").value("DIARIO"))
                .andExpect(jsonPath("$.horaProgramada").value("04:15:00"));

        mockMvc.perform(get("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.horaProgramada").value("04:15:00"));
    }

    @Test
    void elAdministradorPuedeConfigurarFrecuenciaSemanalConSuDia() throws Exception {
        mockMvc.perform(put("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true,\"frecuencia\":\"SEMANAL\",\"horaProgramada\":\"01:00:00\",\"diaSemana\":\"FRIDAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuencia").value("SEMANAL"))
                .andExpect(jsonPath("$.diaSemana").value("FRIDAY"));
    }

    @Test
    void elAdministradorPuedeConfigurarFrecuenciaMensualConSuDia() throws Exception {
        mockMvc.perform(put("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true,\"frecuencia\":\"MENSUAL\",\"horaProgramada\":\"01:00:00\",\"diaMes\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frecuencia").value("MENSUAL"))
                .andExpect(jsonPath("$.diaMes").value(15));
    }

    @Test
    void laConfiguracionSemanalSinDiaDeLaSemanaEsRechazada() throws Exception {
        mockMvc.perform(put("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true,\"frecuencia\":\"SEMANAL\",\"horaProgramada\":\"01:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laConfiguracionMensualSinDiaDelMesEsRechazada() throws Exception {
        mockMvc.perform(put("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true,\"frecuencia\":\"MENSUAL\",\"horaProgramada\":\"01:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laConfiguracionRechazaUnDiaDelMesFueraDeRango() throws Exception {
        mockMvc.perform(put("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true,\"frecuencia\":\"MENSUAL\",\"horaProgramada\":\"01:00:00\",\"diaMes\":32}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laConfiguracionRechazaUnaHoraProgramadaAusente() throws Exception {
        mockMvc.perform(put("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true,\"frecuencia\":\"DIARIO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laConfiguracionRechazaUnaFrecuenciaAusente() throws Exception {
        mockMvc.perform(put("/api/respaldos/configuracion")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true,\"horaProgramada\":\"01:00:00\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void descargarUnRespaldoInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/respaldos/999999/descargar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNotFound());
    }
}
