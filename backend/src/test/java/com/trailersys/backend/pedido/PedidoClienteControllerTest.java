package com.trailersys.backend.pedido;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;

/**
 * Cubre el autoservicio del rol CLIENTE ("hacer un pedido"): que solo cree,
 * vea y confirme SUS PROPIAS cargas/viajes, incluso si adivina el id de una
 * carga de otro cliente, y que no pueda usar los endpoints internos de
 * Cargas/Viajes (uso exclusivo de Administrador/Coordinador/etc.).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PedidoClienteControllerTest {

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
        if (usuarioRepository.findByUsernameIgnoreCase("admintestpedido").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "admintestpedido", passwordEncoder.encode("clave1234"), "Admin Test", null, Rol.ADMINISTRADOR));
        }
        tokenAdmin = login("admintestpedido", "clave1234");
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
                {"nombres":"Conductor Pedido Test","identificacion":"%s","telefono":"0999999999",
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

    /** Crea (via el endpoint de administracion de usuarios) una cuenta CLIENTE vinculada a clienteId, y su token. */
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

    @Test
    void listarSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/mis-cargas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearUsuarioConRolClienteSinClienteIdDaError() throws Exception {
        String usuario = """
                {"username":"clientesinvinculo","password":"clave1234","nombre":"Sin Vinculo","rol":"CLIENTE","activo":true}
                """;
        mockMvc.perform(post("/api/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usuario))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteCreaUnPedidoYQuedaPendienteAsociadoASuPropioCliente() throws Exception {
        Long clienteId = crearCliente("CI-PED-001");
        String tokenCliente = crearUsuarioClienteYToken("clientepedido1", clienteId);

        String pedido = """
                {"descripcion":"Pedido de prueba","tipo":"General","peso":100,
                 "origen":"Quito","destino":"Guayaquil"}
                """;
        String creado = mockMvc.perform(post("/api/mis-cargas")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("Pendiente"))
                .andExpect(jsonPath("$.clienteId").value(clienteId))
                .andReturn().getResponse().getContentAsString();
        Long cargaId = objectMapper.readTree(creado).get("id").asLong();

        mockMvc.perform(get("/api/mis-cargas").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + cargaId + ")]").exists());

        // Todavia no tiene viaje: 204 en vez de un error.
        mockMvc.perform(get("/api/mis-cargas/" + cargaId + "/viaje").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isNoContent());
    }

    @Test
    void clienteNoPuedeVerNiConfirmarCargasDeOtroClienteNiAdivinandoElId() throws Exception {
        Long clienteAId = crearCliente("CI-PED-A");
        Long clienteBId = crearCliente("CI-PED-B");
        String tokenA = crearUsuarioClienteYToken("clientepedidoA", clienteAId);
        String tokenB = crearUsuarioClienteYToken("clientepedidoB", clienteBId);

        String pedidoA = """
                {"descripcion":"Pedido secreto de A","tipo":"General","peso":100,"origen":"Quito","destino":"Guayaquil"}
                """;
        String creado = mockMvc.perform(post("/api/mis-cargas")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedidoA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cargaDeA = objectMapper.readTree(creado).get("id").asLong();

        String listadoB = mockMvc.perform(get("/api/mis-cargas").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(listadoB).doesNotContain("Pedido secreto de A");

        mockMvc.perform(get("/api/mis-cargas/" + cargaDeA + "/viaje").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/mis-cargas/" + cargaDeA + "/confirmar-recepcion")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clienteNoPuedeUsarLosEndpointsInternosDeCargasNiViajes() throws Exception {
        Long clienteId = crearCliente("CI-PED-INT");
        String tokenCliente = crearUsuarioClienteYToken("clientepedidointerno", clienteId);

        mockMvc.perform(get("/api/cargas").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/viajes").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteConfirmaRecepcionSoloCuandoElViajeEstaFinalizadoYNoDosVeces() throws Exception {
        Long clienteId = crearCliente("CI-PED-CONF");
        String tokenCliente = crearUsuarioClienteYToken("clientepedidoconf", clienteId);
        Long vehiculoId = crearVehiculo("PED-0001");
        Long conductorId = crearConductor("CI-PED-COND");

        String pedido = """
                {"descripcion":"Pedido a confirmar","tipo":"General","peso":100,"origen":"Quito","destino":"Guayaquil"}
                """;
        String creado = mockMvc.perform(post("/api/mis-cargas")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long cargaId = objectMapper.readTree(creado).get("id").asLong();

        // Sin viaje todavia: 409, no 404 (la carga si es suya).
        mockMvc.perform(post("/api/mis-cargas/" + cargaId + "/confirmar-recepcion")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

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

        // Viaje En Curso (no Finalizado todavia): sigue en conflicto.
        mockMvc.perform(post("/api/mis-cargas/" + cargaId + "/confirmar-recepcion")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        String tokenConductor = tokenPara("conductorpedidoconf", Rol.CONDUCTOR);
        mockMvc.perform(post("/api/viajes/" + viajeId + "/confirmar-entrega")
                        .header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/mis-cargas/" + cargaId + "/confirmar-recepcion")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"observacion\":\"Todo llegó en buen estado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregaConfirmadaCliente").value(true))
                .andExpect(jsonPath("$.confirmadoPorCliente").value("clientepedidoconf"))
                .andExpect(jsonPath("$.observacionConfirmacionCliente").value("Todo llegó en buen estado"))
                // El paso del cliente es paralelo: no toca la confirmacion del conductor ni la validacion del supervisor.
                .andExpect(jsonPath("$.entregaConfirmada").value(true))
                .andExpect(jsonPath("$.entregaValidada").value(false));

        mockMvc.perform(get("/api/mis-cargas/" + cargaId + "/viaje").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Finalizado"))
                .andExpect(jsonPath("$.entregaConfirmadaCliente").value(true));

        mockMvc.perform(post("/api/mis-cargas/" + cargaId + "/confirmar-recepcion")
                        .header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }
}
