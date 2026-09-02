package com.trailersys.backend.pedido;

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
 * Cubre /api/reclamos: un reclamo no es una entidad propia, es un viaje con
 * estadoReclamoCliente != null (lo deja asi PedidoClienteService.confirmarRecepcion
 * cuando el cliente reporta una novedad distinta de "COMPLETO" al confirmar
 * recepcion). Este modulo (Administrador/Coordinador) solo lista esos viajes
 * y responde/resuelve el reclamo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReclamoControllerTest {

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
        if (usuarioRepository.findByUsernameIgnoreCase("admintestreclamo").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "admintestreclamo", passwordEncoder.encode("clave1234"), "Admin Test", null, Rol.ADMINISTRADOR));
        }
        tokenAdmin = login("admintestreclamo", "clave1234");
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
                {"nombres":"Conductor Reclamo Test","identificacion":"%s","telefono":"0999999999",
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

    private String crearUsuarioClienteYToken(String username, Long clienteId) throws Exception {
        String usuario = """
                {"username":"%s","password":"clave1234","nombre":"Usuario %s","rol":"CLIENTE","activo":true,"clienteId":%d}
                """.formatted(username, username, clienteId);
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuario))
                .andExpect(status().isCreated());
        return login(username, "clave1234");
    }

    /**
     * Arma un viaje En Curso, con la llegada ya confirmada por el conductor, y
     * al cliente reportando una novedad ("DANADO") al confirmar recepcion -
     * eso es justo lo que deja estadoReclamoCliente en "ABIERTO". Devuelve el
     * id del viaje (mismo id que usa /api/reclamos/{id}).
     */
    private Long crearViajeConReclamoAbierto(String sufijo) throws Exception {
        Long clienteId = crearCliente("CI-REC-" + sufijo);
        String tokenCliente = crearUsuarioClienteYToken("clientereclamo" + sufijo, clienteId);
        Long vehiculoId = crearVehiculo("REC-" + sufijo);
        Long conductorId = crearConductor("CI-REC-COND-" + sufijo);

        String pedido = """
                {"descripcion":"Pedido con reclamo %s","tipo":"General","peso":100,"origen":"Quito","destino":"Guayaquil"}
                """.formatted(sufijo);
        String creado = mockMvc.perform(post("/api/mis-cargas")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cargaId = objectMapper.readTree(creado).get("id").asLong();

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"cargaId":%d,
                 "origen":"Quito","destino":"Guayaquil","fechaSalida":"2026-08-15T08:00:00","estado":"En Curso"}
                """.formatted(vehiculoId, conductorId, clienteId, cargaId);
        String viajeCreado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long viajeId = objectMapper.readTree(viajeCreado).get("id").asLong();

        String tokenConductor = tokenPara("conductorreclamo" + sufijo, Rol.CONDUCTOR);
        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mis-cargas/" + cargaId + "/confirmar-recepcion")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novedad\":\"DANADO\",\"observacion\":\"Llegó con la caja rota\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReclamoCliente").value("ABIERTO"));

        return viajeId;
    }

    @Test
    void listarSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/reclamos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesSinAccesoNoPuedenListarNiResponderReclamos() throws Exception {
        String tokenConductor = tokenPara("conductorsinreclamo", Rol.CONDUCTOR);
        mockMvc.perform(get("/api/reclamos").header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isForbidden());

        String tokenSupervisor = tokenPara("supervisorsinreclamo", Rol.SUPERVISOR);
        mockMvc.perform(get("/api/reclamos").header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isForbidden());

        Long clienteId = crearCliente("CI-REC-ROL");
        String tokenCliente = crearUsuarioClienteYToken("clientereclamorol", clienteId);
        mockMvc.perform(get("/api/reclamos").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/reclamos/1")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarDevuelveSoloViajesConReclamoAbiertoYTraeLosDatosDenormalizados() throws Exception {
        Long viajeConReclamoId = crearViajeConReclamoAbierto("LISTA");

        mockMvc.perform(get("/api/reclamos").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + viajeConReclamoId + ")].estadoReclamoCliente").value("ABIERTO"))
                .andExpect(jsonPath("$[?(@.id == " + viajeConReclamoId + ")].novedadRecepcionCliente").value("DANADO"))
                .andExpect(jsonPath("$[?(@.id == " + viajeConReclamoId + ")].observacionConfirmacionCliente")
                        .value("Llegó con la caja rota"))
                .andExpect(jsonPath("$[?(@.id == " + viajeConReclamoId + ")].clienteNombre").exists());
    }

    @Test
    void responderMarcaComoResueltoYFijaFechaDeResolucion() throws Exception {
        Long viajeId = crearViajeConReclamoAbierto("RESUELTO");

        mockMvc.perform(put("/api/reclamos/" + viajeId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"Se repuso la mercancía dañada.\",\"estado\":\"RESUELTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReclamoCliente").value("RESUELTO"))
                .andExpect(jsonPath("$.respuestaReclamoCliente").value("Se repuso la mercancía dañada."))
                .andExpect(jsonPath("$.fechaResolucionReclamoCliente").exists());

        // Ya no aparece en el listado de reclamos sin resolver... en realidad
        // /api/reclamos lista TODOS los que alguna vez tuvieron reclamo (para
        // conservar el historial), asi que sigue apareciendo, ahora Resuelto.
        mockMvc.perform(get("/api/reclamos").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + viajeId + ")].estadoReclamoCliente").value("RESUELTO"));
    }

    @Test
    void responderSinMarcarResueltoQuedaEnRevisionYSinFechaDeResolucion() throws Exception {
        Long viajeId = crearViajeConReclamoAbierto("REVISION");

        mockMvc.perform(put("/api/reclamos/" + viajeId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"Estamos revisando tu caso.\",\"estado\":\"EN_REVISION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoReclamoCliente").value("EN_REVISION"))
                .andExpect(jsonPath("$.respuestaReclamoCliente").value("Estamos revisando tu caso."))
                .andExpect(jsonPath("$.fechaResolucionReclamoCliente").doesNotExist());
    }

    @Test
    void responderConRespuestaEnBlancoDaError() throws Exception {
        Long viajeId = crearViajeConReclamoAbierto("BLANCO");

        mockMvc.perform(put("/api/reclamos/" + viajeId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"   \",\"estado\":\"RESUELTO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void responderUnViajeSinReclamoDaError() throws Exception {
        Long vehiculoId = crearVehiculo("REC-SINREC");
        Long conductorId = crearConductor("CI-REC-SINREC");
        Long clienteId = crearCliente("CI-REC-SINREC-CLI");
        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,
                 "origen":"Quito","destino":"Guayaquil","fechaSalida":"2026-08-15T08:00:00","estado":"Programado"}
                """.formatted(vehiculoId, conductorId, clienteId);
        String viajeCreado = mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long viajeId = objectMapper.readTree(viajeCreado).get("id").asLong();

        mockMvc.perform(put("/api/reclamos/" + viajeId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"No hay nada que responder\",\"estado\":\"RESUELTO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void responderUnViajeInexistenteDaNoEncontrado() throws Exception {
        mockMvc.perform(put("/api/reclamos/9999999")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"x\",\"estado\":\"RESUELTO\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reclamoResueltoDejaFinalizarElViajeYNoResueltoLoBloquea() throws Exception {
        Long viajeId = crearViajeConReclamoAbierto("FINALIZA");

        // Con el reclamo todavia abierto, Coordinador/Administrador no pueden cerrar el viaje.
        mockMvc.perform(post("/api/viajes/" + viajeId + "/finalizar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/reclamos/" + viajeId)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"respuesta\":\"Se resolvió el inconveniente.\",\"estado\":\"RESUELTO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/viajes/" + viajeId + "/finalizar")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Finalizado"));
    }
}
