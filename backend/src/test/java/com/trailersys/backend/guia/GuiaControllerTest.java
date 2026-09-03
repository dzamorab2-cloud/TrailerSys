package com.trailersys.backend.guia;

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
import com.trailersys.backend.carga.CargaRepository;
import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;
import com.trailersys.backend.viaje.ViajeRepository;

/**
 * GuiaController arma el listado a mano con SQL crudo (JdbcTemplate) y
 * traduce el estado de cada Carga/Viaje a español con un CASE propio, sin
 * depender de EstadoCarga/EstadoViaje - cualquier valor nuevo del enum hay
 * que sumarlo ahi tambien a mano, o cae en el ELSE por defecto. Tambien
 * calcula el conductor/placa de una Carga a partir del ultimo viaje que la
 * transporto, sin LATERAL (ver GuiaController.union) para que esta clase
 * pueda correr contra H2, la base de las pruebas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GuiaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ViajeRepository viajeRepository;

    @Autowired
    private CargaRepository cargaRepository;

    private String tokenAdmin;

    @BeforeEach
    void prepararUsuarioYToken() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("admintestguia").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "admintestguia", passwordEncoder.encode("clave1234"), "Admin Test", null, Rol.ADMINISTRADOR));
        }
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admintestguia\",\"password\":\"clave1234\"}"))
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

    private Long crearConductorDePrueba(String identificacion) throws Exception {
        String conductor = """
                {"nombres":"Conductor Guia Test","identificacion":"%s","telefono":"0999999999",
                 "licenciaNumero":"LIC-%s","licenciaCategoria":"Tipo E",
                 "licenciaVencimiento":"2030-01-01","estado":"Disponible"}
                """.formatted(identificacion, identificacion);
        String creado = mockMvc.perform(post("/api/conductores")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conductor))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private Long crearClienteDePrueba(String identificacion) throws Exception {
        String cliente = """
                {"nombre":"Cliente Guia Test","identificacion":"%s","estado":"Activo",
                 "telefono":"0999999999","direccion":"Direccion de prueba"}
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
        mockMvc.perform(get("/api/guias"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolSinAccesoDaProhibido() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("supervisortestguia").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "supervisortestguia", passwordEncoder.encode("clave1234"), "Supervisor Test", null, Rol.SUPERVISOR));
        }
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"supervisortestguia\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenSupervisor = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/guias").header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaCargaCanceladaApareceComoCanceladaEnElListadoDeGuiasNoComoPendiente() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-GUIA-CANCEL");
        String carga = """
                {"descripcion":"Pedido cancelado para guia","clienteId":%d,"tipo":"General",
                 "peso":100,"origen":"A","destino":"B","estado":"Pendiente"}
                """.formatted(clienteId);
        String creada = mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cargaId = objectMapper.readTree(creada).get("id").asLong();

        String edicion = """
                {"descripcion":"Pedido cancelado para guia","clienteId":%d,"tipo":"General",
                 "peso":100,"origen":"A","destino":"B","estado":"Cancelada"}
                """.formatted(clienteId);
        mockMvc.perform(put("/api/cargas/" + cargaId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edicion))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/guias")
                        .param("search", "Pedido cancelado para guia")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.referenciaId == " + cargaId + " && @.tipo == 'CARGA')].estado")
                        .value("Cancelada"));

        // El filtro por estado (antes rompia contra H2 por el LATERAL de mas
        // abajo) tambien tiene que encontrarla.
        mockMvc.perform(get("/api/guias")
                        .param("estado", "Cancelada")
                        .param("search", "Pedido cancelado para guia")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.referenciaId == " + cargaId + " && @.tipo == 'CARGA')]").exists());
    }

    @Test
    void unaCargaConViajeAsignadoMuestraElConductorYLaPlacaDelUltimoViaje() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-GUIA-ASIG");
        Long vehiculoId = crearVehiculoDePrueba("GUIA-001");
        Long conductorId = crearConductorDePrueba("CI-GUIA-COND");

        String carga = """
                {"descripcion":"Carga con viaje para guia","clienteId":%d,"tipo":"General",
                 "peso":100,"origen":"A","destino":"B","estado":"Pendiente"}
                """.formatted(clienteId);
        String cargaCreada = mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cargaId = objectMapper.readTree(cargaCreada).get("id").asLong();

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"A","destino":"B","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId, cargaId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/guias")
                        .param("search", "Carga con viaje para guia")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.referenciaId == " + cargaId + " && @.tipo == 'CARGA')].conductor")
                        .value("Conductor Guia Test"))
                .andExpect(jsonPath("$.content[?(@.referenciaId == " + cargaId + " && @.tipo == 'CARGA')].placa")
                        .value("GUIA-001"));
    }

    @Test
    void unViajeSinCargaAparaceComoViajeSinCargaEnElListado() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-GUIA-VIAJE");
        Long vehiculoId = crearVehiculoDePrueba("GUIA-002");
        Long conductorId = crearConductorDePrueba("CI-GUIA-COND2");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"Guia Origen","destino":"Guia Destino","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long viajeId = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(get("/api/guias")
                        .param("search", "Guia Origen")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.referenciaId == " + viajeId + " && @.tipo == 'VIAJE')].descripcion")
                        .value("Viaje sin carga"))
                .andExpect(jsonPath("$.content[?(@.referenciaId == " + viajeId + " && @.tipo == 'VIAJE')].estado")
                        .value("Programado"));
    }

    /**
     * Sin texto de busqueda, listar() usa el camino rapido (listarSinBusqueda):
     * empuja el filtro de tipo/estado y el ORDER BY+LIMIT antes de los JOIN,
     * en vez de armar el UNION completo y recien ahi filtrar/ordenar/paginar
     * (ver el comentario de ese metodo). Estas pruebas cubren ese camino
     * directamente, sin "search", que es justo lo que las de mas arriba NO
     * cubren (todas pasan "search").
     */
    @Test
    void sinBusquedaElFiltroDeTipoSoloTraeCargas() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-GUIA-RAPIDO-TIPO");
        String carga = """
                {"descripcion":"Carga camino rapido tipo","clienteId":%d,"tipo":"General",
                 "peso":100,"origen":"A","destino":"B","estado":"Pendiente"}
                """.formatted(clienteId);
        String creada = mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cargaId = objectMapper.readTree(creada).get("id").asLong();

        // size grande para no depender del orden entre pruebas que comparten la misma base H2.
        mockMvc.perform(get("/api/guias").param("tipo", "CARGA").param("size", "100")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.tipo == 'VIAJE')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.referenciaId == " + cargaId + " && @.tipo == 'CARGA')]").exists());
    }

    @Test
    void sinBusquedaElFiltroDeEstadoEncuentraElViajeCorrecto() throws Exception {
        Long clienteId = crearClienteDePrueba("CI-GUIA-RAPIDO-EST");
        Long vehiculoId = crearVehiculoDePrueba("GUIA-RAPIDO");
        Long conductorId = crearConductorDePrueba("CI-GUIA-RAPIDO-COND");
        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"Guia Rapido Origen","destino":"Guia Rapido Destino",
                 "fechaSalida":"2026-08-15T08:00:00","estado":"Cancelado"}
                """.formatted(vehiculoId, conductorId, clienteId);
        String creado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long viajeId = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(get("/api/guias").param("tipo", "VIAJE").param("estado", "Cancelado").param("size", "100")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.referenciaId == " + viajeId + " && @.tipo == 'VIAJE')].estado")
                        .value("Cancelado"))
                .andExpect(jsonPath("$.content[?(@.referenciaId == " + viajeId + " && @.tipo == 'VIAJE' && @.origen == 'Guia Rapido Origen')]")
                        .exists());
    }

    @Test
    void sinBusquedaElTotalCoincideConLaSumaDeViajesYCargas() throws Exception {
        long totalReal = viajeRepository.count() + cargaRepository.count();

        String body = mockMvc.perform(get("/api/guias").param("size", "12")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long totalDelEndpoint = objectMapper.readTree(body).get("totalElements").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(totalReal, totalDelEndpoint);
    }
}
