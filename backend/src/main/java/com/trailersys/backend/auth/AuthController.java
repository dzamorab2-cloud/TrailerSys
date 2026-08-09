package com.trailersys.backend.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trailersys.backend.security.JwtService;
import com.trailersys.backend.usuario.Usuario;
import com.trailersys.backend.usuario.UsuarioRepository;

import jakarta.validation.Valid;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(request.username())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new BadCredentialsException("Usuario o contraseña incorrectos."));

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos.");
        }

        String token = jwtService.generarToken(usuario.getUsername(), usuario.getRol().name());
        LoginResponse response = new LoginResponse(
                token, usuario.getId(), usuario.getUsername(), usuario.getNombre(), usuario.getRol().name());
        return ResponseEntity.ok(response);
    }

    /**
     * /api/auth/** esta en permitAll (SecurityConfig), asi que esta ruta es
     * alcanzable sin token: si no hay autenticacion real (JwtAuthenticationFilter
     * no la establecio), el principal queda anonimo y se rechaza como 401.
     */
    @GetMapping("/me")
    public MeResponse me(Principal principal) {
        if (principal == null || "anonymousUser".equals(principal.getName())) {
            throw new BadCredentialsException("No autenticado.");
        }
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(principal.getName())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new BadCredentialsException("No autenticado."));
        return MeResponse.from(usuario);
    }
}
