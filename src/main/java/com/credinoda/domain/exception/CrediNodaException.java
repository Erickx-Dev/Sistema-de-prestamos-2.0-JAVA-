package com.credinoda.domain.exception;

/**
 * Excepción base no chequeada para todas las reglas de negocio y operaciones de CrediNoda.
 */
public class CrediNodaException extends RuntimeException {
    public CrediNodaException(String message) {
        super(message);
    }

    public CrediNodaException(String message, Throwable cause) {
        super(message, cause);
    }
}
