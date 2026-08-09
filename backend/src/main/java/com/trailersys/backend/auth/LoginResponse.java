package com.trailersys.backend.auth;

public record LoginResponse(
        String token,
        Long id,
        String username,
        String nombre,
        String rol
) {
}
