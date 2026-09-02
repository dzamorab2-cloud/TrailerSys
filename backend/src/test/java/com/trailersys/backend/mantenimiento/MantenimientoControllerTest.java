package com.trailersys.backend.mantenimiento;

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
class MantenimientoControllerTest {

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

    private Long crearVehiculo(String placa) throws Exception {
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

    @Test
    void listarSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/mantenimientos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearConsultarYListarPorVehiculo() throws Exception {
        Long vehiculoId = crearVehiculo("MNT-CTRL-01");

        String mantenimiento = """
                {"vehiculoId":%d,"tipo":"Preventivo","fecha":"2026-08-01","kilometraje":1000,
                 "costo":85.50,"proximoServicio":"2026-11-01","descripcion":"Revisión general"}
                """.formatted(vehiculoId);

        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mantenimiento))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehiculoPlaca").value("MNT-CTRL-01"))
                .andExpect(jsonPath("$.proximoServicioVencido").value(false));

        String listado = mockMvc.perform(get("/api/mantenimientos")
                        .param("vehiculoId", String.valueOf(vehiculoId))
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(listado)).hasSize(1);
    }

    @Test
    void preventivoSinProximaFechaLaCalculaAUnMes() throws Exception {
        Long vehiculoId = crearVehiculo("MNT-MENSUAL-01");
        String mantenimiento = """
                {"vehiculoId":%d,"tipo":"Preventivo","fecha":"2026-08-24","kilometraje":1500,
                 "costo":50.0,"descripcion":"Mantenimiento preventivo mensual"}
                """.formatted(vehiculoId);

        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mantenimiento))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proximoServicio").value("2026-09-24"));
    }

    @Test
    void proximoServicioAnteriorALaFechaDevuelve400() throws Exception {
        Long vehiculoId = crearVehiculo("MNT-CTRL-02");

        String mantenimiento = """
                {"vehiculoId":%d,"tipo":"Preventivo","fecha":"2026-08-01","kilometraje":1000,
                 "costo":85.50,"proximoServicio":"2026-07-01","descripcion":"Fechas invertidas"}
                """.formatted(vehiculoId);

        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mantenimiento))
                .andExpect(status().isBadRequest());
    }

    @Test
    void proximoServicioVencidoSeMarcaComoTrue() throws Exception {
        Long vehiculoId = crearVehiculo("MNT-CTRL-03");

        String mantenimiento = """
                {"vehiculoId":%d,"tipo":"Correctivo","fecha":"2020-01-01","kilometraje":1000,
                 "costo":10.0,"proximoServicio":"2020-06-01","descripcion":"Ya vencido"}
                """.formatted(vehiculoId);

        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mantenimiento))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proximoServicioVencido").value(true));
    }

    @Test
    void crearConVehiculoInexistenteDevuelveNoEncontrado() throws Exception {
        String mantenimiento = """
                {"vehiculoId":999999,"tipo":"Preventivo","fecha":"2026-08-01","kilometraje":1000,
                 "costo":10.0,"descripcion":"Sin vehiculo"}
                """;

        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mantenimiento))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtrarPorTipoConLaEtiquetaQueUsaElFrontendFunciona() throws Exception {
        Long vehiculoId = crearVehiculo("MNT-TIPO-01");
        String mantenimiento = """
                {"vehiculoId":%d,"tipo":"Preventivo","fecha":"2026-08-01","kilometraje":1000,
                 "costo":85.50,"descripcion":"Filtro por tipo"}
                """.formatted(vehiculoId);
        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mantenimiento))
                .andExpect(status().isCreated());

        // "Preventivo" es la etiqueta que manda el frontend (js/mantenimientos.js),
        // no el nombre interno del enum ("PREVENTIVO"): antes de este fix,
        // el binder por defecto de Spring solo aceptaba el nombre exacto del
        // enum y esta llamada devolvia 400.
        mockMvc.perform(get("/api/mantenimientos")
                        .param("tipo", "Preventivo")
                        .param("vehiculoId", String.valueOf(vehiculoId))
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("Preventivo"));
    }

    @Test
    void rolSinAccesoAModuloDevuelveProhibido() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("coordinadormanttest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "coordinadormanttest", passwordEncoder.encode("clave1234"), "Coordinador Test", null, Rol.COORDINADOR));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"coordinadormanttest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenCoordinador = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenCoordinador))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolMantenimientoPuedeGestionar() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("mantenimientorolctrl").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "mantenimientorolctrl", passwordEncoder.encode("clave1234"), "Mantenimiento Test", null, Rol.MANTENIMIENTO));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mantenimientorolctrl\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenMantenimiento = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenMantenimiento))
                .andExpect(status().isOk());
    }
}
