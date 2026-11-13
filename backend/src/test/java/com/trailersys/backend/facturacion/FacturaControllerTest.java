package com.trailersys.backend.facturacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailersys.backend.cliente.Cliente;
import com.trailersys.backend.cliente.ClienteRepository;
import com.trailersys.backend.cliente.EstadoCliente;
import com.trailersys.backend.conductor.Conductor;
import com.trailersys.backend.conductor.ConductorRepository;
import com.trailersys.backend.conductor.EstadoConductor;
import com.trailersys.backend.usuario.Rol;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;
import com.trailersys.backend.vehiculo.EstadoVehiculo;
import com.trailersys.backend.vehiculo.Vehiculo;
import com.trailersys.backend.vehiculo.VehiculoRepository;
import com.trailersys.backend.viaje.EstadoViaje;
import com.trailersys.backend.viaje.Viaje;
import com.trailersys.backend.viaje.ViajeRepository;

@SpringBootTest
@AutoConfigureMockMvc
class FacturaControllerTest {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private VehiculoRepository vehiculoRepository;
    @Autowired private ConductorRepository conductorRepository;
    @Autowired private ViajeRepository viajeRepository;

    private String tokenAdmin;

    @BeforeEach
    void preparar() throws Exception {
        tokenAdmin = tokenPara("adminfacturas", Rol.ADMINISTRADOR, null);
    }

    // ---------- utilidades ----------

