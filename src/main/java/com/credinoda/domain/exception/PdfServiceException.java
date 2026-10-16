package com.credinoda.domain.exception;

public class PdfServiceException extends CrediNodaException {
    public PdfServiceException(String message) {
        super(message);
    }

    public PdfServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
