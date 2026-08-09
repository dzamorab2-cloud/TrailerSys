package com.trailersys.backend.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class SeguimientoControllerTest {

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

    private Long crearVehiculo(String placa, String estado) throws Exception {
        String vehiculo = """
                {"placa":"%s","marca":"M","modelo":"M","tipo":"Camión","anio":2020,"color":"Rojo",
                 "estado":"%s","kilometraje":0,"capacidad":0}
                """.formatted(placa, estado);
        String creado = mockMvc.perform(post("/api/vehiculos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiculo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private Long crearConductor(String identificacion, String licenciaVencimiento) throws Exception {
        String conductor = """
                {"nombres":"Conductor Seguimiento Test","identificacion":"%s","telefono":"0999999999",
                 "licenciaNumero":"LIC-1","licenciaCategoria":"Tipo E",
                 "licenciaVencimiento":"%s","estado":"Disponible"}
                """.formatted(identificacion, licenciaVencimiento);
        String creado = mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private Long crearCliente(String identificacion) throws Exception {
        String cliente = """
                {"nombre":"Cliente Seguimiento Test","identificacion":"%s","estado":"Activo",
                 "telefono":"0999999999","direccion":"Direccion"}
                """.formatted(identificacion);
        String creado = mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private Long crearViaje(Long vehiculoId, Long conductorId, Long clienteId, String origen, String destino,
                             String fechaSalida, String estado) throws Exception {
        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"%s","destino":"%s","fechaSalida":"%s","estado":"%s"}
                """.formatted(vehiculoId, conductorId, clienteId, origen, destino, fechaSalida, estado);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    @Test
    void listarEventosSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/seguimiento/eventos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearConsultarYEliminarEvento() throws Exception {
        Long vehiculoId = crearVehiculo("SEG-EVT-01", "Disponible");
        Long conductorId = crearConductor("CI-SEG-EVT-01", "2030-01-01");
        Long clienteId = crearCliente("CI-SEG-EVT-CLI-01");
        Long viajeId = crearViaje(vehiculoId, conductorId, clienteId, "Origen Evento", "Destino Evento",
                "2026-08-15T08:00:00", "En Curso");

        String evento = """
                {"viajeId":%d,"fechaHora":"2026-08-15T08:05:00","evento":"Salida",
                 "ubicacion":"Terminal de prueba","observacion":"Todo en orden"}
                """.formatted(viajeId);

        String creado = mockMvc.perform(post("/api/seguimiento/eventos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evento))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehiculoPlaca").value("SEG-EVT-01"))
                .andExpect(jsonPath("$.viajeOrigen").value("Origen Evento"))
                .andReturn().getResponse().getContentAsString();

        Long eventoId = objectMapper.readTree(creado).get("id").asLong();

        String listado = mockMvc.perform(get("/api/seguimiento/eventos")
                        .param("viajeId", String.valueOf(viajeId))
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(listado)).hasSize(1);

        mockMvc.perform(delete("/api/seguimiento/eventos/" + eventoId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        String listadoTrasEliminar = mockMvc.perform(get("/api/seguimiento/eventos")
                        .param("viajeId", String.valueOf(viajeId))
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(listadoTrasEliminar)).isEmpty();
    }

    @Test
    void crearEventoConViajeInexistenteDevuelveNoEncontrado() throws Exception {
        String evento = """
                {"viajeId":999999,"fechaHora":"2026-08-15T08:05:00","evento":"Salida","ubicacion":"X"}
                """;

        mockMvc.perform(post("/api/seguimiento/eventos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evento))
                .andExpect(status().isNotFound());
    }

    @Test
    void alertasDetectanLicenciaVencidaYVehiculoEnMantenimiento() throws Exception {
        Long vehiculoId = crearVehiculo("SEG-ALERT-01", "Mantenimiento");
        Long conductorId = crearConductor("CI-SEG-ALERT-01", "2020-01-01");
        Long clienteId = crearCliente("CI-SEG-ALERT-CLI-01");
        crearViaje(vehiculoId, conductorId, clienteId, "Origen Alerta", "Destino Alerta",
                "2026-08-20T08:00:00", "En Curso");

        String alertas = mockMvc.perform(get("/api/seguimiento/alertas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean tieneLicenciaVencida = false;
        boolean tieneVehiculoMantenimiento = false;
        for (JsonNode alerta : objectMapper.readTree(alertas)) {
            String texto = alerta.get("texto").asText();
            if (texto.contains("licencia") && texto.contains("Conductor Seguimiento Test")) {
                tieneLicenciaVencida = true;
            }
            if (texto.contains("SEG-ALERT-01") && texto.contains("Mantenimiento")) {
                tieneVehiculoMantenimiento = true;
            }
        }

        assertThat(tieneLicenciaVencida).isTrue();
        assertThat(tieneVehiculoMantenimiento).isTrue();
    }

    @Test
    void alertasDetectanViajeEnCursoSinRuta() throws Exception {
        Long vehiculoId = crearVehiculo("SEG-ALERT-02", "Disponible");
        Long conductorId = crearConductor("CI-SEG-ALERT-02", "2030-01-01");
        Long clienteId = crearCliente("CI-SEG-ALERT-CLI-02");
        crearViaje(vehiculoId, conductorId, clienteId, "Origen Sin Ruta", "Destino Sin Ruta",
                "2026-08-20T08:00:00", "En Curso");

        String alertas = mockMvc.perform(get("/api/seguimiento/alertas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean encontrada = false;
        for (JsonNode alerta : objectMapper.readTree(alertas)) {
            if (alerta.get("texto").asText().contains("Origen Sin Ruta")) {
                encontrada = true;
            }
        }
        assertThat(encontrada).isTrue();
    }

    @Test
    void rolSinAccesoAModuloDevuelveProhibido() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("supervisorseguimientotest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "supervisorseguimientotest", passwordEncoder.encode("clave1234"), "Supervisor Test", null, Rol.SUPERVISOR));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"supervisorseguimientotest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenSupervisor = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/seguimiento/eventos")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isForbidden());
    }
}
