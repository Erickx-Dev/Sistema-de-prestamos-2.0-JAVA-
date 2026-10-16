package com.credinoda.domain.exception;

public class CreditoNoEncontradoException extends CrediNodaException {
    public CreditoNoEncontradoException(String message) {
        super(message);
    }

    public CreditoNoEncontradoException(String message, Throwable cause) {
        super(message, cause);
    }
}