    private String tokenPara(String username, Rol rol, Cliente cliente) throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase(username).isEmpty()) {
            Usuario u = new Usuario(username, passwordEncoder.encode("clave1234"), "Usuario " + username, null, rol);
            u.setCliente(cliente);
            usuarioRepository.save(u);
        }
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"clave1234\"}".formatted(username)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private Cliente nuevoCliente() {
        int n = SECUENCIA.getAndIncrement();
        return clienteRepository.save(new Cliente("Cliente Factura " + n, "17900" + String.format("%05d", n) + "001",
                EstadoCliente.ACTIVO, "0999999999", "factura" + n + "@test.ec", "Av. Prueba " + n, null, null));
    }

    private Viaje nuevoViaje(Cliente cliente, EstadoViaje estado) {
        int n = SECUENCIA.getAndIncrement();
        Vehiculo vehiculo = vehiculoRepository.save(new Vehiculo("FAC-" + String.format("%04d", n), "Hino", "300",
                "Camión", 2020, "Blanco", EstadoVehiculo.DISPONIBLE, 1000, 5000, null, null));
        Conductor conductor = conductorRepository.save(new Conductor("Conductor Factura " + n,
                "09" + String.format("%08d", n), "0991234567", null, "LIC-F" + n, "Tipo E",
                LocalDate.now().plusYears(2), EstadoConductor.DISPONIBLE, null, "", null));
        return viajeRepository.save(new Viaje(vehiculo, conductor, cliente, null, "Guayaquil, Ecuador",
                "Quito, Ecuador", LocalDateTime.now().minusDays(3), estado, ""));
    }

    private String crearFacturaJson(Long clienteId, Long viajeId, String monto) {
        return """
                {"clienteId":%d,"diasCredito":30,"items":[{"viajeId":%d,"monto":%s}]}
                """.formatted(clienteId, viajeId, monto);
    }

    private long crearFactura(Long clienteId, Long viajeId, String monto) throws Exception {
        String body = mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crearFacturaJson(clienteId, viajeId, monto)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions pagar(long facturaId, String monto) throws Exception {
        return mockMvc.perform(post("/api/facturas/" + facturaId + "/pagos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"monto\":%s,\"metodo\":\"Transferencia\",\"referencia\":\"TRX-1\"}".formatted(monto)));
    }

    // ---------- pruebas ----------

    @Test
    void crearFacturaCalculaIvaTotalesYSacaElViajeDeLosFacturables() throws Exception {
        Cliente cliente = nuevoCliente();
        Viaje viaje = nuevoViaje(cliente, EstadoViaje.FINALIZADO);

        mockMvc.perform(get("/api/facturas/viajes-facturables").param("clienteId", cliente.getId().toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clienteId":%d,"diasCredito":30,"ivaPorcentaje":15,
                                 "items":[{"viajeId":%d,"monto":1000.00}]}
                                """.formatted(cliente.getId(), viaje.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(org.hamcrest.Matchers.startsWith("FAC-")))
                .andExpect(jsonPath("$.subtotal").value(1000.00))
                .andExpect(jsonPath("$.iva").value(150.00))
                .andExpect(jsonPath("$.total").value(1150.00))
                .andExpect(jsonPath("$.saldo").value(1150.00))
                .andExpect(jsonPath("$.estado").value("Pendiente"))
                .andExpect(jsonPath("$.fechaVencimiento").value(LocalDate.now().plusDays(30).toString()))
                .andExpect(jsonPath("$.detalles[0].descripcion").value(org.hamcrest.Matchers.containsString("Guayaquil")));

        mockMvc.perform(get("/api/facturas/viajes-facturables").param("clienteId", cliente.getId().toString())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void noSeFacturanViajesNoFinalizadosDeOtroClienteNiYaFacturados() throws Exception {
        Cliente cliente = nuevoCliente();
        Cliente otro = nuevoCliente();
        Viaje enCurso = nuevoViaje(cliente, EstadoViaje.EN_CURSO);
        Viaje ajeno = nuevoViaje(otro, EstadoViaje.FINALIZADO);
        Viaje facturado = nuevoViaje(cliente, EstadoViaje.FINALIZADO);
        crearFactura(cliente.getId(), facturado.getId(), "500");

        for (Long viajeId : new Long[] {enCurso.getId(), ajeno.getId(), facturado.getId()}) {
            mockMvc.perform(post("/api/facturas")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(crearFacturaJson(cliente.getId(), viajeId, "100")))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void sinViajesOConMontoInvalidoDevuelve400() throws Exception {
        Cliente cliente = nuevoCliente();
        Viaje viaje = nuevoViaje(cliente, EstadoViaje.FINALIZADO);
        mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clienteId\":%d,\"items\":[]}".formatted(cliente.getId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crearFacturaJson(cliente.getId(), viaje.getId(), "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagosParcialesYTotalActualizanSaldoYEstado() throws Exception {
        Cliente cliente = nuevoCliente();
        long id = crearFactura(cliente.getId(), nuevoViaje(cliente, EstadoViaje.FINALIZADO).getId(), "100");
        // total = 115.00

        pagar(id, "40").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Parcial"))
                .andExpect(jsonPath("$.saldo").value(75.00))
                .andExpect(jsonPath("$.pagado").value(40.00))
                .andExpect(jsonPath("$.pagos", hasSize(1)));

        pagar(id, "80").andExpect(status().isConflict());

        pagar(id, "75").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Pagada"))
                .andExpect(jsonPath("$.saldo").value(0.0));

        pagar(id, "1").andExpect(status().isConflict());
    }

    @Test
    void anularLiberaElViajeYNoSePermiteConPagos() throws Exception {
        Cliente cliente = nuevoCliente();
        Viaje viaje = nuevoViaje(cliente, EstadoViaje.FINALIZADO);
        long conPago = crearFactura(cliente.getId(), nuevoViaje(cliente, EstadoViaje.FINALIZADO).getId(), "100");
        pagar(conPago, "10").andExpect(status().isOk());
        mockMvc.perform(post("/api/facturas/" + conPago + "/anular")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Error\"}"))
                .andExpect(status().isConflict());

        long id = crearFactura(cliente.getId(), viaje.getId(), "100");
        mockMvc.perform(post("/api/facturas/" + id + "/anular")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Valor mal digitado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Anulada"))
                .andExpect(jsonPath("$.motivoAnulacion").value("Valor mal digitado"));

        // El viaje vuelve a estar disponible para facturarse.
        crearFactura(cliente.getId(), viaje.getId(), "120");
    }

    @Test
    void permisosPorRol() throws Exception {
        Cliente cliente = nuevoCliente();
        Viaje viaje = nuevoViaje(cliente, EstadoViaje.FINALIZADO);
        String supervisor = tokenPara("supervisorfacturas", Rol.SUPERVISOR, null);
        String coordinador = tokenPara("coordinadorfacturas", Rol.COORDINADOR, null);
        String conductor = tokenPara("conductorfacturas", Rol.CONDUCTOR, null);

        mockMvc.perform(get("/api/facturas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/facturas").header("Authorization", "Bearer " + conductor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/facturas").header("Authorization", "Bearer " + supervisor))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + supervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crearFacturaJson(cliente.getId(), viaje.getId(), "100")))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(post("/api/facturas")
                        .header("Authorization", "Bearer " + coordinador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crearFacturaJson(cliente.getId(), viaje.getId(), "100")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).get("id").asLong();
        mockMvc.perform(post("/api/facturas/" + id + "/anular")
                        .header("Authorization", "Bearer " + coordinador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resumenDeCarteraYFiltroPorEstado() throws Exception {
        Cliente cliente = nuevoCliente();
        long id = crearFactura(cliente.getId(), nuevoViaje(cliente, EstadoViaje.FINALIZADO).getId(), "200");
        pagar(id, "30").andExpect(status().isOk());

        mockMvc.perform(get("/api/facturas/resumen").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porCobrar").isNumber())
                .andExpect(jsonPath("$.facturasAbiertas").isNumber())
                .andExpect(jsonPath("$.cobradoMes").isNumber());

        mockMvc.perform(get("/api/facturas")
                        .param("clienteId", cliente.getId().toString())
                        .param("estado", "Parcial")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].saldo").value(200.00));

        mockMvc.perform(get("/api/facturas")
                        .param("clienteId", cliente.getId().toString())
                        .param("estado", "Pagada")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elClienteSoloVeSusPropiasFacturas() throws Exception {
        Cliente cliente = nuevoCliente();
        Cliente otro = nuevoCliente();
        crearFactura(cliente.getId(), nuevoViaje(cliente, EstadoViaje.FINALIZADO).getId(), "100");
        crearFactura(otro.getId(), nuevoViaje(otro, EstadoViaje.FINALIZADO).getId(), "100");
        String tokenCliente = tokenPara("clientefacturas" + cliente.getId(), Rol.CLIENTE, cliente);

        mockMvc.perform(get("/api/mis-facturas").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].clienteId").value(cliente.getId()));

        mockMvc.perform(get("/api/facturas").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
    }
}
