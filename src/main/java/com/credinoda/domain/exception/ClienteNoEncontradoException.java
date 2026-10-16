package com.credinoda.domain.exception;

public class ClienteNoEncontradoException extends CrediNodaException {
    public ClienteNoEncontradoException(String message) {
        super(message);
    }

    public ClienteNoEncontradoException(String message, Throwable cause) {
        super(message, cause);
    }
}
