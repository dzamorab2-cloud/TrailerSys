package com.trailersys.backend.viaje;

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
class ViajeControllerTest {

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

    private Long crearConductor(String identificacion) throws Exception {
        String conductor = """
                {"nombres":"Conductor Viaje Test","identificacion":"%s","telefono":"0999999999",
                 "licenciaNumero":"LIC-1","licenciaCategoria":"Tipo E",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible"}
                """.formatted(identificacion);
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
                {"nombre":"Cliente Viaje Test","identificacion":"%s","estado":"Activo",
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

    @Test
    void listarSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/viajes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearViajeConRutaDevuelveDatosDeRelacionesYRuta() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-0001");
        Long conductorId = crearConductor("CI-VJE-0001");
        Long clienteId = crearCliente("CI-VJE-CLI-0001");

        String viaje = """
                {
                  "vehiculoId": %d,
                  "conductorId": %d,
                  "clienteId": %d,
                  "origen": "Quito, Ecuador",
                  "destino": "Guayaquil, Ecuador",
                  "fechaSalida": "2026-08-15T08:30:00",
                  "estado": "Programado",
                  "ruta": {
                    "origenLat": -0.2201641,
                    "origenLng": -78.5123274,
                    "destinoLat": -2.1894,
                    "destinoLng": -79.8891,
                    "distanciaKm": 424.5,
                    "duracionMin": 372.6,
                    "path": [{"lat": -0.22, "lng": -78.51}, {"lat": -2.18, "lng": -79.88}]
                  }
                }
                """.formatted(vehiculoId, conductorId, clienteId);

        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehiculoPlaca").value("VJE-0001"))
                .andExpect(jsonPath("$.conductorNombres").value("Conductor Viaje Test"))
                .andExpect(jsonPath("$.clienteNombre").value("Cliente Viaje Test"))
                .andExpect(jsonPath("$.cargaId").doesNotExist())
                .andExpect(jsonPath("$.ruta.distanciaKm").value(424.5))
                .andExpect(jsonPath("$.ruta.path.length()").value(2));
    }

    @Test
    void crearViajeSinRutaDevuelveRutaNula() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-0002");
        Long conductorId = crearConductor("CI-VJE-0002");
        Long clienteId = crearCliente("CI-VJE-CLI-0002");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:30:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId);

        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruta").doesNotExist());
    }

    @Test
    void crearViajeConVehiculoInexistenteDevuelveNoEncontrado() throws Exception {
        Long conductorId = crearConductor("CI-VJE-0003");
        Long clienteId = crearCliente("CI-VJE-CLI-0003");

        String viaje = """
                {"vehiculoId":999999,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:30:00","estado":"Programado"}
                """.formatted(conductorId, clienteId);

        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isNotFound());
    }

    @Test
    void listadoIncluyeDatosDenormalizadosDeLasRelaciones() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-LIST");
        Long conductorId = crearConductor("CI-VJE-LIST");
        Long clienteId = crearCliente("CI-VJE-LIST-CLI");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"Origen Listado","destino":"Destino Listado",
                 "fechaSalida":"2026-08-15T08:30:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId);

        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated());

        String listado = mockMvc.perform(get("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String placaEncontrada = null;
        for (JsonNode nodo : objectMapper.readTree(listado)) {
            if ("Origen Listado".equals(nodo.get("origen").asText())) {
                placaEncontrada = nodo.get("vehiculoPlaca").asText();
                break;
            }
        }
        assertThat(placaEncontrada).isEqualTo("VJE-LIST");
    }

    @Test
    void conductorPuedeConsultarPeroNoCrear() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("conductorviajetest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "conductorviajetest", passwordEncoder.encode("clave1234"), "Conductor Test", null, Rol.CONDUCTOR));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"conductorviajetest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenConductor = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/viajes")
                        .header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isOk());

        Long vehiculoId = crearVehiculo("VJE-0004");
        Long conductorId = crearConductor("CI-VJE-0004");
        Long clienteId = crearCliente("CI-VJE-CLI-0004");
        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:30:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId);

        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isForbidden());
    }
}
