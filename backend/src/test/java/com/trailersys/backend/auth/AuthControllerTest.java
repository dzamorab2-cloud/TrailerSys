package com.trailersys.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void loginConCredencialesValidasDevuelveToken() throws Exception {
        usuarioRepository.save(new Usuario(
                "carla", passwordEncoder.encode("secreta123"), "Carla Prueba", null, Rol.COORDINADOR));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carla\",\"password\":\"secreta123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value("COORDINADOR"));
    }

    @Test
    void loginConCredencialesInvalidasDevuelve401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-existe\",\"password\":\"lo-que-sea\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginSinCamposDevuelve400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioActualizaSuPropiaFotoDePerfilYSeReflejaEnMe() throws Exception {
        usuarioRepository.save(new Usuario(
                "supervisorfototest", passwordEncoder.encode("clave1234"), "Supervisor Foto", null, Rol.SUPERVISOR));
        String token = login("supervisorfototest", "clave1234");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").doesNotExist());

        mockMvc.perform(put("/api/auth/me/foto")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foto\":\"data:image/png;base64,abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").value("data:image/png;base64,abc123"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foto").value("data:image/png;base64,abc123"));
    }

    @Test
    void actualizarFotoSinTokenDevuelveNoAutorizado() throws Exception {
        mockMvc.perform(put("/api/auth/me/foto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foto\":\"data:image/png;base64,abc123\"}"))
                .andExpect(status().isUnauthorized());
    }

    private void intentarConContrasenaIncorrecta(String username) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"incorrecta\"}".formatted(username)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cincoIntentosFallidosSeguidosBloqueanLaCuentaAunConLaContrasenaCorrecta() throws Exception {
        usuarioRepository.save(new Usuario(
                "fuerzabruta1", passwordEncoder.encode("claveCorrecta1"), "Prueba Fuerza Bruta", null, Rol.COORDINADOR));

        for (int i = 0; i < 5; i++) {
            intentarConContrasenaIncorrecta("fuerzabruta1");
        }

        // Al sexto intento la cuenta ya esta bloqueada - ni con la contraseña
        // correcta deja entrar, y el mensaje/estado son distintos de un 401
        // comun para que el frontend pueda mostrar que esta bloqueada.
        String respuesta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fuerzabruta1\",\"password\":\"claveCorrecta1\"}"))
                .andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).contains("Demasiados intentos");
    }

    @Test
    void menosDeCincoIntentosFallidosNoBloqueaYUnLoginCorrectoReiniciaElContador() throws Exception {
        usuarioRepository.save(new Usuario(
                "fuerzabruta2", passwordEncoder.encode("claveCorrecta2"), "Prueba Fuerza Bruta 2", null, Rol.COORDINADOR));

        for (int i = 0; i < 4; i++) {
            intentarConContrasenaIncorrecta("fuerzabruta2");
        }

        // Con 4 intentos fallidos (por debajo del limite de 5) el login
        // correcto todavia funciona.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fuerzabruta2\",\"password\":\"claveCorrecta2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // El contador quedo en cero: se pueden volver a fallar hasta 4 veces
        // seguidas sin que la cuenta se bloquee.
        for (int i = 0; i < 4; i++) {
            intentarConContrasenaIncorrecta("fuerzabruta2");
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fuerzabruta2\",\"password\":\"claveCorrecta2\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void unAdministradorQueLeReseteaLaContrasenaDesbloqueaLaCuentaDeInmediato() throws Exception {
        usuarioRepository.save(new Usuario(
                "fuerzabruta3", passwordEncoder.encode("claveVieja"), "Prueba Fuerza Bruta 3", null, Rol.COORDINADOR));
        for (int i = 0; i < 5; i++) {
            intentarConContrasenaIncorrecta("fuerzabruta3");
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fuerzabruta3\",\"password\":\"claveVieja\"}"))
                .andExpect(status().isTooManyRequests());

        usuarioRepository.save(new Usuario(
                "admintestbloqueo", passwordEncoder.encode("clave1234"), "Admin Test", null, Rol.ADMINISTRADOR));
        String tokenAdmin = login("admintestbloqueo", "clave1234");
        Long idBloqueado = usuarioRepository.findByUsernameIgnoreCase("fuerzabruta3").orElseThrow().getId();

        mockMvc.perform(put("/api/usuarios/" + idBloqueado)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"fuerzabruta3","nombre":"Prueba Fuerza Bruta 3","rol":"COORDINADOR",
                                 "activo":true,"password":"claveNueva123"}
                                """))
                .andExpect(status().isOk());

        // La contraseña nueva ya funciona de inmediato, sin esperar el bloqueo.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fuerzabruta3\",\"password\":\"claveNueva123\"}"))
                .andExpect(status().isOk());
    }
}
