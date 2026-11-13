package com.trailersys.backend.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.trailersys.backend.security.JwtAuthenticationFilter;
import com.trailersys.backend.security.RestAuthenticationEntryPoint;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final List<String> origenesPermitidos;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                           @Value("${trailersys.cors.allowed-origins:http://localhost:*,https://*.devtunnels.ms}") List<String> origenesPermitidos) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.origenesPermitidos = origenesPermitidos.stream()
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Todo lo que no es /api es el frontend estatico
                        // (index.html, app.html, css/, js/), que en
                        // produccion sirve este mismo backend. Los datos
                        // siguen protegidos: el frontend no muestra nada
                        // sin un JWT valido contra /api.
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Permite que el frontend (servido en otro puerto, ej. localhost:5173)
     * llame a esta API durante el desarrollo. Los origenes permitidos se
     * leen de trailersys.cors.allowed-origins (variable CORS_ORIGINS): en
     * produccion el frontend lo sirve este mismo backend (mismo origen, sin
     * CORS), asi que solo hace falta agregar dominios si el frontend se
     * aloja aparte.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // "https://*.devtunnels.ms": permite abrir la app desde un Dev Tunnel
        // de VS Code (Terminal > PORTS > reenviar puerto, visibilidad
        // Publica) para que alguien fuera de esta maquina pueda probarla -
        // sin esto, el navegador de esa persona recibe el origen del tunel
        // (no localhost) y el backend lo rechaza antes de llegar al login.
        config.setAllowedOriginPatterns(origenesPermitidos);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        // Sin esto, el navegador no deja leer este header desde JS en
        // peticiones cross-origin (ej. localhost:5173 -> localhost:8080),
        // aunque el backend lo mande - rompe la deteccion del nombre real
        // del archivo al descargar/guardar un respaldo desde el frontend.
        config.setExposedHeaders(List.of("Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
