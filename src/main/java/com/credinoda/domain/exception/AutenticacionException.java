package com.credinoda.domain.exception;

/**
 * Excepción lanzada cuando fallan las credenciales o la verificación de identidad (CU-018, RN-022, RN-023).
 */
public class AutenticacionException extends CrediNodaException {

    public AutenticacionException(String message) {
        super(message);
    }

    public AutenticacionException(String message, Throwable cause) {
        super(message, cause);
    }
}
