package com.trailersys.backend.common;

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
 * Antes, el modulo Reportes (js/reportes.js) calculaba el desglose por
 * estado/tipo contando solo los 100 registros que trae la pagina que se
 * muestra en pantalla, no el total real - con catalogos grandes, esas
 * tarjetas mostraban numeros sin relacion con la realidad. Estas pruebas
 * verifican que /api/reportes/* devuelve el conteo real, no el de una
 * muestra.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReporteResumenControllerTest {

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
                {"nombres":"Conductor Resumen","identificacion":"%s","telefono":"0999999999",
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

    private Long crearCliente(String identificacion, String estado) throws Exception {
        String cliente = """
                {"nombre":"Cliente Resumen","identificacion":"%s","estado":"%s",
                 "telefono":"0999999999","direccion":"Direccion"}
                """.formatted(identificacion, estado);
        String creado = mockMvc.perform(post("/api/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cliente))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(creado).get("id").asLong();
    }

    private void crearCarga(Long clienteId, String sufijo, String estado) throws Exception {
        String carga = """
                {"descripcion":"Carga %s","clienteId":%d,"tipo":"General","peso":100,
                 "origen":"Origen %s","destino":"Destino %s","estado":"%s"}
                """.formatted(sufijo, clienteId, sufijo, sufijo, estado);
        mockMvc.perform(post("/api/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(carga))
                .andExpect(status().isCreated());
    }

    @Test
    void resumenDeCargasCuentaPorEstadoSinDependerDeLaPaginacion() throws Exception {
        Long clienteA = crearCliente("CI-RES-CARGA-A", "Activo");
        crearCarga(clienteA, "ResA1", "Pendiente");
        crearCarga(clienteA, "ResA2", "Pendiente");
        crearCarga(clienteA, "ResA3", "Cancelada");

        String cuerpo = mockMvc.perform(get("/api/reportes/cargas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // No es un conteo exacto de "solo estas 3" porque el catalogo puede
        // traer datos de otras pruebas, pero al menos debe reflejar las que
        // se acaban de crear (si estuviera limitado a una "pagina" de otro
        // origen, estas nuevas ni siquiera se verian reflejadas de forma
        // consistente con el total real).
        var nodo = objectMapper.readTree(cuerpo);
        org.assertj.core.api.Assertions.assertThat(nodo.get("pendientes").asLong()).isGreaterThanOrEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(nodo.get("canceladas").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void resumenDeClientesCuentaPorEstado() throws Exception {
        crearCliente("CI-RES-CLI-ACT", "Activo");
        crearCliente("CI-RES-CLI-INA", "Inactivo");

        String cuerpo = mockMvc.perform(get("/api/reportes/clientes")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var nodo = objectMapper.readTree(cuerpo);
        org.assertj.core.api.Assertions.assertThat(nodo.get("activos").asLong()).isGreaterThanOrEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(nodo.get("inactivos").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void resumenDeViajesRespetaElRangoDeFechaYSumaLaDistancia() throws Exception {
        Long vehiculoId = crearVehiculo("RES-VJE-01");
        Long conductorId = crearConductor("CI-RES-VJE-01");
        Long clienteId = crearCliente("CI-RES-VJE-CLI-01", "Activo");

        String viaje = """
                {"vehiculoId":%d,"conductorId":%d,"clienteId":%d,"origen":"Quito, Ecuador",
                 "destino":"Guayaquil, Ecuador","fechaSalida":"2020-01-15T08:30:00","estado":"Finalizado",
                 "ruta":{"origenLat":-0.22,"origenLng":-78.51,"destinoLat":-2.18,"destinoLng":-79.88,
                 "distanciaKm":424.5,"duracionMin":372.6,"path":[{"lat":-0.22,"lng":-78.51}]}}
                """.formatted(vehiculoId, conductorId, clienteId);
        mockMvc.perform(post("/api/viajes")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(viaje))
                .andExpect(status().isCreated());

        // Fuera del rango: no debe contarlo ni sumar su distancia.
        mockMvc.perform(get("/api/reportes/viajes")
                        .param("desde", "2026-01-01")
                        .param("hasta", "2026-01-31")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalizados").value(0))
                .andExpect(jsonPath("$.kmTotales").value(0.0));

        // Dentro del rango: si debe contarlo y sumar su distancia.
        mockMvc.perform(get("/api/reportes/viajes")
                        .param("desde", "2020-01-01")
                        .param("hasta", "2020-01-31")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalizados").value(1))
                .andExpect(jsonPath("$.kmTotales").value(424.5));
    }

    @Test
    void resumenDeMantenimientosRespetaElVehiculoYNoElTipo() throws Exception {
        Long vehiculoId = crearVehiculo("RES-MNT-01");
        String preventivo = """
                {"vehiculoId":%d,"tipo":"Preventivo","fecha":"2026-08-01","kilometraje":1000,
                 "costo":50.0,"descripcion":"Preventivo resumen"}
                """.formatted(vehiculoId);
        String correctivo = """
                {"vehiculoId":%d,"tipo":"Correctivo","fecha":"2026-08-02","kilometraje":1100,
                 "costo":75.0,"descripcion":"Correctivo resumen"}
                """.formatted(vehiculoId);
        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preventivo))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/mantenimientos")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctivo))
                .andExpect(status().isCreated());

        // Filtrado por este vehiculo especifico: debe ver ambos, uno de cada
        // tipo, y el costo total de los dos ($125).
        mockMvc.perform(get("/api/reportes/mantenimientos")
                        .param("vehiculoId", String.valueOf(vehiculoId))
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preventivos").value(1))
                .andExpect(jsonPath("$.correctivos").value(1))
                .andExpect(jsonPath("$.costoTotal").value(125.0));
    }

    @Test
    void rolSinAccesoDevuelveProhibido() throws Exception {
        if (usuarioRepository.findByUsernameIgnoreCase("clienteresumentest").isEmpty()) {
            usuarioRepository.save(new Usuario(
                    "clienteresumentest", passwordEncoder.encode("clave1234"), "Cliente Test", null, Rol.CLIENTE));
        }
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"clienteresumentest\",\"password\":\"clave1234\"}"))
                .andReturn().getResponse().getContentAsString();
        String tokenCliente = objectMapper.readTree(body).get("token").asText();

        mockMvc.perform(get("/api/reportes/cargas").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reportes/clientes").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reportes/mantenimientos").header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isForbidden());
    }
}
