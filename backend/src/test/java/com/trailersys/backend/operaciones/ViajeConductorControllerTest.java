package com.trailersys.backend.operaciones;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Cubre el autoservicio del rol CONDUCTOR (Dashboard personal + "Mis
 * viajes"): que solo vea SUS PROPIOS viajes, incluso si adivina el id de un
 * viaje de otro conductor, calcado de PedidoClienteControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ViajeConductorControllerTest {

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
        if (usuarioRepository.findByUsernameIgnoreCase("admintestoperaciones").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "admintestoperaciones", passwordEncoder.encode("clave1234"), "Admin Test", null, Rol.ADMINISTRADOR));
        }
        tokenAdmin = login("admintestoperaciones", "clave1234");
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String tokenPara(String username, Rol rol) throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase(username).isEmpty()) {
            usuarioRepository.save(new Usuario(username, passwordEncoder.encode("clave1234"), "Usuario " + username, null, rol));
        }
        return login(username, "clave1234");
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

    private Long crearConductor(String identificacion) throws Exception {
        String conductor = """
                {"nombres":"Conductor Operaciones Test","identificacion":"%s","telefono":"0999999999",
                 "licenciaNumero":"LIC-%s","licenciaCategoria":"Tipo E",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible","fechaNacimiento":"1990-04-20"}
                """.formatted(identificacion, identificacion);
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
                {"nombre":"Cliente %s","identificacion":"%s","estado":"Activo",
                 "telefono":"0999999999","direccion":"Direccion de prueba"}
                """.formatted(identificacion, identificacion);
        String creado = mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    /** Crea (via el endpoint de administracion de usuarios) una cuenta CONDUCTOR vinculada a conductorId, y su token. */
    private String crearUsuarioConductorYToken(String username, Long conductorId) throws Exception {
        String usuario = """
                {"username":"%s","password":"clave1234","nombre":"Usuario %s","rol":"CONDUCTOR","activo":true,"conductorId":%d}
                """.formatted(username, username, conductorId);
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuario))
                .andExpect(status().isCreated());
        return login(username, "clave1234");
    }

    private Long crearViaje(Long vehiculoId, Long conductorId, Long clienteId, String estado, String fechaSalida) throws Exception {
        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"Quito","destino":"Guayaquil","fechaSalida":"%s","estado":"%s"}
                """.formatted(vehiculoId, conductorId, clienteId, fechaSalida, estado);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    @Test
    void listarSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/mis-viajes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unRolQueNoEsConductorNoPuedeAccederAMisViajes() throws Exception {
        // Un usuario CLIENTE (sin conductor asociado) no tiene "Mis viajes"
        // en su rol; el endpoint es exclusivo de CONDUCTOR.
        String tokenCliente = tokenPara("clienteoperacionesrol", Rol.CLIENTE);
        mockMvc.perform(get("/api/mis-viajes").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearUsuarioConRolConductorSinConductorIdDaError() throws Exception {
        String usuario = """
                {"username":"conductorsinvinculo","password":"clave1234","nombre":"Sin Vinculo","rol":"CONDUCTOR","activo":true}
                """;
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuario))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conductorVeSusPropiosViajesYNoLosDeOtroConductorNiAdivinandoElId() throws Exception {
        Long clienteId = crearCliente("CI-OP-CLI");
        Long vehiculoA = crearVehiculo("OPA-0001");
        Long vehiculoB = crearVehiculo("OPB-0002");
        Long conductorAId = crearConductor("CI-OP-CONDA");
        Long conductorBId = crearConductor("CI-OP-CONDB");
        String tokenA = crearUsuarioConductorYToken("conductoropA", conductorAId);
        String tokenB = crearUsuarioConductorYToken("conductoropB", conductorBId);

        Long viajeDeA = crearViaje(vehiculoA, conductorAId, clienteId, "Programado", "2026-09-15T08:00:00");
        crearViaje(vehiculoB, conductorBId, clienteId, "Programado", "2026-09-16T08:00:00");

        String listadoA = mockMvc.perform(get("/api/mis-viajes").header("Authorization", "Bearer " + tokenA))
                        .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + viajeDeA + ")]").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(listadoA).doesNotContain("\"vehiculoPlaca\":\"OPB-0002\"");

        mockMvc.perform(get("/api/mis-viajes/" + viajeDeA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/mis-viajes/" + viajeDeA).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(viajeDeA));
    }

    @Test
    void conductorConsultaSuPerfilResumenYViajeActivo() throws Exception {
        Long clienteId = crearCliente("CI-OP-PERF");
        Long vehiculoId = crearVehiculo("OPP-0003");
        Long conductorId = crearConductor("CI-OP-CONDPERF");
        String tokenConductor = crearUsuarioConductorYToken("conductoropperf", conductorId);

        // Sin viajes todavia: el resumen esta en ceros y no hay viaje activo.
        mockMvc.perform(get("/api/mis-viajes/resumen").header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalViajes").value(0));
        mockMvc.perform(get("/api/mis-viajes/activo").header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isNoContent());

        crearViaje(vehiculoId, conductorId, clienteId, "Programado", "2026-09-20T08:00:00");

        mockMvc.perform(get("/api/mis-viajes/resumen").header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalViajes").value(1))
                .andExpect(jsonPath("$.viajesProgramados").value(1));

        mockMvc.perform(get("/api/mis-viajes/activo").header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Programado"));

        mockMvc.perform(get("/api/mis-viajes/perfil").header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificacion").value("CI-OP-CONDPERF"))
                .andExpect(jsonPath("$.edad").isNumber())
                .andExpect(jsonPath("$.vehiculoPlaca").doesNotExist());
    }

    @Test
    void conductorActualizaSuPropiaFotoDePerfil() throws Exception {
        Long conductorId = crearConductor("CI-OP-FOTO");
        String tokenConductor = crearUsuarioConductorYToken("conductoropfoto", conductorId);

        mockMvc.perform(put("/api/mis-viajes/perfil/foto")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foto\":\"data:image/png;base64,abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").value("data:image/png;base64,abc123"));

        mockMvc.perform(get("/api/mis-viajes/perfil").header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").value("data:image/png;base64,abc123"));
    }
}
