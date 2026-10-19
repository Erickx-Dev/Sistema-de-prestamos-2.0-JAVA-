package com.credinoda.domain.exception;

import java.time.LocalDateTime;

/**
 * Excepción lanzada cuando un usuario intenta iniciar sesión pero su cuenta se encuentra
 * temporalmente bloqueada por superar el límite de intentos fallidos (RN-023).
 */
public class UsuarioBloqueadoException extends AutenticacionException {

    private final LocalDateTime bloqueadoHasta;

    public UsuarioBloqueadoException(String message, LocalDateTime bloqueadoHasta) {
        super(message);
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }
}
