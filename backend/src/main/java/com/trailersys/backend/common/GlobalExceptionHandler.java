package com.trailersys.backend.common;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;

/**
 * Traduce las excepciones de negocio y de Spring a respuestas JSON
 * consistentes ({@link ApiError}) en vez del comportamiento por defecto.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "No tienes permisos para realizar esta accion.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Datos invalidos." : message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es valido.", request);
    }

    /**
     * Cuando un @RequestParam no se puede convertir al tipo esperado (ej.
     * "?estado=Disponible" contra un enum cuya constante real es
     * "DISPONIBLE"), Spring lanza esto ANTES de llegar al controlador. Sin
     * este handler caia en handleGeneric() y devolvia 500 "Ocurrio un error
     * inesperado" - un dato mal escrito en la URL no es un error del
     * servidor, es un 400 con un mensaje que diga que se esperaba.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Class<?> tipo = ex.getRequiredType();
        String valoresValidos = tipo != null && tipo.isEnum()
                ? " Valores validos: " + Arrays.stream(tipo.getEnumConstants())
                        .map(Object::toString).collect(Collectors.joining(", "))
                : "";
        String mensaje = "El parametro '" + ex.getName() + "' no admite el valor '" + ex.getValue() + "'." + valoresValidos;
        return build(HttpStatus.BAD_REQUEST, mensaje, request);
    }

    /**
     * Para validaciones cruzadas entre campos que no se pueden expresar con
     * una sola anotacion Bean Validation (ej. "proximoServicio posterior a
     * fecha" en Mantenimientos). Los servicios lanzan esta excepcion cuando
     * detectan ese tipo de regla de negocio invalida.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * Eliminar un Vehiculo, Conductor, Cliente o Carga que todavia tiene
     * Viajes que lo referencian (vehiculo_id/conductor_id/cliente_id/
     * carga_id son NOT NULL) rompe la restriccion de clave foranea en la
     * base de datos - ninguno de esos *Service.eliminar() comprueba antes
     * si hay Viajes dependientes. Sin este handler, esa violacion caia en
     * handleGeneric() y devolvia 500 "Ocurrio un error inesperado", dejando
     * a quien intentaba borrar sin ninguna pista de que el registro seguia
     * en uso. Se traduce a 409 con un mensaje accionable.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "No se puede eliminar: otros registros (por ejemplo, viajes) todavia hacen referencia a este.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error inesperado.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError error = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
