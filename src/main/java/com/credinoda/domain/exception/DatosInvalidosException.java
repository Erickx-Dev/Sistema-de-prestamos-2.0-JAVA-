package com.credinoda.domain.exception;

public class DatosInvalidosException extends CrediNodaException {
    public DatosInvalidosException(String message) {
        super(message);
    }

    public DatosInvalidosException(String message, Throwable cause) {
        super(message, cause);
    }
}
