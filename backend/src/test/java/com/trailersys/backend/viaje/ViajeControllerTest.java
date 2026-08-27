package com.trailersys.backend.viaje;

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

    private Long crearCarga(Long clienteId, String sufijo) throws Exception {
        String carga = """
                {"descripcion":"Carga %s","clienteId":%d,"tipo":"General","peso":100,
                 "origen":"Origen %s","destino":"Destino %s","estado":"Pendiente"}
                """.formatted(sufijo, clienteId, sufijo, sufijo);
        String creada = mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creada).get("id").asLong();
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

    private Long crearViajeEnCurso(String sufijo) throws Exception {
        Long vehiculoId = crearVehiculo("VJE-EC-" + sufijo);
        Long conductorId = crearConductor("CI-VJE-EC-" + sufijo);
        Long clienteId = crearCliente("CI-VJE-EC-CLI-" + sufijo);
        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"Quito","destino":"Guayaquil",
                 "fechaSalida":"2026-08-15T08:30:00","estado":"En Curso"}
                """.formatted(vehiculoId, conductorId, clienteId);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
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

    @Test
    void crearViajeConCargaDeOtroClienteDaError() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-CRG-01");
        Long conductorId = crearConductor("CI-VJE-CRG-01");
        Long clienteViaje = crearCliente("CI-VJE-CRG-CLI-01");
        Long clienteCarga = crearCliente("CI-VJE-CRG-CLI-02");
        Long cargaId = crearCarga(clienteCarga, "CRG01");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteViaje, cargaId);

        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearViajeConCargaYaAsignadaAOtroViajeActivoDaConflicto() throws Exception {
        Long clienteId = crearCliente("CI-VJE-CRG-CLI-03");
        Long cargaId = crearCarga(clienteId, "CRG02");

        Long vehiculo1 = crearVehiculo("VJE-CRG-02");
        Long conductor1 = crearConductor("CI-VJE-CRG-02");
        String viaje1 = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculo1, conductor1, clienteId, cargaId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje1))
                .andExpect(status().isCreated());

        Long vehiculo2 = crearVehiculo("VJE-CRG-03");
        Long conductor2 = crearConductor("CI-VJE-CRG-03");
        String viaje2 = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"C","destino":"D","fechaSalida":"2026-08-16T08:00:00","estado":"Programado"}
                """.formatted(vehiculo2, conductor2, clienteId, cargaId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje2))
                .andExpect(status().isConflict());
    }

    @Test
    void crearViajeEnCursoConCargaSincronizaEstadoATransito() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-CRG-04");
        Long conductorId = crearConductor("CI-VJE-CRG-04");
        Long clienteId = crearCliente("CI-VJE-CRG-CLI-04");
        Long cargaId = crearCarga(clienteId, "CRG04");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"En Curso"}
                """.formatted(vehiculoId, conductorId, clienteId, cargaId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/cargas/" + cargaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("En Tránsito"));
    }

    @Test
    void confirmarEntregaSincronizaCargaAEntregada() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-CRG-05");
        Long conductorId = crearConductor("CI-VJE-CRG-05");
        Long clienteId = crearCliente("CI-VJE-CRG-CLI-05");
        Long cargaId = crearCarga(clienteId, "CRG05");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"En Curso"}
                """.formatted(vehiculoId, conductorId, clienteId, cargaId);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long viajeId = objectMapper.readTree(creado).get("id").asLong();

        String tokenConductor = tokenPara("conductorcarga1", Rol.CONDUCTOR);
        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cargas/" + cargaId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Entregada"));
    }

    @Test
    void crearViajeConVehiculoYaAsignadoAOtroViajeActivoDaConflicto() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-DISP-01");
        Long conductor1 = crearConductor("CI-VJE-DISP-01");
        Long conductor2 = crearConductor("CI-VJE-DISP-02");
        Long clienteId = crearCliente("CI-VJE-DISP-CLI-01");

        String viaje1 = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoId, conductor1, clienteId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje1))
                .andExpect(status().isCreated());

        String viaje2 = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"C","destino":"D","fechaSalida":"2026-08-16T08:00:00","estado":"Programado"}
                """.formatted(vehiculoId, conductor2, clienteId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje2))
                .andExpect(status().isConflict());
    }

    @Test
    void crearViajeConConductorYaAsignadoAOtroViajeActivoDaConflicto() throws Exception {
        Long vehiculo1 = crearVehiculo("VJE-DISP-02");
        Long vehiculo2 = crearVehiculo("VJE-DISP-03");
        Long conductorId = crearConductor("CI-VJE-DISP-03");
        Long clienteId = crearCliente("CI-VJE-DISP-CLI-02");

        String viaje1 = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"En Curso"}
                """.formatted(vehiculo1, conductorId, clienteId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje1))
                .andExpect(status().isCreated());

        String viaje2 = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"C","destino":"D","fechaSalida":"2026-08-16T08:00:00","estado":"Programado"}
                """.formatted(vehiculo2, conductorId, clienteId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje2))
                .andExpect(status().isConflict());
    }

    @Test
    void crearViajeEnCursoSincronizaVehiculoYConductorAEnRuta() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-DISP-04");
        Long conductorId = crearConductor("CI-VJE-DISP-04");
        Long clienteId = crearCliente("CI-VJE-DISP-CLI-03");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"En Curso"}
                """.formatted(vehiculoId, conductorId, clienteId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/vehiculos/" + vehiculoId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("En Ruta"));
        mockMvc.perform(get("/api/conductores/" + conductorId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("En Ruta"));
    }

    @Test
    void actualizarViajeConOtroVehiculoYConductorLiberaLosAnteriores() throws Exception {
        Long vehiculoOriginal = crearVehiculo("VJE-REAS-01");
        Long conductorOriginal = crearConductor("CI-VJE-REAS-01");
        Long vehiculoNuevo = crearVehiculo("VJE-REAS-02");
        Long conductorNuevo = crearConductor("CI-VJE-REAS-02");
        Long clienteId = crearCliente("CI-VJE-REAS-CLI");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoOriginal, conductorOriginal, clienteId);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long viajeId = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(get("/api/vehiculos/" + vehiculoOriginal)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("En Ruta"));

        String actualizacion = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoNuevo, conductorNuevo, clienteId);
        mockMvc.perform(put("/api/viajes/" + viajeId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacion))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/vehiculos/" + vehiculoOriginal)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("Disponible"));
        mockMvc.perform(get("/api/conductores/" + conductorOriginal)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("Disponible"));
        mockMvc.perform(get("/api/vehiculos/" + vehiculoNuevo)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("En Ruta"));
        mockMvc.perform(get("/api/conductores/" + conductorNuevo)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("En Ruta"));
    }

    @Test
    void actualizarViajeConOtraCargaLiberaLaCargaAnterior() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-REAS-03");
        Long conductorId = crearConductor("CI-VJE-REAS-03");
        Long clienteId = crearCliente("CI-VJE-REAS-CLI-02");
        Long cargaOriginal = crearCarga(clienteId, "Reas1");
        Long cargaNueva = crearCarga(clienteId, "Reas2");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId, cargaOriginal);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long viajeId = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(get("/api/cargas/" + cargaOriginal)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("Asignada"));

        String actualizacion = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId, cargaNueva);
        mockMvc.perform(put("/api/viajes/" + viajeId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actualizacion))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cargas/" + cargaOriginal)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("Pendiente"));
        mockMvc.perform(get("/api/cargas/" + cargaNueva)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("Asignada"));
    }

    @Test
    void confirmarEntregaSincronizaVehiculoYConductorADisponible() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-DISP-05");
        Long conductorId = crearConductor("CI-VJE-DISP-05");
        Long clienteId = crearCliente("CI-VJE-DISP-CLI-04");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"En Curso"}
                """.formatted(vehiculoId, conductorId, clienteId);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long viajeId = objectMapper.readTree(creado).get("id").asLong();

        String tokenConductor = tokenPara("conductordisp1", Rol.CONDUCTOR);
        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/vehiculos/" + vehiculoId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("Disponible"));
        mockMvc.perform(get("/api/conductores/" + conductorId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$.estado").value("Disponible"));
    }

    @Test
    void conductorConfirmaLlegadaFinalizaElViajeYQuedaRegistrado() throws Exception {
        Long viajeId = crearViajeEnCurso("CONF1");
        String tokenConductor = tokenPara("conductorentrega1", Rol.CONDUCTOR);

        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"Entregado sin novedad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Finalizado"))
                .andExpect(jsonPath("$.entregaConfirmada").value(true))
                .andExpect(jsonPath("$.confirmadoPor").value("conductorentrega1"))
                .andExpect(jsonPath("$.observacionEntrega").value("Entregado sin novedad"));
    }

    @Test
    void supervisorNoPuedeConfirmarLlegada() throws Exception {
        Long viajeId = crearViajeEnCurso("CONF2");
        String tokenSupervisor = tokenPara("supervisorentrega1", Rol.SUPERVISOR);

        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmarLlegadaDeViajeNoEnCursoDaConflicto() throws Exception {
        Long vehiculoId = crearVehiculo("VJE-PROG");
        Long conductorId = crearConductor("CI-VJE-PROG");
        Long clienteId = crearCliente("CI-VJE-PROG-CLI");
        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:30:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long viajeId = objectMapper.readTree(creado).get("id").asLong();

        String tokenConductor = tokenPara("conductorentrega2", Rol.CONDUCTOR);
        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmarLlegadaDosVecesDaConflicto() throws Exception {
        Long viajeId = crearViajeEnCurso("CONF3");
        String tokenConductor = tokenPara("conductorentrega3", Rol.CONDUCTOR);

        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void supervisorValidaEntregaYaConfirmada() throws Exception {
        Long viajeId = crearViajeEnCurso("VAL1");
        String tokenConductor = tokenPara("conductorentrega4", Rol.CONDUCTOR);
        String tokenSupervisor = tokenPara("supervisorentrega2", Rol.SUPERVISOR);

        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/viajes/" + viajeId + "/validar-entrega")
                        .header("Authorization", "Bearer " + tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"Confirmado con el cliente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregaValidada").value(true))
                .andExpect(jsonPath("$.validadoPor").value("supervisorentrega2"));
    }

    @Test
    void validarEntregaSinConfirmarDaConflicto() throws Exception {
        Long viajeId = crearViajeEnCurso("VAL2");
        String tokenSupervisor = tokenPara("supervisorentrega3", Rol.SUPERVISOR);

        mockMvc.perform(post("/api/viajes/" + viajeId + "/validar-entrega")
                        .header("Authorization", "Bearer " + tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void conductorNoPuedeValidarEntrega() throws Exception {
        Long viajeId = crearViajeEnCurso("VAL3");
        String tokenConductor = tokenPara("conductorentrega5", Rol.CONDUCTOR);

        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/viajes/" + viajeId + "/validar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
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
