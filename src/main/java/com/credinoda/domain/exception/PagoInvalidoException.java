package com.credinoda.domain.exception;

public class PagoInvalidoException extends CrediNodaException {
    public PagoInvalidoException(String message) {
        super(message);
    }

    public PagoInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
