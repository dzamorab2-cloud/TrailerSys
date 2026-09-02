package com.trailersys.backend.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    private Long crearVehiculo(String token, String placa) throws Exception {
        String vehiculo = """
                {"placa":"%s","marca":"M","modelo":"M","tipo":"Camión","anio":2020,"color":"Rojo",
                 "estado":"Disponible","kilometraje":0,"capacidad":0}
                """.formatted(placa);
        String creado = mockMvc.perform(post("/api/vehiculos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiculo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    @Test
    void mantenimientoPuedeVerElReportePrincipalConLosTotalesCorrectos() throws Exception {
        String tokenAdmin = tokenPara("admincreavehiculoreporte", Rol.ADMINISTRADOR);
        Long vehiculoId = crearVehiculo(tokenAdmin, "MNT-REP-01");
        String token = tokenPara("mantenimientoreporteobtener", Rol.MANTENIMIENTO);

        String preventivo = """
                {"vehiculoId":%d,"tipo":"Preventivo","fecha":"2026-08-01","kilometraje":1000,
                 "costo":100.00,"proximoServicio":"2026-11-01","descripcion":"Revisión"}
                """.formatted(vehiculoId);
        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preventivo))
                .andExpect(status().isCreated());

        String correctivo = """
                {"vehiculoId":%d,"tipo":"Correctivo","fecha":"2026-08-05","kilometraje":1050,
                 "costo":50.00,"descripcion":"Reparación de frenos"}
                """.formatted(vehiculoId);
        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctivo))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/mantenimientos/reportes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costosPorVehiculo[?(@.placa == 'MNT-REP-01')].cantidad").value(2))
                .andExpect(jsonPath("$.costosPorVehiculo[?(@.placa == 'MNT-REP-01')].costo").value(150.0))
                .andReturn().getResponse().getContentAsString();

        // preventivos/correctivos/costoTotal son globales (toda la base de
        // pruebas, compartida con otras clases) - se comprueban "al menos"
        // en vez de un valor exacto.
        var reporte = objectMapper.readTree(body);
        assertThat(reporte.get("preventivos").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(reporte.get("correctivos").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(reporte.get("costoTotal").asDouble()).isGreaterThanOrEqualTo(150.0);
    }

    @Test
    void administradorPuedeVerElReportePrincipal() throws Exception {
        String token = tokenPara("adminreporteobtener", Rol.ADMINISTRADOR);
        mockMvc.perform(get("/api/mantenimientos/reportes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void coordinadorNoPuedeVerElReportePrincipalDeMantenimientos() throws Exception {
        String token = tokenPara("coordinadorreporteobtener", Rol.COORDINADOR);
        mockMvc.perform(get("/api/mantenimientos/reportes").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
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
