package com.trailersys.backend.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trailersys.backend.common.ApiError;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sin este componente, Spring Security responde 403 tanto para "no hay
 * token" como para "el rol no alcanza". Con esto, la ausencia de
 * autenticacion valida devuelve 401 (mas correcto semanticamente), y
 * @PreAuthorize sigue devolviendo 403 cuando el rol es insuficiente.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError error = new ApiError(
                Instant.now(), 401, "Unauthorized", "Se requiere autenticacion para acceder a este recurso.", request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
