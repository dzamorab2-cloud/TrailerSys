package com.trailersys.backend.common;

/**
 * La cuenta acumulo demasiados intentos de login con contraseña incorrecta
 * seguidos (ver AuthController.login()) y esta temporalmente bloqueada,
 * sin importar si la contraseña de este intento es correcta o no.
 */
public class CuentaBloqueadaException extends RuntimeException {

    public CuentaBloqueadaException(String message) {
        super(message);
    }
}
