package com.trailersys.backend.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        // Crear el viaje directo en "En Curso" ya dispara un evento de Salida
        // automatico (ver ViajeService.registrarSalidaAutomatica).
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
        assertThat(objectMapper.readTree(listado)).hasSize(2);

        mockMvc.perform(delete("/api/seguimiento/eventos/" + eventoId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());

        String listadoTrasEliminar = mockMvc.perform(get("/api/seguimiento/eventos")
                        .param("viajeId", String.valueOf(viajeId))
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Queda el evento de Salida automatico; solo se elimino el manual.
        assertThat(objectMapper.readTree(listadoTrasEliminar)).hasSize(1);
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
        // El vehiculo se crea Disponible (ValidarDisponibilidad exige ese
        // estado para poder asignarlo a un viaje) y recien despues de que el
        // viaje ya esta En Curso se lleva a Mantenimiento, simulando el caso
        // real que la alerta detecta: un vehiculo que quedo en mantenimiento
        // mientras seguia con un viaje activo asignado.
        Long vehiculoId = crearVehiculo("SEG-ALERT-01", "Disponible");
        Long conductorId = crearConductor("CI-SEG-ALERT-01", "2020-01-01");
        Long clienteId = crearCliente("CI-SEG-ALERT-CLI-01");
        crearViaje(vehiculoId, conductorId, clienteId, "Origen Alerta", "Destino Alerta",
                "2026-08-20T08:00:00", "En Curso");

        String vehiculoEnMantenimiento = """
                {"placa":"SEG-ALERT-01","marca":"M","modelo":"M","tipo":"Camión","anio":2020,"color":"Rojo",
                 "estado":"Mantenimiento","kilometraje":0,"capacidad":0}
                """;
        mockMvc.perform(put("/api/vehiculos/" + vehiculoId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vehiculoEnMantenimiento))
                .andExpect(status().isOk());

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
    void alertasDetectanMantenimientoVencido() throws Exception {
        Long vehiculoId = crearVehiculo("SEG-ALERT-03", "Disponible");

        String mantenimiento = """
                {"vehiculoId":%d,"tipo":"Correctivo","fecha":"2020-01-01","kilometraje":1000,
                 "costo":10.0,"proximoServicio":"2020-06-01","descripcion":"Vencido para la alerta"}
                """.formatted(vehiculoId);

        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mantenimiento))
                .andExpect(status().isCreated());

        String alertas = mockMvc.perform(get("/api/seguimiento/alertas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean encontrada = false;
        for (JsonNode alerta : objectMapper.readTree(alertas)) {
            if (alerta.get("texto").asText().contains("SEG-ALERT-03")) {
                encontrada = true;
            }
        }
        assertThat(encontrada).isTrue();
    }

    @Test
    void alertaDeEntregaPendienteApareceTrasConfirmarYDesapareceTrasValidar() throws Exception {
        Long vehiculoId = crearVehiculo("SEG-ALERT-04", "Disponible");
        Long conductorId = crearConductor("CI-SEG-ALERT-04", "2030-01-01");
        Long clienteId = crearCliente("CI-SEG-ALERT-CLI-04");
        Long viajeId = crearViaje(vehiculoId, conductorId, clienteId, "Origen Pendiente", "Destino Pendiente",
                "2026-08-15T08:00:00", "En Curso");

        String tokenConductor = tokenPara("conductoralertatest", Rol.CONDUCTOR);
        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        String alertasTrasConfirmar = mockMvc.perform(get("/api/seguimiento/alertas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        boolean aparecePendiente = false;
        for (JsonNode alerta : objectMapper.readTree(alertasTrasConfirmar)) {
            if (alerta.get("texto").asText().contains("Origen Pendiente")) {
                aparecePendiente = true;
                assertThat(alerta.get("nivel").asText()).isEqualTo("info");
            }
        }
        assertThat(aparecePendiente).isTrue();

        String tokenSupervisor = tokenPara("supervisoralertatest", Rol.SUPERVISOR);
        mockMvc.perform(post("/api/viajes/" + viajeId + "/validar-entrega")
                        .header("Authorization", "Bearer " + tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        String alertasTrasValidar = mockMvc.perform(get("/api/seguimiento/alertas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        boolean siguePendiente = false;
        for (JsonNode alerta : objectMapper.readTree(alertasTrasValidar)) {
            if (alerta.get("texto").asText().contains("Origen Pendiente")) {
                siguePendiente = true;
            }
        }
        assertThat(siguePendiente).isFalse();
    }

    @Test
    void supervisorPuedeConsultarPeroNoCrearEventos() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("supervisorseguimientotest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "supervisorseguimientotest", passwordEncoder.encode("clave1234"), "Supervisor Test", null, Rol.SUPERVISOR));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"supervisorseguimientotest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenSupervisor = objectMapper.readTree(body).get("token").asText();

        // Puede leer: necesita ver el detalle del viaje para validar una entrega.
        mockMvc.perform(get("/api/seguimiento/eventos")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isOk());

        // No puede escribir: no gestiona eventos manuales, solo consulta.
        // El body debe ser valido para que la validacion no enmascare el 403.
        Long vehiculoId = crearVehiculo("SEG-SUP-01", "Disponible");
        Long conductorId = crearConductor("CI-SEG-SUP-01", "2030-01-01");
        Long clienteId = crearCliente("CI-SEG-SUP-CLI-01");
        Long viajeId = crearViaje(vehiculoId, conductorId, clienteId, "Origen Supervisor", "Destino Supervisor",
                "2026-08-15T08:00:00", "En Curso");
        String evento = """
                {"viajeId":%d,"fechaHora":"2026-08-15T08:05:00","evento":"Salida",
                 "ubicacion":"Terminal de prueba","observacion":"Todo en orden"}
                """.formatted(viajeId);

        mockMvc.perform(post("/api/seguimiento/eventos")
                        .header("Authorization", "Bearer " + tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evento))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolSinAccesoAModuloDevuelveProhibido() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("mantenimientoseguimientotest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "mantenimientoseguimientotest", passwordEncoder.encode("clave1234"), "Mantenimiento Test", null, Rol.MANTENIMIENTO));
        }

        String bodyMantenimiento = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mantenimientoseguimientotest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenMantenimiento = objectMapper.readTree(bodyMantenimiento).get("token").asText();

        mockMvc.perform(get("/api/seguimiento/eventos")
                        .header("Authorization", "Bearer " + tokenMantenimiento))
                .andExpect(status().isForbidden());
    }
}
