package com.credinoda.domain.exception;

public class MontoInvalidoException extends CrediNodaException {
    public MontoInvalidoException(String message) {
        super(message);
    }

    public MontoInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
