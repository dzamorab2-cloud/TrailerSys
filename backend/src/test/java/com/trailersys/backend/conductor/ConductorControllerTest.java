package com.trailersys.backend.conductor;

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
class ConductorControllerTest {

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

    private Long crearVehiculoDePrueba(String placa) throws Exception {
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
        mockMvc.perform(get("/api/conductores"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearConVehiculoAsignadoDevuelvePlacaEnLaRespuesta() throws Exception {
        Long vehiculoId = crearVehiculoDePrueba("CND-0001");

        String conductor = """
                {
                  "nombres": "Conductor Prueba",
                  "identificacion": "CI-0001",
                  "telefono": "0999999999",
                  "correo": "conductor@trailersys.test",
                  "licenciaNumero": "LIC-999",
                  "licenciaCategoria": "Tipo E",
                  "licenciaVencimiento": "2030-01-01",
                  "estado": "Disponible",
                  "vehiculoId": %d,
                  "observaciones": ""
                }
                """.formatted(vehiculoId);

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehiculoId").value(vehiculoId))
                .andExpect(jsonPath("$.vehiculoPlaca").value("CND-0001"))
                .andExpect(jsonPath("$.licenciaVencida").value(false));
    }

    /**
     * A diferencia del test anterior (que lee vehiculoPlaca de la respuesta
     * de creacion, donde la entidad se resuelve directo sin proxy), este
     * ejercita el listado: repository.findAll() + mapeo a DTO fuera de la
     * transaccion de lectura, que es donde vehiculo.getPlaca() fallaria si
     * la relacion fuera LAZY (ver comentario en Conductor.vehiculo).
     */
    @Test
    void listadoIncluyeVehiculoPlacaDelConductorAsignado() throws Exception {
        Long vehiculoId = crearVehiculoDePrueba("CND-LIST");

        String conductor = """
                {"nombres":"Conductor Listado","identificacion":"CI-LISTVEH","telefono":"0999999999",
                 "licenciaNumero":"LIC-500","licenciaCategoria":"Tipo E",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible","vehiculoId":%d}
                """.formatted(vehiculoId);

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated());

        String listado = mockMvc.perform(get("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String placaEncontrada = null;
        for (JsonNode nodo : objectMapper.readTree(listado)) {
            if ("CI-LISTVEH".equals(nodo.get("identificacion").asText())) {
                placaEncontrada = nodo.get("vehiculoPlaca").asText();
                break;
            }
        }
        assertThat(placaEncontrada).isEqualTo("CND-LIST");
    }

    @Test
    void crearConVehiculoInexistenteDevuelveNoEncontrado() throws Exception {
        String conductor = """
                {
                  "nombres": "Conductor Prueba 2",
                  "identificacion": "CI-0002",
                  "telefono": "0999999999",
                  "licenciaNumero": "LIC-998",
                  "licenciaCategoria": "Tipo E",
                  "licenciaVencimiento": "2030-01-01",
                  "estado": "Disponible",
                  "vehiculoId": 999999
                }
                """;

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearConCorreoInvalidoDevuelve400() throws Exception {
        String conductor = """
                {
                  "nombres": "Conductor Prueba 3",
                  "identificacion": "CI-0003",
                  "telefono": "0999999999",
                  "correo": "correo-invalido",
                  "licenciaNumero": "LIC-997",
                  "licenciaCategoria": "Tipo E",
                  "licenciaVencimiento": "2030-01-01",
                  "estado": "Disponible"
                }
                """;

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearConIdentificacionDuplicadaDevuelveConflicto() throws Exception {
        String conductor = """
                {"nombres":"Uno","identificacion":"CI-DUP","telefono":"0999999999",
                 "licenciaNumero":"LIC-1","licenciaCategoria":"Tipo B",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible"}
                """;

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isConflict());
    }

    @Test
    void licenciaVencidaSeMarcaComoTrueEnLaRespuesta() throws Exception {
        String conductor = """
                {"nombres":"Con Licencia Vencida","identificacion":"CI-VENC","telefono":"0999999999",
                 "licenciaNumero":"LIC-2","licenciaCategoria":"Tipo B",
                 "licenciaVencimiento":"2020-01-01","estado":"Disponible"}
                """;

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licenciaVencida").value(true));
    }

    @Test
    void rolSinAccesoAModuloDevuelveProhibido() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("mantenimientotest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "mantenimientotest", passwordEncoder.encode("clave1234"), "Mantenimiento Test", null, Rol.MANTENIMIENTO));
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mantenimientotest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenMantenimiento = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/conductores")
                        .header("Authorization", "Bearer " + tokenMantenimiento))
                .andExpect(status().isForbidden());
    }

    @Test
    void listadoIncluyeConductoresCreados() throws Exception {
        String conductor = """
                {"nombres":"Listado Test","identificacion":"CI-LIST","telefono":"0999999999",
                 "licenciaNumero":"LIC-3","licenciaCategoria":"Tipo B",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible"}
                """;

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated());

        String listado = mockMvc.perform(get("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean encontrado = false;
        for (JsonNode nodo : objectMapper.readTree(listado)) {
            if ("CI-LIST".equals(nodo.get("identificacion").asText())) {
                encontrado = true;
                break;
            }
        }
        assertThat(encontrado).isTrue();
    }

    /**
     * fechaNacimiento es opcional y se guarda tal cual; edad se calcula al
     * leer (Period.between contra la fecha actual), nunca se persiste como
     * numero aparte para que no quede desactualizada.
     */
    @Test
    void fechaNacimientoViajaBienYLaEdadSeCalculaAlLeer() throws Exception {
        java.time.LocalDate hoy = java.time.LocalDate.now();
        java.time.LocalDate nacimiento = hoy.minusYears(30).minusDays(1);
        String conductor = """
                {"nombres":"Con Fecha Nacimiento","identificacion":"CI-EDAD","telefono":"0999999999",
                 "licenciaNumero":"LIC-EDAD","licenciaCategoria":"Tipo B",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible","fechaNacimiento":"%s"}
                """.formatted(nacimiento);

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaNacimiento").value(nacimiento.toString()))
                .andExpect(jsonPath("$.edad").value(30));
    }

    @Test
    void sinFechaNacimientoLaEdadEsNula() throws Exception {
        String conductor = """
                {"nombres":"Sin Fecha Nacimiento","identificacion":"CI-SINEDAD","telefono":"0999999999",
                 "licenciaNumero":"LIC-SINEDAD","licenciaCategoria":"Tipo B",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible"}
                """;

        mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaNacimiento").doesNotExist())
                .andExpect(jsonPath("$.edad").doesNotExist());
    }
}
