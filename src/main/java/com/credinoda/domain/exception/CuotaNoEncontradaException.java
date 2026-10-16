package com.credinoda.domain.exception;

public class CuotaNoEncontradaException extends CrediNodaException {
    public CuotaNoEncontradaException(String message) {
        super(message);
    }

    public CuotaNoEncontradaException(String message, Throwable cause) {
        super(message, cause);
    }
}
